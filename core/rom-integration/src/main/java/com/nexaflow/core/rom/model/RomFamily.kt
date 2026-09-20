package com.nexaflow.core.rom.model

/**
 * Neutral capability tiers of the Android build landscape. The engine never
 * branches on a commercial product name: it reasons about *what the build can
 * do* (privileged SDK access, hidden vendor APIs, vendor settings prefixes).
 * Which concrete build maps to which tier is resolved solely by the protocol
 * evidence in [com.nexaflow.core.rom.RomDetectionMatrix] — build properties,
 * brand constraints and manufacturer fallbacks live there and nowhere else.
 *
 * Display names describe the tier's behavior, not any product.
 */
enum class RomFamily(val displayName: String, val description: String) {
    /** Clean Android Open Source Project build. */
    AOSP("AOSP / Stock", "Clean Android Open Source Project build"),

    /** Stock build of the Android platform maintainer. */
    STOCK_GOOGLE("Stock platform build", "Stock build published by the Android platform maintainer"),

    /**
     * Community ROM derived from an open-source base that publishes a version
     * property and vendor setting-key prefixes, and (when elevated) exposes a
     * full privileged SDK plus vendor hardware HALs.
     */
    CUSTOM_ROM_PRIVILEGED(
        "Custom ROM (privileged SDK)",
        "Community ROM with a full privileged SDK and vendor hardware APIs"
    ),

    /** Community AOSP build focused on privacy/security hardening. */
    CUSTOM_ROM_PRIVACY(
        "Privacy-focused custom ROM",
        "Security/privacy-hardened community AOSP build"
    ),

    /**
     * Vendor skin that exposes hidden system APIs to system components and
     * gates background execution behind vendor autostart switches.
     */
    OEM_SKIN_PRIVILEGED(
        "Vendor skin (hidden APIs)",
        "Vendor skin exposing hidden APIs to system components"
    ),

    /** Vendor skin without extra privileged surface from this engine's view. */
    OEM_SKIN("Vendor skin", "Vendor Android skin"),

    /** Vendor stock build without a distinct skin API surface. */
    OEM_STOCK("Vendor stock", "Vendor stock Android build"),

    /** Undetected or another build family. */
    OTHER("Other / Custom", "Undetected or another build family")
}
