package com.nexaflow.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
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
    /**
     * Installation is offered only when the exact phone APK and its checksum
     * are both present. Future updater-enabled releases therefore never fall
     * back to an unverified download.
     */
    val canInstall: Boolean get() = apkUrl != null && sha256 != null
}

/**
 * In-app update checker (P2-6): queries the GitHub releases API for the latest
 * release, requires the exact phone APK plus its published SHA-256 asset,
 * downloads it into the app cache, verifies size + digest, and hands
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
        val expectedApkName = "NexaFlow-$version.apk"
        val expectedShaName = "$expectedApkName.sha256"
        var apkUrl: String? = null
        var apkSize: Long? = null
        var sha256: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                when (asset.optString("name")) {
                    expectedApkName -> {
                        apkUrl = asset.optString("browser_download_url").ifEmpty { null }
                        apkSize = if (asset.has("size")) asset.optLong("size") else null
                    }
                    expectedShaName -> {
                        sha256 = asset.optString("browser_download_url").ifEmpty { null }
                    }
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
     * Downloads [url] into `cacheDir/updates/nexaflow-latest.apk`.
     * The published checksum is mandatory; inability to fetch or parse it is a
     * verification failure. [expectedSizeBytes], when present, must match the
     * GitHub release metadata before the digest is checked.
     */
    suspend fun downloadAndVerify(
        context: Context,
        url: String,
        sha256Url: String,
        expectedSizeBytes: Long? = null
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
            if (expectedSizeBytes != null && expectedSizeBytes >= 0L && dest.length() != expectedSizeBytes) {
                dest.delete()
                return@runCatching null
            }
            val expected = fetchText(sha256Url)?.let(::parseSha256)
            if (expected == null) {
                dest.delete()
                return@runCatching null
            }
            val actual = sha256(dest)
            if (!actual.equals(expected, ignoreCase = true)) {
                dest.delete()
                return@runCatching null
            }
            if (!packageIdentityMatches(context, dest)) {
                dest.delete()
                return@runCatching null
            }
            dest
        }.getOrNull()
    }

    /**
     * Accepts the raw 64-hex form emitted by NexaFlow releases and the common
     * `sha256sum` form ("<digest>  <filename>") for forward compatibility.
     */
    fun parseSha256(text: String): String? =
        Regex("(?i)(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])")
            .find(text)
            ?.value
            ?.lowercase()

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
     * Verifies that the downloaded archive is the same Android package and
     * belongs to the installed app's signing lineage before it ever reaches the
     * package installer. The release checksum protects transport integrity; this
     * additionally protects package identity if release metadata is tampered
     * with or the wrong APK asset is attached. The archive must also advance
     * versionCode, so a validly signed historical APK cannot be offered as an
     * in-app "update".
     *
     * Signing-certificate history is used instead of current-signer equality so
     * Android's proof-of-rotation lineage remains compatible with legitimate key
     * rotation.
     */
    internal fun packageIdentityMatches(context: Context, apk: File): Boolean = runCatching {
        val packageManager = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val installed = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, flags)
        }
        val archive = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        } ?: return@runCatching false

        archive.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(archive) >
                PackageInfoCompat.getLongVersionCode(installed) &&
            hasTrustedSigningLineage(
                installedCurrent = currentSigningDigests(installed),
                archiveCurrent = currentSigningDigests(archive),
                archiveHistory = signingHistoryDigests(archive)
            )
    }.getOrDefault(false)

    /**
     * Current signer(s) only. For a single-signer package this is the newest
     * certificate after a key rotation; accepting an arbitrary historical
     * intersection would permit a rollback APK signed only by an old key.
     */
    private fun currentSigningDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: return emptySet()
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return digestSignatures(signatures.orEmpty())
    }

    /**
     * Full proof-of-rotation lineage exposed by the candidate APK. Multi-signer
     * packages cannot use signer rotation, so their trusted history is exactly
     * their current signer set.
     */
    private fun signingHistoryDigests(info: PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return currentSigningDigests(info)
        }
        val signingInfo = info.signingInfo ?: return emptySet()
        val signatures = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        return digestSignatures(signatures.orEmpty())
    }

    private fun digestSignatures(signatures: Array<android.content.pm.Signature>): Set<String> =
        signatures.mapTo(linkedSetOf()) { signature ->
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        }

    /**
     * A candidate is trusted only when it is the same signer set or when its
     * proof-of-rotation history contains every currently installed signer.
     *
     * This direction matters: `old ∩ current != empty` is insufficient because
     * an APK signed only with an old certificate would otherwise pass after the
     * app has legitimately rotated to a newer certificate.
     */
    internal fun hasTrustedSigningLineage(
        installedCurrent: Set<String>,
        archiveCurrent: Set<String>,
        archiveHistory: Set<String>
    ): Boolean {
        if (installedCurrent.isEmpty() || archiveCurrent.isEmpty() || archiveHistory.isEmpty()) {
            return false
        }
        if (installedCurrent.size > 1 || archiveCurrent.size > 1) {
            return installedCurrent == archiveCurrent
        }
        return installedCurrent.all(archiveHistory::contains)
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
