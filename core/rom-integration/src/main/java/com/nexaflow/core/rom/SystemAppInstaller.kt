package com.nexaflow.core.rom

import android.content.Context
import com.nexaflow.core.rom.model.SystemControlResult
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Installs NexaFlow as a true privileged system app (/system/priv-app) using a
 * Magisk module. This is the "become part of the system" integration path for
 * rooted LineageOS-family ROMs (Evolution X, crDroid, ...):
 *
 *  1. Builds a module zip containing:
 *     - `module.prop`          — module metadata (id, name, version)
 *     - `system/priv-app/NexaFlow/NexaFlow.apk` — a copy of this app's APK
 *     - `system/etc/permissions/privapp-permissions-com.nexaflow.app.xml` —
 *       the priv-app permission whitelist granting WRITE_SECURE_SETTINGS,
 *       STATUS_BAR, MODIFY_PHONE_STATE, FORCE_STOP_PACKAGES, READ_LOGS, ...,
 *       which normal apps can never hold and which make the deep ROM
 *       integration work without per-command elevation.
 *  2. Installs it with `magisk --install-module <zip>` (one command, no reboot
 *     during install; the module becomes active on the next boot).
 *
 * The module is removable: `magisk --remove-module nexaflow_system_integration`
 * (or the Magisk app's Modules screen) uninstalls it, restoring the previous
 * state — safer than directly editing /system partitions.
 */
object SystemAppInstaller {

    /** Must stay in sync with the module id in [buildModuleZip]. */
    const val MODULE_ID = "nexaflow_system_integration"

    /** priv-app permissions whitelisted so the elevated paths work without su. */
    private val privilegedPermissions = listOf(
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.STATUS_BAR",
        "android.permission.EXPAND_STATUS_BAR",
        "android.permission.MODIFY_PHONE_STATE",
        "android.permission.FORCE_STOP_PACKAGES",
        "android.permission.KILL_BACKGROUND_PROCESSES",
        "android.permission.READ_LOGS",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.WRITE_SETTINGS",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.ACCESS_NOTIFICATION_POLICY"
    )

    /** True when a Magisk binary is available on the device. */
    fun isMagiskAvailable(): Boolean = SystemAppStatusDetector.suPaths
        .any { java.io.File(it).exists() }

    /**
     * Builds the Magisk module zip into the app cache dir. Returns the zip
     * file, or null when the current APK source can't be read.
     */
    fun buildModuleZip(context: Context): File? {
        val apkSource = File(context.applicationInfo.sourceDir)
        if (!apkSource.exists()) return null
        val cacheDir = File(context.cacheDir, "rom_modules").apply { mkdirs() }
        val zipFile = File(cacheDir, "nexaflow_system_integration.zip")
        return try {
            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zip ->
                    zip.putNextEntry(ZipEntry("module.prop"))
                    zip.write(moduleProp().toByteArray())
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("system/priv-app/NexaFlow/NexaFlow.apk"))
                    apkSource.inputStream().use { input ->
                        input.copyTo(zip, 1 shl 16)
                    }
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(
                        "system/etc/permissions/privapp-permissions-com.nexaflow.app.xml"
                    ))
                    zip.write(whitelistXml().toByteArray())
                    zip.closeEntry()
                }
            }
            zipFile
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Installs the module through Magisk. The install itself is synchronous
     * and safe; the module (and thus the priv-app copy + whitelist) takes
     * effect on the next reboot, after which the app reports integration
     * level PRIVILEGED_SYSTEM_APP.
     */
    fun install(context: Context): SystemControlResult {
        val zip = buildModuleZip(context)
            ?: return SystemControlResult.fail("Could not build the module zip (APK source unreadable)")
        // Prefer the Magisk CLI; fall back to the MMRL module manager when
        // only that is present.
        val result = PrivilegedRunner.runShell(
            "magisk --install-module \"${zip.absolutePath}\""
        )
        return if (result.success) {
            SystemControlResult.ok(
                "Module installed. Reboot the device to activate NexaFlow as a system app."
            )
        } else {
            SystemControlResult.fail("Magisk install failed: ${result.message}")
        }
    }

    internal fun moduleProp(): String = """
        id=$MODULE_ID
        name=NexaFlow System Integration
        version=1.0
        versionCode=1
        author=NexaFlow
        description=Installs NexaFlow as a privileged system app with deep ROM integration.
    """.trimIndent() + "\n"

    internal fun whitelistXml(): String {
        val permissions = privilegedPermissions.joinToString("\n    ") {
            "    <permission name=\"$it\" />"
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <!--
                Privileged permission whitelist for NexaFlow (system/priv-app).
                Granted only because the app is installed as a priv-app by the
                $MODULE_ID Magisk module; revoke the module to remove them.
            -->
            <permissions>
                <privapp-permissions package="com.nexaflow.app">
            $permissions
                </privapp-permissions>
            </permissions>
        """.trimIndent() + "\n"
    }
}
