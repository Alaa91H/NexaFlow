package com.nexaflow.core.rom

import android.content.Context
import android.provider.Settings
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.security.SafeCommandBuilder

/** Central settings write/read policy for the system-control façade. */
internal class SystemSettingsController(
    private val context: Context,
    private val capabilityProvider: RomCapabilityProvider,
) {
    fun writeSecureSetting(name: String, value: String): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SECURE_SETTINGS)) {
            return SystemControlResult.fail(
                "Write secure settings is not available at the current integration level"
            )
        }
        return try {
            Settings.Secure.putString(context.contentResolver, name, value)
            SystemControlResult.ok("Set $name = $value")
        } catch (failure: Throwable) {
            SystemControlResult.fail("Failed to write secure setting: ${failure.message}")
        }
    }

    fun writeSystemInt(name: String, value: Int, successMessage: String): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SETTINGS)) {
            return privileged("settings put system $name $value", successMessage)
        }
        return try {
            val written = Settings.System.putInt(context.contentResolver, name, value)
            if (written) SystemControlResult.ok(successMessage)
            else SystemControlResult.fail("The ROM rejected the change")
        } catch (failure: Throwable) {
            SystemControlResult.fail("Failed to change setting: ${failure.message}")
        }
    }

    fun writeSecureInt(name: String, value: Int, successMessage: String): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SECURE_SETTINGS)) {
            return privileged("settings put secure $name $value", successMessage)
        }
        return try {
            val written = Settings.Secure.putInt(context.contentResolver, name, value)
            if (written) SystemControlResult.ok(successMessage)
            else SystemControlResult.fail("The ROM rejected the change")
        } catch (failure: Throwable) {
            SystemControlResult.fail("Failed to change setting: ${failure.message}")
        }
    }

    fun readGlobalSetting(key: String): String? {
        val result = PrivilegedRunner.runShell(
            SafeCommandBuilder.build("settings", "get", "global", key)
        )
        return result.message.trim().takeIf { result.success && it != "null" }
    }

    fun writeAndReadGlobalBoolean(key: String, enabled: Boolean): SystemControlResult {
        val expected = if (enabled) "1" else "0"
        val write = writeSetting("GLOBAL", key, expected)
        if (!write.success) return write
        return if (readGlobalSetting(key) == expected) {
            SystemControlResult.ok("$key = $expected")
        } else {
            SystemControlResult.fail("$key was not accepted by this ROM")
        }
    }

    fun writeSetting(namespace: String, key: String, value: String): SystemControlResult {
        val ns = when (namespace.uppercase()) {
            "SYSTEM" -> "system"
            "SECURE" -> "secure"
            else -> "global"
        }
        if (key.isBlank()) return SystemControlResult.fail("No settings key configured")
        if (!SafeCommandBuilder.isSafeCommand(value)) {
            return SystemControlResult.fail("Settings value rejected: unsafe characters")
        }
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", ns, key, value)
            )
            if (shell.success) SystemControlResult.ok("$ns/$key = $value") else shell
        } catch (failure: Throwable) {
            SystemControlResult.fail("Settings write failed: ${failure.message}")
        }
    }

    private fun privileged(command: String, successMessage: String): SystemControlResult {
        val result = PrivilegedRunner.runShell(command)
        return if (result.success) SystemControlResult.ok(successMessage) else result
    }
}
