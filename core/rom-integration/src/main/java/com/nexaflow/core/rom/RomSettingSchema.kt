package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.RomFamily

/**
 * Pure, dependency-free map of the settings layout each build tier uses for
 * its custom ("vendor-defined") keys. The system `settings` providers
 * (system / secure / global) are shared by every Android 12–17 build — what
 * differs between builds is the *prefix* the vendor's own settings app reads
 * and the namespace the keys conventionally live in. This schema is what lets
 * the settings bridge enumerate and write the build's real keys on any tier
 * instead of hard-coding one product.
 *
 * The prefix strings below are protocol evidence — literal key names present
 * in device settings providers. They intentionally live only here and in
 * [RomDetectionMatrix], never in engine or UI code.
 *
 * No `android.*` imports — atomically unit-tested on the JVM.
 */
object RomSettingSchema {

    /**
     * Known custom-setting key prefixes per tier. Privileged community ROMs
     * share the base vendor prefixes plus their fork prefix; vendor skins use
     * their own vendor prefixes.
     */
    fun prefixes(family: RomFamily): List<String> = when (family) {
        RomFamily.CUSTOM_ROM_PRIVILEGED ->
            listOf(
                "evo_", "evolution_", "lineage_", "sysui_", "qs_",
                "lockscreen_", "status_bar_", "notification_", "dex_",
                "arrow_", "pixelos_", "elixir_", "derp_", "superior_", "pa_", "pe_"
            )
        RomFamily.OEM_SKIN_PRIVILEGED,
        RomFamily.OEM_SKIN ->
            listOf("miui_", "hyper_", "sec_", "oneui_", "oplus_", "oppo_", "oneplus_", "realme_", "vivo_", "funtouch_", "hw_", "emui_", "asus_", "nothing_")
        else -> emptyList()
    }

    /**
     * The namespace the tier's custom settings conventionally live in.
     * Privileged community ROM keys live in `Settings.Secure`; vendor skins
     * spread across `system`/`secure`.
     */
    fun defaultNamespaceName(family: RomFamily): String = when (family) {
        RomFamily.CUSTOM_ROM_PRIVILEGED -> "secure"
        RomFamily.OEM_SKIN_PRIVILEGED, RomFamily.OEM_SKIN -> "system"
        else -> "system"
    }

    /** Community ROMs derived from the same base share the privileged SDK/HALs. */
    fun isLineageDerived(family: RomFamily): Boolean =
        family == RomFamily.CUSTOM_ROM_PRIVILEGED

    /** The tiers whose custom settings the bridge can read/write. */
    fun isSupported(family: RomFamily): Boolean =
        prefixes(family).isNotEmpty()

    /** True when the tier is a vendor skin with vendor-specific APIs. */
    fun isOemSkin(family: RomFamily): Boolean =
        family == RomFamily.OEM_SKIN_PRIVILEGED || family == RomFamily.OEM_SKIN
}
