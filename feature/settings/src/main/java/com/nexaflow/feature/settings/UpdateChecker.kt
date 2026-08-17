package com.nexaflow.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Result of checking GitHub for the latest release. */
data class UpdateInfo(
    val version: String,
    val apkUrl: String?,
    val apkSizeBytes: Long?,
    val sha256: String?,
    val notes: String?
) {
    /** Whether a downloadable APK is attached to this release. */
    val canInstall: Boolean get() = apkUrl != null
}

/**
 * In-app update checker (P2-6): queries the GitHub releases API for the latest
 * release, verifies the attached APK against a published SHA-256 (when the
 * release ships a `.sha256` asset), downloads it into the app cache and hands
 * it to the system installer through the FileProvider.
 *
 * Parsing is pure ([parseRelease]) so it is unit-testable without the network;
 * the network calls run on [kotlinx.coroutines.Dispatchers.IO].
 */
object UpdateChecker {
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"


    /** GitHub repo in `owner/name` form. */
    const val REPO = "Alaa91H/NexaFlow"

    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /**
     * Newest-first releases page. Used when `releases/latest` 404s because
     * every release is a prerelease (see [pickLatestPublishedRelease]).
     */
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases?per_page=5"

    /**
     * Parses the GitHub "latest release" JSON payload. Pure — no I/O.
     * Returns null when the payload is not a usable release.
     */
    fun parseRelease(json: String): UpdateInfo? = runCatching {
        val root = JSONObject(json)
        val version = root.optString("tag_name").trim().ifEmpty { return null }
        val notes = root.optString("body").trim().ifEmpty { null }
        val assets = root.optJSONArray("assets")
        var apkUrl: String? = null
        var apkSize: Long? = null
        var sha256: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true) && apkUrl == null) {
                    apkUrl = asset.optString("browser_download_url").ifEmpty { null }
                    apkSize = if (asset.has("size")) asset.optLong("size") else null
                } else if (name.endsWith(".sha256", ignoreCase = true)) {
                    sha256 = asset.optString("browser_download_url").ifEmpty { null }
                }
            }
        }
        UpdateInfo(version, apkUrl, apkSize, sha256, notes)
    }.getOrNull()

    /**
     * Fetches the newest usable release JSON from the GitHub API.
     *
     * `releases/latest` only returns the newest *stable* release (non-draft,
     * non-prerelease) and responds 404 when a project ships every release as
     * a prerelease (e.g. `-alpha` tags) — so on any non-200 it falls back to
     * the releases list and picks the newest published release, prereleases
     * included.
     */
    suspend fun fetchLatestJson(): String? = kotlinx.coroutines.withContext(
        kotlinx.coroutines.Dispatchers.IO
    ) {
        val fromLatest = fetchJson(LATEST_URL)
        if (fromLatest != null) return@withContext fromLatest
        val releasesList = fetchJson(RELEASES_URL) ?: return@withContext null
        pickLatestPublishedRelease(releasesList)
    }

    /**
     * Pure: picks the JSON of the newest published (non-draft) release from a
     * `GET /releases` payload. The list is newest-first, so the first non-draft
     * entry wins. Prereleases deliberately count — this project ships every
     * release as `-alpha`/`-beta`, which is exactly why the plain `latest`
     * endpoint 404s. Returns null when the payload has no usable release.
     */
    fun pickLatestPublishedRelease(releasesJson: String): String? = runCatching {
        val array = JSONArray(releasesJson)
        for (i in 0 until array.length()) {
            val release = array.optJSONObject(i) ?: continue
            if (release.optBoolean("draft", false)) continue
            return@runCatching release.toString()
        }
        null
    }.getOrNull()

    /** Body of a 200 response, or null on any other status / error. */
    private fun fetchJson(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "NexaFlow")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode != 200) return@runCatching null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /**
     * Downloads [url] into `cacheDir/updates/nexaflow-latest.apk`. When
     * [sha256Url] is given, the published digest is fetched and the download is
     * verified against it; a mismatch returns null (never installs corrupt
     * bytes). Returns the downloaded file on success.
     */
    suspend fun downloadAndVerify(
        context: Context,
        url: String,
        sha256Url: String?
    ): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val dest = File(dir, "nexaflow-latest.apk")
            // Stream to disk, then verify.
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.setRequestProperty("User-Agent", "NexaFlow")
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                if (connection.responseCode != 200) return@runCatching null
                dest.outputStream().use { out ->
                    connection.inputStream.copyTo(out)
                }
            } finally {
                connection.disconnect()
            }
            val expected = sha256Url?.let { fetchText(it) }
            if (expected != null) {
                val actual = sha256(dest)
                // The published digest may be lower/upper case or have trailing
                // whitespace; compare normalized.
                if (!actual.equals(expected.trim(), ignoreCase = true)) {
                    dest.delete()
                    return@runCatching null
                }
            }
            dest
        }.getOrNull()
    }

    /** SHA-256 of [file] as lowercase hex. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Hands [apk] to the system installer through a FileProvider URI. The user
     * confirms the installation in the system dialog.
     */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun fetchText(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "NexaFlow")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode != 200) return@runCatching null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
