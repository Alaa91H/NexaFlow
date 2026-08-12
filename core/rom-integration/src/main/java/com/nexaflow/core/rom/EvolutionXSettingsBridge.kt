package com.nexaflow.core.rom

import android.content.Context
import android.provider.Settings
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.core.rom.model.SystemControlResult

/**
 * Deep integration with Evolution X's custom settings (the "Evolver" keys).
 *
 * Evolution X is a fork of LineageOS, so its ROM-specific settings live in the
 * standard `Settings.System` / `Settings.Secure` providers under well-known
 * prefixes (`evo_*`, `evolution_*`, `lineage_*`, `sysui_*`, ...). Instead of
 * hard-coding a key list that drifts between ROM versions, this bridge lists
 * the *actual* keys present on the device through the elevated shell and lets
 * the user read or write any of them — the same way the ROM's own Evolver
 * settings app does, but from inside NexaFlow.
 *
 * Reading is free (the providers expose keys read-only to normal apps);
 * writing goes through [PrivilegedRunner] (root / Shizuku / system app) or
 * falls back to the direct `WRITE_SETTINGS`/`WRITE_SECURE_SETTINGS` grant when
 * the app has it.
 */
object EvolutionXSettingsBridge {

    data class SettingEntry(
        val namespace: Namespace,
        val key: String,
        val value: String
    ) {
        val displayKey: String get() = "${namespace.shellName}.key:$key"
    }

    enum class Namespace(val shellName: String) {
        SYSTEM("system"),
        SECURE("secure"),
        GLOBAL("global");

        companion object {
            fun fromShell(name: String): Namespace? =
                entries.firstOrNull { it.shellName == name }
        }
    }

    /** Prefixes used by Evolution X / LineageOS custom settings. */
    private val romKeyPrefixes = listOf(
        "evo_",
        "evolution_",
        "lineage_",
        "dex_",
        "sysui_",
        "qs_",
        "lockscreen_",
        "status_bar_",
        "notification_"
    )

    /**
     * True when the device runs Evolution X (or a LineageOS-derived ROM whose
     * Evolver keys we can still read/write).
     */
    fun isEvolutionX(context: Context): Boolean {
        val family = RomIntegrationManager.buildInfo(context).family
        return family == RomFamily.EVOLUTION_X ||
            family == RomFamily.LINEAGE_OS ||
            family == RomFamily.CR_DROID
    }

    /**
     * Lists all ROM-custom setting keys currently present on the device by
     * running `settings list` through the elevated shell and filtering the
     * known ROM prefixes. Returns an empty list when no elevated runtime is
     * available (nothing crashes).
     */
    fun listCustomKeys(): List<SettingEntry> {
        val result = mutableListOf<SettingEntry>()
        Namespace.entries.forEach { namespace ->
            val raw = PrivilegedRunner.runShell("settings list ${namespace.shellName}")
            if (!raw.success) return@forEach
            result.addAll(parseSettingsList(namespace, raw.message))
        }
        return result.sortedBy { it.displayKey }
    }

    /**
     * Parses the `settings list` shell output into [SettingEntry]s, keeping
     * only keys with a known ROM prefix. Pure function — unit-tested.
     */
    internal fun parseSettingsList(
        namespace: Namespace,
        output: String
    ): List<SettingEntry> {
        val result = mutableListOf<SettingEntry>()
        output.split('\n').forEach { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (key.isNotBlank() && romKeyPrefixes.any { key.startsWith(it, ignoreCase = true) }) {
                result.add(SettingEntry(namespace, key, value))
            }
        }
        return result
    }

    /** Reads one key through the ContentResolver (no permission needed for reads). */
    fun read(context: Context, entry: SettingEntry): String? =
        read(context, entry.namespace, entry.key)

    fun read(context: Context, namespace: Namespace, key: String): String? {
        return try {
            when (namespace) {
                Namespace.SYSTEM -> Settings.System.getString(context.contentResolver, key)
                Namespace.SECURE -> Settings.Secure.getString(context.contentResolver, key)
                Namespace.GLOBAL -> Settings.Global.getString(context.contentResolver, key)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Writes one key. Tries the direct provider write first (honours an
     * existing WRITE_SETTINGS / WRITE_SECURE_SETTINGS grant), then falls back
     * to the elevated shell `settings put` (root / Shizuku / system app).
     */
    fun write(context: Context, entry: SettingEntry): SystemControlResult =
        write(context, entry.namespace, entry.key, entry.value)

    fun write(
        context: Context,
        namespace: Namespace,
        key: String,
        value: String
    ): SystemControlResult {
        val direct = try {
            val written = when (namespace) {
                Namespace.SYSTEM -> Settings.System.putString(context.contentResolver, key, value)
                Namespace.SECURE -> Settings.Secure.putString(context.contentResolver, key, value)
                Namespace.GLOBAL -> Settings.Global.putString(context.contentResolver, key, value)
            }
            written
        } catch (_: Throwable) {
            false
        }
        if (direct) {
            return SystemControlResult.ok("$namespace.key:$key = $value")
        }
        return PrivilegedRunner.runShell(
            "settings put ${namespace.shellName} \"$key\" \"$value\""
        ).let { result ->
            if (result.success) {
                SystemControlResult.ok("$namespace.key:$key = $value")
            } else {
                SystemControlResult.fail(
                    "Cannot write $namespace.key:$key — needs root, Shizuku or " +
                        "WRITE_SETTINGS/WRITE_SECURE_SETTINGS: ${result.message}"
                )
            }
        }
    }
}
