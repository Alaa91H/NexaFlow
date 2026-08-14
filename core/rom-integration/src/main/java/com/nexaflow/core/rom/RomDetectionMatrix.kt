package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.RomBuildInfo
import com.nexaflow.core.rom.model.RomFamily

/**
 * Pure, dependency-free detection table for the full ROM landscape: official
 * OEM skins (One UI, HyperOS/MIUI, ColorOS/OxygenOS, OriginOS, EMUI/HarmonyOS,
 * ZenUI, Nothing OS, Realme UI, stock Pixel/Motorola/Sony) and AOSP-derived
 * custom ROMs (LineageOS and its forks: Evolution X, crDroid, ArrowOS, PixelOS,
 * PixelExperience, Project Elixir, DerpFest, SuperiorOS, Paranoid Android;
 * plus the AOSP privacy builds like GrapheneOS).
 *
 * No `android.*` imports — the table and its precedence are unit-testable on
 * the pure JVM. [RomDetector] feeds it the live `Build.*` values and the
 * `ro.*` system properties; every custom ROM of the LineageOS family publishes
 * its own `ro.<name>.version` property via its vendor overlay, and the OEM
 * skins publish the version properties listed below.
 *
 * Precedence rules:
 *  - Custom ROMs are checked before OEM skins, so a LineageOS build flashed on
 *    a Samsung/Xiaomi device is classified as the custom ROM, never as the
 *    stock skin.
 *  - Specific forks precede their bases: Evolution X sets both
 *    `ro.evolution.version` and the inherited `ro.lineage.version`; Realme and
 *    OnePlus 9+ (ColorOS-based OxygenOS) set `ro.oplus.version`; HarmonyOS
 *    builds also set EMUI markers.
 *  - OEM property rules carry [Rule.brands] constraints so a ColorOS-based
 *    OxygenOS/Realme build is never misread as plain ColorOS: the family is
 *    resolved by brand when the shared `ro.oplus.version` property is present.
 *  - Manufacturer fallback covers stock builds whose version property is
 *    missing or renamed by the vendor (Motorola, Sony, Pixel, ...).
 */
object RomDetectionMatrix {

    data class Rule(
        val family: RomFamily,
        /** Property keys identifying the ROM; any non-blank value admits it. */
        val properties: List<String> = emptyList(),
        /**
         * Brand constraints (lowercase). When non-empty, the rule only wins on
         * a property hit if `Build.BRAND` (lowercased) is in this list — used
         * to disambiguate shared ColorOS-family properties between OPPO,
         * OnePlus and Realme.
         */
        val brands: List<String> = emptyList(),
        /** Manufacturer fallback (lowercase, matched against Build.MANUFACTURER). */
        val manufacturers: List<String> = emptyList()
    )

    /**
     * Ordered rules — most specific first. Custom ROMs (with no brand
     * constraint, since they run on any hardware) precede OEM skins.
     */
    val RULES: List<Rule> = listOf(
        // --- Custom ROMs, specific forks first ---
        Rule(RomFamily.EVOLUTION_X, properties = listOf("ro.evolution.version")),
        Rule(RomFamily.CR_DROID, properties = listOf("ro.crdroid.version")),
        Rule(RomFamily.PIXEL_EXPERIENCE, properties = listOf("ro.pixelexperience.version")),
        Rule(RomFamily.PARANOID_ANDROID, properties = listOf("ro.pa.version")),
        Rule(RomFamily.ARROW_OS, properties = listOf("ro.arrow.version")),
        Rule(RomFamily.PIXEL_OS, properties = listOf("ro.pixelos.version")),
        Rule(RomFamily.PROJECT_ELIXIR, properties = listOf("ro.elixir.version")),
        Rule(RomFamily.DERPFEST, properties = listOf("ro.derp.version")),
        Rule(RomFamily.SUPERIOR_OS, properties = listOf("ro.superior.version")),
        Rule(RomFamily.LINEAGE_OS, properties = listOf("ro.lineage.version")),
        // --- AOSP privacy builds ---
        Rule(
            RomFamily.GRAPHENE_OS,
            properties = listOf("ro.grapheneos.build_type", "ro.grapheneos.version")
        ),
        // --- OEM skins: version properties, brand-constrained ---
        Rule(
            RomFamily.HARMONY_OS,
            properties = listOf("ro.build.version.harmonyos", "hw_sc.build.platform.version"),
            brands = listOf("huawei", "honor")
        ),
        Rule(
            RomFamily.EMUI,
            properties = listOf("ro.build.version.emui", "ro.build.hw_emui_api_level"),
            brands = listOf("huawei", "honor")
        ),
        Rule(
            RomFamily.HYPER_OS,
            properties = listOf("ro.mi.os.version.name"),
            brands = listOf("xiaomi", "redmi", "poco")
        ),
        Rule(
            RomFamily.MIUI,
            properties = listOf("ro.miui.ui.version.name"),
            brands = listOf("xiaomi", "redmi", "poco")
        ),
        Rule(
            RomFamily.REALME_UI,
            properties = listOf("ro.build.version.realme", "ro.realme.version"),
            brands = listOf("realme")
        ),
        Rule(
            RomFamily.VIVO_ORIGIN_OS,
            properties = listOf("ro.vivo.os.build.display.id", "ro.vivo.os.build.display.version"),
            brands = listOf("vivo", "iqoo")
        ),
        Rule(
            RomFamily.COLOR_OS,
            properties = listOf("ro.oplus.version", "ro.build.version.oplusrom", "ro.build.version.oplus"),
            brands = listOf("oppo")
        ),
        Rule(
            RomFamily.OXYGEN_OS,
            properties = listOf("ro.oxygen.version", "ro.build.version.oxygen"),
            brands = listOf("oneplus")
        ),
        Rule(
            RomFamily.ONE_UI,
            properties = listOf("ro.build.version.oneui"),
            brands = listOf("samsung")
        ),
        Rule(
            RomFamily.ASUS_ZEN_UI,
            properties = listOf("ro.build.asus.version", "ro.asus.version"),
            brands = listOf("asus")
        ),
        Rule(RomFamily.NOTHING_OS, properties = listOf("ro.nothing.version", "ro.nothing.build.version")),
        // --- Manufacturer fallback (stock builds without a version prop) ---
        Rule(RomFamily.PIXEL, manufacturers = listOf("google")),
        Rule(RomFamily.ONE_UI, manufacturers = listOf("samsung")),
        Rule(RomFamily.MIUI, manufacturers = listOf("xiaomi", "redmi", "poco")),
        Rule(RomFamily.OXYGEN_OS, manufacturers = listOf("oneplus")),
        Rule(RomFamily.REALME_UI, manufacturers = listOf("realme")),
        Rule(RomFamily.COLOR_OS, manufacturers = listOf("oppo")),
        Rule(RomFamily.VIVO_ORIGIN_OS, manufacturers = listOf("vivo", "iqoo")),
        Rule(RomFamily.EMUI, manufacturers = listOf("huawei", "honor")),
        Rule(RomFamily.ASUS_ZEN_UI, manufacturers = listOf("asus")),
        Rule(RomFamily.MOTOROLA, manufacturers = listOf("motorola")),
        Rule(RomFamily.SONY_XPERIA, manufacturers = listOf("sony", "semc")),
        Rule(RomFamily.NOTHING_OS, manufacturers = listOf("nothing"))
    )

    /** Every property key any rule reads, for [RomDetector] to snapshot once. */
    val ALL_PROPERTIES: Set<String> =
        RULES.flatMap { it.properties }.toSet()

    /**
     * Resolves the family from a property snapshot plus the device identity.
     *
     * Pass 1 — property rules: the first rule (in [RULES] order) whose property
     * is present and whose brand constraints admit the brand wins. This puts
     * forks ahead of their bases and keeps ColorOS-family skins honest via the
     * brand tiebreak.
     *
     * Pass 2 — manufacturer fallback, in [RULES] order, for stock builds whose
     * version property is missing.
     */
    fun detectFamily(
        props: Map<String, String>,
        brand: String,
        manufacturer: String
    ): RomFamily {
        val brandLower = brand.lowercase()
        for (rule in RULES) {
            val propHit = rule.properties.any { props[it].orEmpty().isNotBlank() }
            if (!propHit) continue
            if (rule.brands.isNotEmpty() && brandLower !in rule.brands) continue
            return rule.family
        }
        for (rule in RULES) {
            if (rule.manufacturers.any { manufacturer.equals(it, ignoreCase = true) }) {
                return rule.family
            }
        }
        return RomFamily.OTHER
    }

    /**
     * Full detection including the version-agnostic build snapshot. Pure —
     * [RomDetector] supplies the live values.
     */
    fun detect(
        props: Map<String, String>,
        brand: String,
        manufacturer: String,
        device: String,
        model: String,
        androidVersion: String,
        securityPatch: String,
        buildId: String,
        buildDisplay: String,
        sdkInt: Int
    ): RomBuildInfo {
        val family = detectFamily(props, brand, manufacturer)
        return RomBuildInfo(
            family = family,
            brand = brand,
            manufacturer = manufacturer,
            device = device,
            model = model,
            androidVersion = androidVersion,
            securityPatch = securityPatch,
            buildId = buildId,
            buildDisplay = buildDisplay,
            androidSdk = sdkInt,
            evolutionVersion = props["ro.evolution.version"].orEmpty(),
            lineageVersion = props["ro.lineage.version"].orEmpty(),
            evolutionBuildType = props["ro.evolution.buildtype"].orEmpty()
        )
    }
}
