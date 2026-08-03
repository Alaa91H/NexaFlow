package com.nexaflow.core.rom.model

enum class RomFamily(val displayName: String, val description: String) {
    AOSP("AOSP / Stock", "Clean Android Open Source Project build"),
    LINEAGE_OS("LineageOS", "Custom ROM with a full privileged SDK"),
    CR_DROID("crDroid", "Custom ROM based on LineageOS"),
    EVOLUTION_X("Evolution X", "Pixel-like custom ROM"),
    PIXEL_EXPERIENCE("Pixel Experience", "Pixel-like experience ROM"),
    PARANOID_ANDROID("Paranoid Android", "Custom ROM with proprietary additions"),
    HYPER_OS("HyperOS", "Xiaomi HyperOS (MIUI successor)"),
    MIUI("MIUI", "Xiaomi MIUI"),
    COLOR_OS("ColorOS", "OPPO ColorOS"),
    OXYGEN_OS("OxygenOS", "OnePlus OxygenOS"),
    ONE_UI("One UI", "Samsung One UI"),
    PIXEL("Pixel / Stock Google", "Stock Google Pixel build"),
    OTHER("Other / Custom", "Undetected or another ROM family")
}
