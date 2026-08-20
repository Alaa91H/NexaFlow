package com.nexaflow.core.rom

import com.nexaflow.core.security.SafeCommandBuilder

/**
 * Closed elevated-operation algebra. New workflow capabilities may request only
 * these typed operations; no variant accepts a shell expression or executable
 * name supplied by a workflow/plugin.
 */
sealed interface PrivilegedOperation {
    val wireId: PrivilegedOperationId

    /** Typed AIDL arguments (at most three) reconstructed by UserService. */
    fun wireArguments(): List<String>

    /** Direct argv executed by the Shizuku UserService (never `sh -c`). */
    fun argv(): List<String>

    /** Internal, safely quoted representation required by `su -c` only. */
    fun rootCommand(): String {
        val parts = argv()
        return SafeCommandBuilder.build(parts.first(), *parts.drop(1).toTypedArray())
    }

    data class ForceStopPackage(val packageName: String) : PrivilegedOperation {
        override val wireId: PrivilegedOperationId = PrivilegedOperationId.PACKAGE_FORCE_STOP
        init { require(packageName.isPackageName()) }
        override fun wireArguments(): List<String> = listOf(packageName)
        override fun argv(): List<String> = listOf("am", "force-stop", packageName)
    }

    data class SetPackageEnabled(val packageName: String, val enabled: Boolean) : PrivilegedOperation {
        override val wireId: PrivilegedOperationId = PrivilegedOperationId.PACKAGE_SET_ENABLED
        init { require(packageName.isPackageName()) }
        override fun wireArguments(): List<String> = listOf(packageName, enabled.toString())
        override fun argv(): List<String> = if (enabled) {
            listOf("pm", "enable", packageName)
        } else {
            listOf("pm", "disable-user", "--user", "0", packageName)
        }
    }

    data class WriteSetting(
        val namespace: SettingNamespace,
        val key: String,
        val value: String
    ) : PrivilegedOperation {
        override val wireId: PrivilegedOperationId = PrivilegedOperationId.SYSTEM_SETTING_WRITE
        init {
            require(key in ALLOWED_SETTING_KEYS) { "Setting key is not allowlisted" }
            require(value.isSettingValue()) { "Setting value is invalid" }
        }
        override fun wireArguments(): List<String> = listOf(namespace.name, key, value)
        override fun argv(): List<String> = listOf("settings", "put", namespace.commandValue, key, value)
    }

    data class CopyControlledFile(val source: String, val destination: String) : PrivilegedOperation {
        override val wireId: PrivilegedOperationId = PrivilegedOperationId.FILE_COPY
        init {
            require(source.isControlledFilePath()) { "Source path is outside controlled storage" }
            require(destination.isControlledFilePath()) { "Destination path is outside controlled storage" }
            require(source != destination) { "Source and destination must differ" }
        }
        override fun wireArguments(): List<String> = listOf(source, destination)
        override fun argv(): List<String> = listOf("cp", "--", source, destination)
    }

    enum class SettingNamespace(val commandValue: String) {
        SYSTEM("system"),
        SECURE("secure"),
        GLOBAL("global");

        companion object {
            fun parse(value: String): SettingNamespace? = entries.firstOrNull { it.name == value }
        }
    }

    companion object {
        /** Decodes the only four operation shapes permitted across the AIDL boundary. */
        fun fromWire(
            wireId: String,
            first: String,
            second: String,
            third: String
        ): PrivilegedOperation? = runCatching {
            when (PrivilegedOperationId.entries.firstOrNull { it.wireValue == wireId }) {
                PrivilegedOperationId.PACKAGE_FORCE_STOP -> ForceStopPackage(first)
                PrivilegedOperationId.PACKAGE_SET_ENABLED -> SetPackageEnabled(first, second.toBooleanStrict())
                PrivilegedOperationId.SYSTEM_SETTING_WRITE -> WriteSetting(
                    namespace = SettingNamespace.parse(first) ?: return null,
                    key = second,
                    value = third
                )
                PrivilegedOperationId.FILE_COPY -> CopyControlledFile(first, second)
                null -> null
            }
        }.getOrNull()

        /** Conservative write allowlist; additions require a capability/test review. */
        val ALLOWED_SETTING_KEYS: Set<String> = setOf(
            "screen_off_timeout",
            "screen_brightness",
            "screen_brightness_mode",
            "airplane_mode_on",
            "wifi_on",
            "bluetooth_on",
            "zen_mode",
            "location_mode"
        )

        private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
        private const val CONTROLLED_FILE_ROOT = "/sdcard/NexaFlow/"
        private const val MAX_SETTING_VALUE_LENGTH = 512

        private fun String.isPackageName(): Boolean = PACKAGE.matches(this)
        private fun String.isSettingValue(): Boolean =
            length in 1..MAX_SETTING_VALUE_LENGTH && none { it.code < 0x20 || it == '\u0000' }
        private fun String.isControlledFilePath(): Boolean =
            startsWith(CONTROLLED_FILE_ROOT) && length <= 1_024 &&
                !contains("..") && none { it.code < 0x20 || it == '\u0000' }
    }
}


enum class PrivilegedOperationId(val wireValue: String) {
    PACKAGE_FORCE_STOP("package.force_stop"),
    PACKAGE_SET_ENABLED("package.set_enabled"),
    SYSTEM_SETTING_WRITE("settings.write"),
    FILE_COPY("file.copy")
}
