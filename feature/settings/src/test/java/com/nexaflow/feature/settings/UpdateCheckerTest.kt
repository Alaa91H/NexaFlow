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
          "tag_name": "v3.7.0-alpha",
          "body": "Fixes and features",
          "assets": [
            {"name": "nexaflow-release.apk", "size": 123456, "browser_download_url": "https://github.com/Alaa91H/NexaFlow/releases/download/v3.7.0-alpha/nexaflow-release.apk"},
            {"name": "nexaflow-release.apk.sha256", "browser_download_url": "https://github.com/Alaa91H/NexaFlow/releases/download/v3.7.0-alpha/nexaflow-release.apk.sha256"}
          ]
        }
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json)
        assertTrue(info != null)
        assertEquals("v3.7.0-alpha", info!!.version)
        assertEquals("https://github.com/Alaa91H/NexaFlow/releases/download/v3.7.0-alpha/nexaflow-release.apk", info.apkUrl)
        assertEquals(123456L, info.apkSizeBytes)
        assertEquals("https://github.com/Alaa91H/NexaFlow/releases/download/v3.7.0-alpha/nexaflow-release.apk.sha256", info.sha256)
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
    fun parseRelease_ignoresNonApkAssets() {
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
        assertEquals("https://x/nexaflow-debug.apk", info!!.apkUrl)
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
