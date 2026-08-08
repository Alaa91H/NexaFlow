package com.nexaflow.core.engine

/**
 * Pure policy for Android 15+ foreground-service time limits. Kept free of
 * Android API calls so the classification is unit-testable on the JVM.
 *
 * Android 15 (API 35) limits `dataSync` and `mediaProcessing` foreground
 * services to a combined 6 hours per 24-hour period. When the quota is
 * exhausted the system calls [android.app.Service.onTimeout]; the service must
 * call `stopSelf()` within a few seconds or the system throws
 * `RemoteServiceException`. Re-starting a time-limited type before the window
 * rolls over throws `ForegroundServiceStartNotAllowedException`.
 */
object MonitoringTimeoutPolicy {

    // ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC = 1 << 3 (API 26)
    private const val FGS_DATA_SYNC = 1 shl 3
    // ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING = 1 << 10 (API 35)
    private const val FGS_MEDIA_PROCESSING = 1 shl 10
    private const val FGS_TIMED_MASK = FGS_DATA_SYNC or FGS_MEDIA_PROCESSING

    /** Combined 6-hour quota shared by all time-limited types, per 24 hours. */
    const val TIME_LIMIT_MS = 6L * 60 * 60 * 1000

    /**
     * How long to wait before attempting to resume a service that hit its
     * time limit. 15 minutes is short enough to recover promptly once the
     * window rolls over and long enough not to hammer the system with
     * rejected starts.
     */
    const val RESUME_RETRY_MS = 15L * 60 * 1000

    /** Delay before starting monitoring after boot / background restrictions. */
    const val START_DELAY_MS = 8_000L

    /** True when [fgsType] carries a time-limited foreground service type. */
    fun isTimeLimitedType(fgsType: Int): Boolean = fgsType and FGS_TIMED_MASK != 0
}
