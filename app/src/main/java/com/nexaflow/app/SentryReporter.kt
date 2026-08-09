package com.nexaflow.app

import android.app.Application
import com.nexaflow.core.datastore.PrivacyPreferences
import com.nexaflow.core.engine.di.ApplicationScope
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Opt-in crash / ANR reporting (P0-2). Sentry stays completely inert until the
 * user enables "share anonymous crash reports" in Settings > Privacy AND a DSN
 * was baked into the build (BuildConfig.SENTRY_DSN). Privacy-first: nothing is
 * ever sent by default, and the decision is persisted per-user.
 */
@Singleton
class SentryReporter @Inject constructor(
    private val app: Application,
    private val privacyPreferences: PrivacyPreferences,
    @ApplicationScope private val scope: CoroutineScope
) {

    /** Whether a DSN was configured at build time (CI builds only). */
    private val dsnConfigured: Boolean = BuildConfig.SENTRY_DSN.isNotBlank()

    /** Reactive entry point: initialize/close Sentry as the opt-in flips. */
    fun attach() {
        scope.launch {
            privacyPreferences.settings
                .onEach { settings ->
                    if (settings.crashReportingEnabled && dsnConfigured) {
                        ensureInitialized()
                    } else if (Sentry.isEnabled()) {
                        // User turned reporting off (or a build without a DSN).
                        Sentry.close()
                    }
                }
                .collect { /* onEach already handled the transition */ }
        }
    }

    private fun ensureInitialized() {
        if (Sentry.isEnabled()) return
        SentryAndroid.init(app) { options: SentryAndroidOptions ->
            options.dsn = BuildConfig.SENTRY_DSN
            // Attach ANR traces (ApplicationExitInfo) so background kills and
            // freezes are reported, not just crashes.
            options.isAnrEnabled = true
            options.anrTimeoutIntervalMillis = 5_000L
            // Java-only crash reporting (NDK excluded from the dependency).
            options.isEnableNdk = false
            // Never capture screenshots or user input — automation privacy.
            options.isAttachScreenshot = false
            options.isSendDefaultPii = false
            options.setTag("app", "nexaflow")
        }
    }
}
