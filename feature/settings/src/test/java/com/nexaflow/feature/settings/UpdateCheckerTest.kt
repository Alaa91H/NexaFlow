package com.nexaflow.feature.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Pure parsing + digest logic of the in-app update checker. */
@RunWith(JUnit4::class)
class UpdateCheckerTest {

    @Test
    fun parseRelease_extractsVersionAndApk() {
        val json = """
        {
          "tag_name": "v3.7.0",
          "body": "Fixes and features",
          "assets": [
            {"name": "NexaFlow-v3.7.0.apk", "size": 123456, "browser_download_url": "https://github.com/Alaa91H/NexaFlow/releases/download/v3.7.0/NexaFlow-v3.7.0.apk"},
            {"name": "NexaFlow-v3.7.0.apk.sha256", "browser_download_url": "https://github.com/Alaa91H/NexaFlow/releases/download/v3.7.0/NexaFlow-v3.7.0.apk.sha256"}
          ]
        }
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json)
        assertTrue(info != null)
        assertEquals("v3.7.0", info!!.version)
        assertEquals("https://github.com/Alaa91H/NexaFlow/releases/download/v3.7.0/NexaFlow-v3.7.0.apk", info.apkUrl)
        assertEquals(123456L, info.apkSizeBytes)
        assertEquals("https://github.com/Alaa91H/NexaFlow/releases/download/v3.7.0/NexaFlow-v3.7.0.apk.sha256", info.sha256)
        assertTrue(info.canInstall)
    }

    @Test
    fun parseRelease_missingApkCannotInstall() {
        val json = """{"tag_name": "v1.0.0", "assets": []}"""
        val info = UpdateChecker.parseRelease(json)
        assertTrue(info != null)
        assertNull(info!!.apkUrl)
        assertFalse(info.canInstall)
    }

    @Test
    fun parseRelease_requiresMatchingChecksumBeforeInstall() {
        val json = """
        {
          "tag_name": "v3.90.0",
          "assets": [
            {"name": "NexaFlow-v3.90.0.apk", "size": 123, "browser_download_url": "https://x/phone.apk"}
          ]
        }
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json)!!
        assertEquals("https://x/phone.apk", info.apkUrl)
        assertNull(info.sha256)
        assertFalse(info.canInstall)
    }

    @Test
    fun parseRelease_ignoresWearAndDebugApks() {
        val json = """
        {
          "tag_name": "v3.90.0",
          "assets": [
            {"name": "NexaFlow-Wear-v3.90.0.apk", "browser_download_url": "https://x/wear.apk"},
            {"name": "NexaFlow-v3.90.0-debug.apk", "browser_download_url": "https://x/debug.apk"},
            {"name": "NexaFlow-v3.90.0.apk", "size": 321, "browser_download_url": "https://x/phone.apk"},
            {"name": "NexaFlow-v3.90.0.apk.sha256", "browser_download_url": "https://x/phone.sha256"}
          ]
        }
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json)!!
        assertEquals("https://x/phone.apk", info.apkUrl)
        assertEquals("https://x/phone.sha256", info.sha256)
        assertTrue(info.canInstall)
    }

    @Test
    fun parseRelease_invalidJsonReturnsNull() {
        assertNull(UpdateChecker.parseRelease("not-json{"))
        assertNull(UpdateChecker.parseRelease(""))
        assertNull(UpdateChecker.parseRelease("""{"assets": []}""")) // no tag_name
    }

    @Test
    fun pickLatestPublishedRelease_picksNewestFromList() {
        val json = """
        [
          {"tag_name": "v3.15.0-alpha", "draft": false, "prerelease": true, "assets": [{"name": "app-release.apk"}]},
          {"tag_name": "v3.14.0-alpha", "draft": false, "prerelease": true, "assets": []}
        ]
        """.trimIndent()
        val chosen = UpdateChecker.pickLatestPublishedRelease(json)
        assertTrue(chosen != null)
        // The newest entry wins even though every release is a prerelease.
        assertEquals("v3.15.0-alpha", UpdateChecker.parseRelease(chosen!!)!!.version)
    }

    @Test
    fun pickLatestPublishedRelease_skipsDrafts() {
        val json = """
        [
          {"tag_name": "v9.9.9-draft", "draft": true, "assets": []},
          {"tag_name": "v3.15.0-alpha", "draft": false, "prerelease": true, "assets": []}
        ]
        """.trimIndent()
        val chosen = UpdateChecker.pickLatestPublishedRelease(json)
        assertEquals("v3.15.0-alpha", UpdateChecker.parseRelease(chosen!!)!!.version)
    }

    @Test
    fun pickLatestPublishedRelease_emptyOrInvalidReturnsNull() {
        assertNull(UpdateChecker.pickLatestPublishedRelease("[]"))
        assertNull(UpdateChecker.pickLatestPublishedRelease("not-json{"))
        assertNull(UpdateChecker.pickLatestPublishedRelease("""[{"tag_name": "x", "draft": true}]"""))
    }

    @Test
    fun parseRelease_ignoresNonReleaseAssets() {
        val json = """
        {
          "tag_name": "v2.0.0",
          "assets": [
            {"name": "readme.txt", "browser_download_url": "https://x/readme.txt"},
            {"name": "nexaflow-debug.apk", "browser_download_url": "https://x/nexaflow-debug.apk"}
          ]
        }
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json)
        assertNull(info!!.apkUrl)
        assertFalse(info.canInstall)
    }

    @Test
    fun parseSha256_acceptsRawAndSha256sumFormats() {
        val digest = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertEquals(digest, UpdateChecker.parseSha256(digest))
        assertEquals(digest, UpdateChecker.parseSha256("$digest  NexaFlow-v3.90.0.apk"))
        assertNull(UpdateChecker.parseSha256("not-a-checksum"))
    }

    @Test
    fun forwardVersion_requiresStrictlyNewerVersionCode() {
        assertTrue(UpdateChecker.isForwardVersion(100L, 101L))
        assertFalse(UpdateChecker.isForwardVersion(100L, 100L))
        assertFalse(UpdateChecker.isForwardVersion(100L, 99L))
    }

    @Test
    fun signingLineage_acceptsSameSignerAndForwardRotation() {
        assertTrue(
            UpdateChecker.hasTrustedSigningLineage(
                installedCurrent = setOf("current-cert"),
                archiveCurrent = setOf("current-cert"),
                archiveHistory = setOf("current-cert")
            )
        )
        assertTrue(
            UpdateChecker.hasTrustedSigningLineage(
                installedCurrent = setOf("current-cert"),
                archiveCurrent = setOf("next-cert"),
                archiveHistory = setOf("old-cert", "current-cert", "next-cert")
            )
        )
    }

    @Test
    fun signingLineage_rejectsOldKeyRollbackAndUnrelatedSigner() {
        assertFalse(
            UpdateChecker.hasTrustedSigningLineage(
                installedCurrent = setOf("current-cert"),
                archiveCurrent = setOf("old-cert"),
                archiveHistory = setOf("old-cert")
            )
        )
        assertFalse(
            UpdateChecker.hasTrustedSigningLineage(
                installedCurrent = setOf("installed-cert"),
                archiveCurrent = setOf("other-cert"),
                archiveHistory = setOf("other-cert")
            )
        )
        assertFalse(
            UpdateChecker.hasTrustedSigningLineage(
                installedCurrent = emptySet(),
                archiveCurrent = setOf("cert"),
                archiveHistory = setOf("cert")
            )
        )
    }

    @Test
    fun signingLineage_requiresExactSetForMultipleSigners() {
        assertTrue(
            UpdateChecker.hasTrustedSigningLineage(
                installedCurrent = setOf("a", "b"),
                archiveCurrent = setOf("a", "b"),
                archiveHistory = setOf("a", "b")
            )
        )
        assertFalse(
            UpdateChecker.hasTrustedSigningLineage(
                installedCurrent = setOf("a", "b"),
                archiveCurrent = setOf("a", "c"),
                archiveHistory = setOf("a", "b", "c")
            )
        )
    }

    @Test
    fun sha256_matchesKnownDigest() {
        // "hello" → known SHA-256.
        val file = File.createTempFile("update-checker", ".bin")
        try {
            file.writeText("hello")
            assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                UpdateChecker.sha256(file)
            )
        } finally {
            file.delete()
        }
    }
}
