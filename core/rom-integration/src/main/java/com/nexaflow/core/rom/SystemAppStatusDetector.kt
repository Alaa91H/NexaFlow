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
        val cached = rootProbeAt
        if (cached > 0L && System.currentTimeMillis() - cached < ROOT_PROBE_TTL_MS) {
            return rootProbeResult
        }
        val result = probeRoot()
        rootProbeResult = result
        rootProbeAt = System.currentTimeMillis()
        return result
    }

    /** Drops the cached probe result so the next check re-probes the device. */
    fun refreshRootAvailability() {
        rootProbeAt = 0L
    }

    @Volatile
    private var rootProbeResult = false
    @Volatile
    private var rootProbeAt = 0L
    // Short TTL: a freshly granted root (via Magisk/KernelSU) must be picked up
    // quickly by the permission manager without re-spawning a process too often.
    private const val ROOT_PROBE_TTL_MS = 5_000L

    private val suPaths = listOf(
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

    private fun probeRoot(): Boolean {
        // Fast path: classic static su locations (legacy SuperSU, some OEM ROMs).
        if (suPaths.any { File(it).exists() }) return true
        // Definitive probe: resolve su from PATH (Magisk/KernelSU/APatch) and
        // run `id` as root. A successful uid=0 answer means root really works.
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
        return try {
            val process = ProcessBuilder("sh", "-c", "su -c id || su 0 id || /system/bin/su -c id")
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
            output.contains("uid=0")
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
