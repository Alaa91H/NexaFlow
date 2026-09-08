package com.nexaflow.core.rom

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.nexaflow.core.rom.model.IntegrationLevel
import java.io.File
import java.util.concurrent.TimeUnit

object SystemAppStatusDetector {
    fun detect(context: Context): IntegrationLevel {
        val applicationInfo = context.applicationInfo
        val isSystem = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val isPrivileged = applicationInfo.sourceDir?.contains("priv-app", ignoreCase = true) == true

        return when {
            isPlatformSigned(context) && isPrivileged -> IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP
            isPrivileged -> IntegrationLevel.PRIVILEGED_SYSTEM_APP
            isSystem || isUpdatedSystem -> IntegrationLevel.SYSTEM_APP
            isShizukuAvailable(context) -> IntegrationLevel.SHIZUKU
            isRootAvailable() -> IntegrationLevel.ROOT
            else -> IntegrationLevel.NORMAL
        }
    }

    @Suppress("DEPRECATION")
    fun isPlatformSigned(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            // On API 28+ use signingInfo (GET_SIGNING_CERTIFICATES); the legacy
            // signatures field (GET_SIGNATURES) is only populated on API < 28, so
            // the flag must match the branch — a shared flag would silently leave
            // `signatures` null on older devices.
            val useLegacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.P
            val flags = if (useLegacy) PackageManager.GET_SIGNATURES else PackageManager.GET_SIGNING_CERTIFICATES
            val ownPackage = packageManager.getPackageInfo(context.packageName, flags)
            val platformPackage = packageManager.getPackageInfo("android", flags)
            val ownSignature = if (useLegacy) {
                ownPackage.signatures?.firstOrNull()
            } else {
                ownPackage.signingInfo?.apkContentsSigners?.firstOrNull()
            } ?: return false
            val platformSignature = if (useLegacy) {
                platformPackage.signatures?.firstOrNull()
            } else {
                platformPackage.signingInfo?.apkContentsSigners?.firstOrNull()
            } ?: return false
            ownSignature.toByteArray().contentEquals(platformSignature.toByteArray())
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * True when a usable root shell is available.
     *
     * Modern root solutions (Magisk, KernelSU, APatch) do NOT install `su` at
     * the classic fixed paths — they expose it dynamically through PATH. A
     * path-existence check therefore reports "not rooted" even after the user
     * granted root. We probe the same way Tasker/libsu/RootBeer do: resolve
     * `su` from PATH and actually execute it to confirm a uid=0 shell answers.
     */
    fun isRootAvailable(): Boolean {
        val now = System.currentTimeMillis()
        val cached = rootProbeAt
        if (cached > 0L && now - cached < ROOT_PROBE_TTL_MS) {
            return rootProbeResult
        }
        synchronized(rootProbeLock) {
            // Re-check under the lock: another caller may have completed the
            // probe while we were waiting, so two concurrent requests never
            // spawn two `su` processes for the same observation.
            val nowUnderLock = System.currentTimeMillis()
            val rechecked = rootProbeAt
            if (rechecked > 0L && nowUnderLock - rechecked < ROOT_PROBE_TTL_MS) {
                return rootProbeResult
            }
            // Storm guard: even when the cache was invalidated, a probe that
            // finished less than probeSpacingMs ago is reused. A misbehaving
            // invalidation loop (e.g. a flapping Shizuku listener) can no
            // longer force a fresh `su` process spawn on every call — that
            // flood is what made ActivityManager kill the app for
            // "Too many Binders sent to SYSTEM".
            if (lastProbeAtMs > 0L && nowUnderLock - lastProbeAtMs < probeSpacingMs) {
                return rootProbeResult
            }
            val result = probeRoot()
            rootProbeResult = result
            rootProbeAt = nowUnderLock
            lastProbeAtMs = nowUnderLock
            return result
        }
    }

    /** Drops the cached probe result so the next check re-probes the device. */
    fun refreshRootAvailability() {
        rootProbeAt = 0L
    }

    /**
     * Clears the cache and probes again immediately — used when an execution
     * just failed with "No elevated runtime" so a freshly granted root is seen
     * without waiting for the TTL.
     */
    fun refreshAndProbe(): Boolean {
        refreshRootAvailability()
        return isRootAvailable()
    }

    @Volatile
    private var rootProbeResult = false
    @Volatile
    private var rootProbeAt = 0L
    // Wall-clock of the last actual probe; never cleared by refreshRootAvailability
    // so the storm guard below can always see how recently a probe really ran.
    @Volatile
    private var lastProbeAtMs = 0L
    private val rootProbeLock = Any()
    // Short TTL: a freshly granted root (via Magisk/KernelSU) must be picked up
    // quickly by the permission manager without re-spawning a process too often.
    // Reduced from 5s to 2s after review — the previous window hid a new grant
    // while the dashboard toast was still visible.
    private const val ROOT_PROBE_TTL_MS = 2_000L

    /**
     * Minimum wall-clock spacing between real `su` probes. Calls within this
     * window reuse the last answer even if the cache was invalidated, so an
     * event storm cannot translate into a process-spawn/binder flood. Each
     * spawn costs ~57 binder transactions on KernelSU (observed on device),
     * so the spacing directly bounds the binder rate. Internal so tests can
     * disable it for deterministic grant flows.
     */
    internal var probeSpacingMs: Long = 5_000L

    /**
     * Static `su` locations covering legacy SuperSU/OEM ROMs plus the modern
     * root managers (Magisk, KernelSU, APatch). Kept `internal` so tests can
     * assert the paths stay present.
     */
    internal val suPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
        "/system/sbin/su",
        "/data/adb/magisk/busybox",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su"
    )

    /**
     * Test seam: replaces the PATH-resolution probe so tests can simulate
     * Magisk/KernelSU/APatch (su on PATH) without spawning real processes.
     */
    internal var pathResolution: (() -> Boolean)? = null

    /** Test seam: replaces the uid=0 probe for deterministic root-grant tests. */
    internal var rootProbe: (() -> Boolean)? = null

    private fun probeRoot(): Boolean {
        // A discovered binary only proves that a root manager may be present.
        // Availability is admitted solely after `su` answers as uid=0; otherwise
        // the UI could expose privileged actions before this app was granted root.
        return suAnswersAsRoot()
    }

    /**
     * True when a root manager's `su` binary is present, regardless of whether
     * this app has been granted yet. Resolves `su` from PATH with `command -v`
     * (Magisk/KernelSU/APatch expose it dynamically) without actually invoking
     * it, so no grant dialog is triggered by mere detection.
     */
    fun isSuBinaryAvailable(): Boolean {
        if (suPaths.any { File(it).exists() }) return true
        return resolveSuFromPath()
    }

    private fun resolveSuFromPath(): Boolean {
        pathResolution?.let { return it() }
        return try {
            val process = ProcessBuilder("sh", "-c", "command -v su")
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                output.append(process.inputStream.bufferedReader().readText())
            }
            reader.start()
            val exited = process.waitFor(2, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                reader.join(1000)
                return false
            }
            reader.join(1000)
            process.destroy()
            output.isNotBlank()
        } catch (_: Throwable) {
            false
        }
    }

    private fun suAnswersAsRoot(): Boolean {
        rootProbe?.let { return it() }
        return try {
            // KernelSU Next exposes su only inside a granted app's mount namespace,
            // so probe the most reliable forms including the explicit KSU path.
            val process = ProcessBuilder(
                "sh", "-c",
                "su -c id 2>&1 || su 0 -c id 2>&1 || /system/bin/su -c id 2>&1 || /data/adb/ksu/bin/su -c id 2>&1 || /data/adb/magisk/busybox su -c id 2>&1"
            )
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                output.append(process.inputStream.bufferedReader().readText())
            }
            reader.start()
            val exited = process.waitFor(3, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                reader.join(1000)
                return false
            }
            reader.join(1000)
            process.destroy()
            // su answered: the output of `id` contains "uid=0".
            val text = output.toString()
            android.util.Log.d(
                "SystemAppStatusDetector",
                "su probe: exit=${process.exitValue()} out=${text.trim().take(120)} caller=" +
                    Throwable().stackTrace.take(6).joinToString("<-") { "${it.className.substringAfterLast('.')}#${it.methodName}:${it.lineNumber}" }
            )
            text.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
    }

    fun isShizukuAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
