package com.nexaflow.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.PrivacyPreferences
import io.sentry.android.core.SentryAndroidOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sentry activation rules (P0-2, privacy-first): Sentry must stay completely
 * inert on any build without a real DSN — a blank DSN with crash reporting
 * enabled must NOT initialize the SDK (and the app must keep running). Only a
 * build with an actual DSN AND the user's opt-in may boot the SDK, and it must
 * receive exactly that DSN. The initImpl seam swaps the real SDK boot for a
 * flag-setter so these tests assert WHEN initialization is attempted without
 * touching the network or the SDK's own behaviour.
 */
@RunWith(RobolectricTestRunner::class)
// The app targets SDK 37 but Robolectric 4.17 sandboxes for SDK 36+ need
// Java 21 while this build runs Java 17; these tests are SDK-agnostic, so
// run them on 35 (the newest SDK that supports Java 17).
@Config(sdk = [35])
class SentryReporterTest {

    private lateinit var app: Application
    private lateinit var privacyPreferences: PrivacyPreferences
    private lateinit var reporter: SentryReporter

    private var initCalls = 0
    private var configuredDsn: String? = null

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        privacyPreferences = PrivacyPreferences(app)
        reporter = SentryReporter(
            app = app,
            privacyPreferences = privacyPreferences,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
        initCalls = 0
        configuredDsn = null
        reporter.initImpl = { _, configure ->
            initCalls++
            val options = SentryAndroidOptions()
            configure(options)
            configuredDsn = options.dsn
        }
    }

    @After
    fun tearDown() {
        // Make sure the opt-in state never leaks into another test's file.
        runBlocking { privacyPreferences.setCrashReportingEnabled(false) }
    }

    @Test
    fun `blank dsn never initializes sentry even when crash reporting is enabled`() {
        reporter.dsn = "" // a DSN-less build (default: BuildConfig.SENTRY_DSN is empty)

        runBlocking {
            privacyPreferences.setCrashReportingEnabled(true)
            reporter.attach()
            awaitInitCalls(0)
        }

        assertEquals("Sentry must not initialize on a DSN-less build", 0, initCalls)
        assertTrue(configuredDsn == null)
    }

    @Test
    fun `real dsn initializes sentry with that exact dsn when reporting is enabled`() {
        val fakeDsn = "https://0123456789abcdef0123456789abcdef@example.com/42"
        reporter.dsn = fakeDsn

        runBlocking {
            privacyPreferences.setCrashReportingEnabled(true)
            reporter.attach()
            awaitInitCalls(1)
        }

        assertEquals("Sentry must initialize exactly once", 1, initCalls)
        assertEquals("Sentry must receive the configured DSN", fakeDsn, configuredDsn)
    }

    @Test
    fun `real dsn with reporting disabled stays inert`() {
        reporter.dsn = "https://0123456789abcdef0123456789abcdef@example.com/42"
        // crashReportingEnabled stays false (privacy-first default).

        runBlocking {
            reporter.attach()
            awaitInitCalls(0)
        }

        assertEquals("Opt-out must keep Sentry off even with a DSN", 0, initCalls)
    }

    /**
     * Polls until initCalls settles at the expected value (DataStore I/O and
     * the flow hop dispatchers, so a plain synchronous wait would race).
     */
    private suspend fun awaitInitCalls(expected: Int) {
        withTimeout(10_000) {
            while (initCalls != expected) delay(25)
            // Give any stray duplicate emission a moment to surface.
            delay(150)
            while (initCalls != expected) delay(25)
        }
    }
}
