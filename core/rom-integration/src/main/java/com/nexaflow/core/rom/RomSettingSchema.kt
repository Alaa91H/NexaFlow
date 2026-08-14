package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.RomFamily

/**
 * Pure, dependency-free map of the settings layout each ROM family uses for
 * its custom ("Evolver-style") keys. The system `settings` providers
 * (system / secure / global) are shared by every Android 12–17 build — what
 * differs between ROMs is the *prefix* the ROM's own settings app reads and
 * the namespace the keys conventionally live in. This schema is what lets the
 * settings bridge enumerate and write the ROM's real keys on any family
 * (LineageOS-derived, OEM skin, or AOSP) instead of hard-coding one ROM.
 *
 * No `android.*` imports — atomically unit-tested on the JVM.
 */
object RomSettingSchema {

    /**
     * Known custom-setting key prefixes per family. LineageOS-derived ROMs
     * share the LineageOS vendor prefixes (`lineage_`, `sysui_`, `qs_`, ...)
     * plus their own fork prefix; OEM skins use their vendor prefixes.
     */
    fun prefixes(family: RomFamily): List<String> = when (family) {
        RomFamily.EVOLUTION_X ->
            listOf("evo_", "evolution_", "dex_", "lineage_", "sysui_", "qs_", "lockscreen_", "status_bar_", "notification_")
        RomFamily.LINEAGE_OS,
        RomFamily.CR_DROID,
        RomFamily.ARROW_OS,
        RomFamily.PIXEL_OS,
        RomFamily.PROJECT_ELIXIR,
        RomFamily.DERPFEST,
        RomFamily.SUPERIOR_OS,
        RomFamily.PIXEL_EXPERIENCE,
        RomFamily.PARANOID_ANDROID ->
            listOf("lineage_", "sysui_", "qs_", "lockscreen_", "status_bar_", "notification_", "arrow_", "pixelos_", "elixir_", "derp_", "superior_", "pa_", "pe_")
        RomFamily.MIUI, RomFamily.HYPER_OS ->
            listOf("miui_", "hyper_")
        RomFamily.ONE_UI ->
            listOf("sec_", "oneui_")
        RomFamily.COLOR_OS, RomFamily.OXYGEN_OS, RomFamily.REALME_UI ->
            listOf("oplus_", "oppo_", "oneplus_", "realme_")
        RomFamily.VIVO_ORIGIN_OS ->
            listOf("vivo_", "funtouch_")
        RomFamily.EMUI, RomFamily.HARMONY_OS ->
            listOf("hw_", "emui_")
        RomFamily.ASUS_ZEN_UI ->
            listOf("asus_")
        RomFamily.NOTHING_OS ->
            listOf("nothing_")
        else -> emptyList()
    }

    /**
     * The namespace the family's custom settings conventionally live in.
     * LineageOS-family custom keys live in `Settings.Secure` (the "Evolver"
     * layout); OEM skins spread across `system`/`secure`.
     */
    fun defaultNamespaceName(family: RomFamily): String = when (family) {
        RomFamily.EVOLUTION_X,
        RomFamily.LINEAGE_OS,
        RomFamily.CR_DROID,
        RomFamily.ARROW_OS,
        RomFamily.PIXEL_OS,
        RomFamily.PROJECT_ELIXIR,
        RomFamily.DERPFEST,
        RomFamily.SUPERIOR_OS,
        RomFamily.PIXEL_EXPERIENCE,
        RomFamily.PARANOID_ANDROID -> "secure"
        RomFamily.MIUI, RomFamily.HYPER_OS -> "system"
        RomFamily.ONE_UI -> "system"
        RomFamily.COLOR_OS, RomFamily.OXYGEN_OS, RomFamily.REALME_UI -> "system"
        RomFamily.VIVO_ORIGIN_OS -> "system"
        RomFamily.EMUI, RomFamily.HARMONY_OS -> "system"
        RomFamily.ASUS_ZEN_UI -> "system"
        RomFamily.NOTHING_OS -> "secure"
        else -> "system"
    }

    /** LineageOS and its direct forks share the LineageOS privileged SDK/HALs. */
    fun isLineageDerived(family: RomFamily): Boolean = when (family) {
        RomFamily.LINEAGE_OS,
        RomFamily.EVOLUTION_X,
        RomFamily.CR_DROID,
        RomFamily.ARROW_OS,
        RomFamily.PIXEL_OS,
        RomFamily.PROJECT_ELIXIR,
        RomFamily.DERPFEST,
        RomFamily.SUPERIOR_OS,
        RomFamily.PIXEL_EXPERIENCE,
        RomFamily.PARANOID_ANDROID -> true
        else -> false
    }

    /** The families whose custom settings the bridge can read/write. */
    fun isSupported(family: RomFamily): Boolean =
        prefixes(family).isNotEmpty()

    /** True when the family is an OEM skin with vendor-specific APIs. */
    fun isOemSkin(family: RomFamily): Boolean = when (family) {
        RomFamily.ONE_UI,
        RomFamily.MIUI,
        RomFamily.HYPER_OS,
        RomFamily.COLOR_OS,
        RomFamily.OXYGEN_OS,
        RomFamily.REALME_UI,
        RomFamily.VIVO_ORIGIN_OS,
        RomFamily.EMUI,
        RomFamily.HARMONY_OS,
        RomFamily.ASUS_ZEN_UI,
        RomFamily.NOTHING_OS -> true
        else -> false
    }
}
