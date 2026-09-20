package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.RomBuildInfo
import com.nexaflow.core.rom.model.RomFamily

/**
 * Pure, dependency-free detection table mapping *build evidence* to neutral
 * capability tiers ([RomFamily]). The evidence below is protocol surface:
 * version properties each build publishes via its vendor overlay, brand
 * constraints that disambiguate shared properties, and manufacturer fallbacks
 * for stock builds without version properties. No `android.*` imports — the
 * table and its precedence are unit-testable on the pure JVM. [RomDetector]
 * feeds it the live `Build.*` values and the `ro.*` system properties.
 *
 * The table names no commercial product: builds are classified into tiers by
 * what they expose (privileged SDK, hidden vendor APIs, vendor prefixes).
 *
 * Precedence rules:
 *  - Community ROM evidence is checked before vendor skins, so a community
 *    build flashed on any hardware classifies as the community tier.
 *  - Fork evidence precedes the shared base property (forks publish both).
 *  - Vendor property rules carry [Rule.brands] constraints so shared family
 *    properties resolve to the right tier by brand.
 *  - Manufacturer fallback covers stock builds whose version property is
 *    missing or renamed by the vendor.
 */
object RomDetectionMatrix {

    data class Rule(
        val family: RomFamily,
        /** Property keys identifying the build; any non-blank value admits it. */
        val properties: List<String> = emptyList(),
        /**
         * Brand constraints (lowercase). When non-empty, the rule only wins on
         * a property hit if `Build.BRAND` (lowercased) is in this list — used
         * to disambiguate shared vendor-family properties between brands.
         */
        val brands: List<String> = emptyList(),
        /** Manufacturer fallback (lowercase, matched against Build.MANUFACTURER). */
        val manufacturers: List<String> = emptyList()
    )

    /**
     * Ordered rules — most specific first. Community ROMs (with no brand
     * constraint, since they run on any hardware) precede vendor skins.
     */
    val RULES: List<Rule> = listOf(
        // --- Community ROMs: fork evidence precedes the shared base property ---
        Rule(RomFamily.CUSTOM_ROM_PRIVILEGED, properties = listOf("ro.evolution.version")),
        Rule(RomFamily.CUSTOM_ROM_PRIVILEGED, properties = listOf("ro.crdroid.version")),
        Rule(RomFamily.CUSTOM_ROM_PRIVILEGED, properties = listOf("ro.pixelexperience.version")),
        Rule(RomFamily.CUSTOM_ROM_PRIVILEGED, properties = listOf("ro.pa.version")),
        Rule(RomFamily.CUSTOM_ROM_PRIVILEGED, properties = listOf("ro.arrow.version")),
        Rule(RomFamily.CUSTOM_ROM_PRIVILEGED, properties = listOf("ro.pixelos.version")),
        Rule(RomFamily.CUSTOM_ROM_PRIVILEGED, properties = listOf("ro.elixir.version")),
        Rule(RomFamily.CUSTOM_ROM_PRIVILEGED, properties = listOf("ro.derp.version")),
        Rule(RomFamily.CUSTOM_ROM_PRIVILEGED, properties = listOf("ro.superior.version")),
        Rule(
            RomFamily.CUSTOM_ROM_PRIVILEGED,
            properties = listOf("ro.lineage.version")
        ),
        // --- Privacy/security-hardened community builds ---
        Rule(
            RomFamily.CUSTOM_ROM_PRIVACY,
            properties = listOf("ro.grapheneos.build_type", "ro.grapheneos.version")
        ),
        // --- Vendor skins: version properties, brand-constrained ---
        Rule(
            RomFamily.OEM_SKIN,
            properties = listOf("ro.build.version.harmonyos", "hw_sc.build.platform.version"),
            brands = listOf("huawei", "honor")
        ),
        Rule(
            RomFamily.OEM_SKIN,
            properties = listOf("ro.build.version.emui", "ro.build.hw_emui_api_level"),
            brands = listOf("huawei", "honor")
        ),
        Rule(
            RomFamily.OEM_SKIN_PRIVILEGED,
            properties = listOf("ro.mi.os.version.name"),
            brands = listOf("xiaomi", "redmi", "poco")
        ),
        Rule(
            RomFamily.OEM_SKIN_PRIVILEGED,
            properties = listOf("ro.miui.ui.version.name"),
            brands = listOf("xiaomi", "redmi", "poco")
        ),
        Rule(
            RomFamily.OEM_SKIN,
            properties = listOf("ro.build.version.realme", "ro.realme.version"),
            brands = listOf("realme")
        ),
        Rule(
            RomFamily.OEM_SKIN,
            properties = listOf("ro.vivo.os.build.display.id", "ro.vivo.os.build.display.version"),
            brands = listOf("vivo", "iqoo")
        ),
        Rule(
            RomFamily.OEM_SKIN_PRIVILEGED,
            properties = listOf("ro.oplus.version", "ro.build.version.oplusrom", "ro.build.version.oplus"),
            brands = listOf("oppo", "oneplus")
        ),
        Rule(
            RomFamily.OEM_SKIN_PRIVILEGED,
            properties = listOf("ro.oxygen.version", "ro.build.version.oxygen"),
            brands = listOf("oneplus")
        ),
        Rule(
            RomFamily.OEM_SKIN_PRIVILEGED,
            properties = listOf("ro.build.version.oneui"),
            brands = listOf("samsung")
        ),
        Rule(
            RomFamily.OEM_SKIN,
            properties = listOf("ro.build.asus.version", "ro.asus.version"),
            brands = listOf("asus")
        ),
        Rule(
            RomFamily.OEM_SKIN,
            properties = listOf("ro.nothing.version", "ro.nothing.build.version")
        ),
        // --- Manufacturer fallback (stock builds without a version prop) ---
        Rule(RomFamily.STOCK_GOOGLE, manufacturers = listOf("google")),
        Rule(RomFamily.OEM_SKIN_PRIVILEGED, manufacturers = listOf("samsung")),
        Rule(RomFamily.OEM_SKIN_PRIVILEGED, manufacturers = listOf("xiaomi", "redmi", "poco")),
        Rule(RomFamily.OEM_SKIN_PRIVILEGED, manufacturers = listOf("oneplus")),
        Rule(RomFamily.OEM_SKIN, manufacturers = listOf("realme")),
        Rule(RomFamily.OEM_SKIN_PRIVILEGED, manufacturers = listOf("oppo")),
        Rule(RomFamily.OEM_SKIN, manufacturers = listOf("vivo", "iqoo")),
        Rule(RomFamily.OEM_SKIN, manufacturers = listOf("huawei", "honor")),
        Rule(RomFamily.OEM_SKIN, manufacturers = listOf("asus")),
        Rule(RomFamily.OEM_STOCK, manufacturers = listOf("motorola")),
        Rule(RomFamily.OEM_STOCK, manufacturers = listOf("sony", "semc")),
        Rule(RomFamily.OEM_SKIN, manufacturers = listOf("nothing"))
    )

    /** Every property key any rule reads, for [RomDetector] to snapshot once. */
    val ALL_PROPERTIES: Set<String> =
        RULES.flatMap { it.properties }.toSet()

    /**
     * Resolves the tier from a property snapshot plus the device identity.
     *
     * Pass 1 — property rules: the first rule (in [RULES] order) whose property
     * is present and whose brand constraints admit the brand wins. This puts
     * forks ahead of their bases and keeps shared vendor properties honest via
     * the brand tiebreak.
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
            vendorVersion = props["ro.lineage.version"]
                ?: props["ro.evolution.version"].orEmpty(),
            baseVersion = props["ro.lineage.version"].orEmpty()
        )
    }
}
