package com.nexaflow.core.rom.model

/**
 * The full ROM landscape the integration layer understands: official OEM skins
 * and AOSP-derived custom ROMs. Detection precedence is handled by
 * [com.nexaflow.core.rom.RomDetectionMatrix]; this enum only names families.
 */
enum class RomFamily(val displayName: String, val description: String) {
    // --- Stock / AOSP ---
    AOSP("AOSP / Stock", "Clean Android Open Source Project build"),
    PIXEL("Pixel / Stock Google", "Stock Google Pixel build"),

    // --- Custom ROMs (LineageOS-derived unless noted) ---
    LINEAGE_OS("LineageOS", "Custom ROM with a full privileged SDK"),
    EVOLUTION_X("Evolution X", "Pixel-like custom ROM"),
    CR_DROID("crDroid", "Custom ROM based on LineageOS"),
    PIXEL_EXPERIENCE("Pixel Experience", "Pixel-like experience ROM"),
    PARANOID_ANDROID("Paranoid Android", "Custom ROM with proprietary additions"),
    ARROW_OS("ArrowOS", "AOSP-based custom ROM"),
    PIXEL_OS("PixelOS", "Pixel-like AOSP custom ROM"),
    PROJECT_ELIXIR("Project Elixir", "Minimalistic AOSP custom ROM"),
    DERPFEST("DerpFest", "Feature-rich custom ROM"),
    SUPERIOR_OS("SuperiorOS", "AOSP-based custom ROM"),
    GRAPHENE_OS("GrapheneOS", "Privacy/security-focused AOSP build"),

    // --- OEM skins ---
    ONE_UI("One UI", "Samsung One UI"),
    MIUI("MIUI", "Xiaomi MIUI"),
    HYPER_OS("HyperOS", "Xiaomi HyperOS (MIUI successor)"),
    COLOR_OS("ColorOS", "OPPO ColorOS"),
    OXYGEN_OS("OxygenOS", "OnePlus OxygenOS"),
    REALME_UI("Realme UI", "Realme UI (ColorOS-based)"),
    VIVO_ORIGIN_OS("OriginOS", "Vivo OriginOS / Funtouch OS"),
    EMUI("EMUI", "Huawei EMUI"),
    HARMONY_OS("HarmonyOS", "Huawei HarmonyOS"),
    ASUS_ZEN_UI("ZenUI", "ASUS ZenUI"),
    NOTHING_OS("Nothing OS", "Nothing OS"),
    MOTOROLA("Motorola", "Motorola stock Android"),
    SONY_XPERIA("Sony Xperia", "Sony Xperia stock Android"),

    OTHER("Other / Custom", "Undetected or another ROM family")
}
