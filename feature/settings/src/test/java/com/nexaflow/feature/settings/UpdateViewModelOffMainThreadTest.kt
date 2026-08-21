package com.nexaflow.feature.settings

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Proves the in-app update checker never performs network I/O on the main
 * thread: [UpdateViewModel.check] -> [UpdateChecker.fetchLatestJson] and
 * [UpdateViewModel.downloadAndInstall] -> [UpdateChecker.downloadAndVerify]
 * both dispatch through [kotlinx.coroutines.Dispatchers.IO], so every
 * HttpURLConnection method (getResponseCode/getInputStream) must observe a
 * worker thread, never `Looper.getMainLooper().thread`.
 *
 * The network is intercepted with a fake https URLStreamHandler that serves
 * canned GitHub API / APK / digest payloads and records the calling thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateViewModelOffMainThreadTest {

    companion object {
        private const val APK_URL =
            "https://github.com/Alaa91H/NexaFlow/releases/download/v99.0.0/nexaflow-release.apk"
        private const val SHA_URL =
            "https://github.com/Alaa91H/NexaFlow/releases/download/v99.0.0/nexaflow-release.apk.sha256"

        private val FAKE_APK = "NexaFlow fake APK payload (off-main-thread test)".toByteArray()

        private val RELEASE_JSON = """
        {
          "tag_name": "v99.0.0",
          "body": "test release",
          "assets": [
            {"name": "nexaflow-release.apk", "size": 123, "browser_download_url": "$APK_URL"},
            {"name": "nexaflow-release.apk.sha256", "browser_download_url": "$SHA_URL"}
          ]
        }
        """.trimIndent()

        /** Thread that observed each (url) connection; shared across instances. */
        private val calls = java.util.Collections.synchronizedList(
            mutableListOf<Pair<String, Thread>>()
        )

        @Volatile
        private var factoryInstalled = false

        private fun sha256Hex(data: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(data).joinToString("") { "%02x".format(it) }
        }
    }

    @Before
    fun installFakeNetwork() {
        if (!factoryInstalled) {
            synchronized(this) {
                if (!factoryInstalled) {
                    URL.setURLStreamHandlerFactory { protocol ->
                        if (protocol != "https") {
                            null
                        } else {
                            object : URLStreamHandler() {
                                override fun openConnection(u: URL): URLConnection =
                                    FakeConnection(u)
                            }
                        }
                    }
                    factoryInstalled = true
                }
            }
        }
        calls.clear()
    }

    /** HttpURLConnection that records the calling thread and serves canned data. */
    private inner class FakeConnection(private val connectionUrl: URL) : HttpURLConnection(connectionUrl) {
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() {
            connected = true
        }

        override fun getResponseCode(): Int {
            calls.add(connectionUrl.toString() to Thread.currentThread())
            return 200
        }

        override fun getInputStream(): InputStream {
            calls.add(connectionUrl.toString() to Thread.currentThread())
            val body = when {
                connectionUrl.toString().contains("/releases/latest") -> RELEASE_JSON.toByteArray()
                connectionUrl.toString().endsWith(".sha256") -> sha256Hex(FAKE_APK).toByteArray()
                else -> FAKE_APK
            }
            return ByteArrayInputStream(body)
        }
    }

    private fun awaitTerminalState(vm: UpdateViewModel, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            when (vm.state.value) {
                is UpdateUiState.Checking, is UpdateUiState.Downloading -> Thread.sleep(20)
                else -> return
            }
        }
        fail("Timed out waiting for a terminal state; state=${vm.state.value}")
    }

    private fun assertNotMainThread(threads: List<Thread>, what: String) {
        assertTrue("$what must have executed (no network calls observed)", threads.isNotEmpty())
        val main = Looper.getMainLooper().thread
        assertTrue(
            "$what must run off the main thread; observed ${threads.size} call(s), " +
                "main=${main.name}",
            threads.all { it !== main }
        )
    }

    @Test
    fun updateChecking_isDisabledByDefaultUntilUserRequestsIt() {
        val vm = UpdateViewModel(ApplicationProvider.getApplicationContext())

        // Construction represents a fresh Settings screen / app launch. It must
        // not begin a network request or surface a download prompt by itself.
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "Update checking must remain idle until the user explicitly taps Check for updates",
            vm.state.value is UpdateUiState.Idle
        )
        assertTrue(
            "No update network request may start automatically on a fresh install",
            calls.isEmpty()
        )
    }

    @Test
    fun fetchLatestJson_runsOffMainThread() {
        val vm = UpdateViewModel(ApplicationProvider.getApplicationContext())
        vm.check()
        awaitTerminalState(vm)
        assertTrue("check() should reach a terminal state", vm.state.value !is UpdateUiState.Checking)
        assertNotMainThread(
            calls.filter { it.first.contains("/releases/latest") }.map { it.second },
            "fetchLatestJson (GitHub API round-trip)"
        )
    }

    @Test
    fun downloadAndVerify_runsOffMainThread() {
        val vm = UpdateViewModel(ApplicationProvider.getApplicationContext())
        vm.check()
        awaitTerminalState(vm)
        assertTrue(
            "check() should surface an Available update; state=${vm.state.value}",
            vm.state.value is UpdateUiState.Available
        )
        vm.downloadAndInstall()
        awaitTerminalState(vm)
        assertTrue(
            "download must leave the Downloading state; state=${vm.state.value}",
            vm.state.value !is UpdateUiState.Downloading
        )
        // APK download + digest fetch (and the later install attempt) are the
        // network-touching parts of downloadAndVerify.
        val downloadThreads = calls
            .filter { it.first.contains("nexaflow-release.apk") }
            .map { it.second }
        assertNotMainThread(downloadThreads, "downloadAndVerify (APK download + SHA-256 fetch)")
    }
}
