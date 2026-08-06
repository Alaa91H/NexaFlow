package com.nexaflow.core.rom

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.nexaflow.core.rom.model.IntegrationLevel
import java.io.File

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
                @Suppress("DEPRECATION")
                ownPackage.signatures?.firstOrNull()
            } else {
                ownPackage.signingInfo?.apkContentsSigners?.firstOrNull()
            } ?: return false
            val platformSignature = if (useLegacy) {
                @Suppress("DEPRECATION")
                platformPackage.signatures?.firstOrNull()
            } else {
                platformPackage.signingInfo?.apkContentsSigners?.firstOrNull()
            } ?: return false
            ownSignature.toByteArray().contentEquals(platformSignature.toByteArray())
        } catch (_: Throwable) {
            false
        }
    }

    fun isRootAvailable(): Boolean {
        val suPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/vendor/bin/su",
            "/system/sbin/su"
        )
        return suPaths.any { File(it).exists() }
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
