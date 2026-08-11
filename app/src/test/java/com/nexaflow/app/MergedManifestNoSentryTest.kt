package com.nexaflow.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Boot-safety guarantee (P0-2, "open-crash fix"): sentry-android-core would
 * auto-register SentryInitProvider (and SentryPerformanceProvider) for
 * automatic initialization on every app start. Sentry then throws "DSN is
 * required" and force-closes the app when no NEXAFLOW_SENTRY_DSN was baked
 * into the build — before Application.onCreate even runs. The manifest
 * removes both providers with tools:node="remove", so a DSN-less build must
 * boot. This test asserts those providers are really gone from the merged
 * manifest Robolectric sees.
 */
@RunWith(RobolectricTestRunner::class)
// The app targets SDK 37 but Robolectric 4.17 sandboxes for SDK 36+ need
// Java 21 while this build runs Java 17; these tests are SDK-agnostic, so
// run them on 35 (the newest SDK that supports Java 17).
@Config(sdk = [35])
class MergedManifestNoSentryTest {

    @Test
    fun `sentry auto-init providers are stripped from the merged manifest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // ShadowPackageManager returns null here (not an empty list), and the
        // provider registry is populated from the merged manifest.
        val providers = context.packageManager.queryContentProviders(null, 0, 0).orEmpty()
        val providerNames = providers.map { it.name }.joinToString()

        assertFalse(
            "SentryInitProvider must be removed from the merged manifest " +
                "(it force-closes DSN-less builds); found providers: $providerNames",
            providerNames.contains("io.sentry.android.core.SentryInitProvider")
        )
        assertFalse(
            "SentryPerformanceProvider must be removed from the merged manifest; " +
                "found providers: $providerNames",
            providerNames.contains("io.sentry.android.core.SentryPerformanceProvider")
        )
    }
}
