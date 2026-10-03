package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalRuntimePipelineTest {

    private val pipeline = CanonicalRuntimePipeline()

    /** The pilot schema, used by the open-settings cutover tests. */
    private val openSchema = PilotOpenFamily.openSettingsSchema()
    private val openSemantics = PilotOpenFamily.semantics
    private val openCapability = CapabilityRequirement.Capability(CapabilityId.SETTINGS_LAUNCH)

    @Test
    fun defaultAdapterCoversAll237TypesWithFamilyOverrides() {
        assertEquals(237, pipeline.adapter.declaredRules)
    }

    @Test
    fun legacyOpenSettingsFlowsThroughToAnExecutablePlan() {
        val plan = pipeline.planLegacy(
            runId = "run-1",
            legacyType = "SYSTEM_OPEN_WIFI_SETTINGS",
            kind = LegacyNodeKind.ACTION,
            schema = openSchema,
            config = listOf(LegacyConfigEntry("page", "WIFI")),
            semantics = openSemantics,
            capabilityRequirement = openCapability,
        )

        assertTrue(plan.verdict.isValid)
        assertEquals("run-1", plan.runId)
        // The skeleton becomes a command targeting the reviewed target.
        assertTrue(plan.plan.allCommands.isNotEmpty())
        assertEquals(
            listOf("legacy.system_open_wifi_settings"),
            plan.plan.allCommands.map { it.commandId },
        )
    }

    @Test
    fun preservedLegacyConfigRidesButIsNotExecuted() {
        val plan = pipeline.planLegacy(
            runId = "run-2",
            legacyType = "SYSTEM_OPEN_WIFI_SETTINGS",
            kind = LegacyNodeKind.ACTION,
            schema = openSchema,
            config = listOf(
                LegacyConfigEntry("page", "WIFI"),
                LegacyConfigEntry("futureKey", "42"),
            ),
            semantics = openSemantics,
            capabilityRequirement = openCapability,
        )

        // Lossless carry-through...
        assertEquals(1, plan.preservedKeyCount)
        assertEquals(
            listOf(LegacyConfigEntry("futureKey", "42")),
            plan.preservedConfig,
        )
        // ...but the executable plan carries no unconsumed legacy payload:
        // only schema-typed arguments (the page enum token) may appear.
        assertTrue(
            plan.plan.allCommands.all { command ->
                command.arguments.entries.all { it.id.value != "futureKey" }
            },
        )
    }

    @Test
    fun rejectedLegacyTypeNeverReachesThePlanner() {
        try {
            pipeline.planLegacy(
                runId = "run-3",
                legacyType = "NOT_A_REAL_TYPE",
                kind = LegacyNodeKind.ACTION,
                schema = openSchema,
                config = emptyList(),
                semantics = openSemantics,
                capabilityRequirement = openCapability,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("cutover refused"))
        }
    }

    @Test
    fun schemaTypedConfigBecomesTheValidatedSurface() {
        // The consumed legacy key (page) is the schema-typed validated surface:
        // the adapter upgraded it into an enum token matching openSettingsSchema.
        val plan = pipeline.planLegacy(
            runId = "run-4",
            legacyType = "SYSTEM_OPEN_SETTINGS",
            kind = LegacyNodeKind.ACTION,
            schema = openSchema,
            config = listOf(LegacyConfigEntry("page", "SETTINGS")),
            semantics = openSemantics,
            capabilityRequirement = openCapability,
        )

        assertTrue(plan.verdict.isValid)
        assertEquals(
            listOf("legacy.system_open_settings"),
            plan.plan.allCommands.map { it.commandId },
        )
    }

    @Test
    fun schemaMismatchBlocksTheCutover() {
        // openSettingsSchema's enum allowlist rejects an unknown page token,
        // so the cutover refuses before planning (fail closed).
        try {
            pipeline.planLegacy(
                runId = "run-4b",
                legacyType = "SYSTEM_OPEN_SETTINGS",
                kind = LegacyNodeKind.ACTION,
                schema = openSchema,
                config = listOf(LegacyConfigEntry("page", "NOT_A_PAGE")),
                semantics = openSemantics,
                capabilityRequirement = openCapability,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("cutover refused"))
        }
    }

    @Test
    fun defaultPlannerCoversEveryFamilyDeclaredOperation() {
        // Every family-declared command semantics must survive the merge with
        // the default table: cutover plans may use any reviewed operation.
        val declared =
            FamilyPhase21Applications.commandSemantics() +
                FamilyPhase22Communication.commandSemantics() +
                FamilyPhase25AdvancedExternal.commandSemantics()
        val planner = CanonicalRuntimePipeline.defaultPlanner()
        for (semantics in declared) {
            assertTrue(
                "planner lost family declaration for ${semantics.operation.value}",
                planner.declaredOperations().contains(semantics.operation),
            )
        }
        assertTrue(planner.declaredOperations().contains(OperationId("core.operation.set_state")))
    }

    @Test
    fun all237TypesCanonicalizeDeterministicallyThroughTheCutoverAdapter() {
        val settingsPages = PilotOpenFamily.legacyPageMappings
            .associate { it.legacyType to it.pageToken }
        for (rule in LegacyMappingTable.all()) {
            val config = when {
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType == "SYSTEM_OPEN_URL" ->
                    listOf(LegacyConfigEntry("url", "https://example.com"))
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType in settingsPages ->
                    listOf(LegacyConfigEntry("page", settingsPages.getValue(rule.legacyType)))
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType in setOf(
                        "SYSTEM_MEDIA_PLAY_FROM_SEARCH",
                    ) -> listOf(LegacyConfigEntry("query", "jazz"))
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType in setOf(
                        // Skeleton state actions (T15).
                        "SYSTEM_WIFI", "SYSTEM_BLUETOOTH", "SYSTEM_MOBILE_DATA",
                        "SYSTEM_HOTSPOT", "SYSTEM_NFC", "SYSTEM_AIRPLANE_MODE",
                        "SYSTEM_DATA_SAVER", "SYSTEM_DATA_ROAMING", "SYSTEM_WIFI_SCANNING",
                        "SYSTEM_POWER_SAVER", "SYSTEM_ADAPTIVE_BATTERY",
                        "SYSTEM_DARK_MODE", "SYSTEM_DND", "SYSTEM_LOCATION",
                        "SYSTEM_AUTO_ROTATE", "SYSTEM_BRIGHTNESS_ADAPTIVE",
                        "SYSTEM_FLASHLIGHT",
                        // T20 display booleans.
                        "SYSTEM_ALWAYS_ON_DISPLAY", "SYSTEM_ANIMATIONS",
                        "SYSTEM_AUTO_BRIGHTNESS", "SYSTEM_COLOR_INVERSION",
                        "SYSTEM_EXTRA_DIM", "SYSTEM_GRAYSCALE", "SYSTEM_NIGHT_LIGHT",
                        "SYSTEM_POINTER_LOCATION", "SYSTEM_SCREENSAVER",
                        "SYSTEM_SCREEN_ROTATION", "SYSTEM_SHOW_TAPS", "SYSTEM_STAY_AWAKE",
                        // T20 sound booleans.
                        "SYSTEM_CAMERA_SHUTTER_SOUND", "SYSTEM_HAPTIC_FEEDBACK",
                        "SYSTEM_SOUND_EFFECTS", "SYSTEM_VIBRATE",
                    ) -> listOf(LegacyConfigEntry("enabled", "true"))
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType in setOf(
                        "APPLICATION_CLOSE_APP", "SYSTEM_FORCE_STOP_APP",
                        "SYSTEM_ENABLE_APP", "SYSTEM_DISABLE_APP",
                        "SYSTEM_CLEAR_APP_DATA", "SYSTEM_UNINSTALL_APP",
                        "SYSTEM_INSTALL_APK", "SYSTEM_UPDATE_GOOGLE_PLAY_APPS",
                    ) -> listOf(LegacyConfigEntry("packages", "com.example.app"))
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType == "SYSTEM_RINGER_MODE" ->
                    listOf(LegacyConfigEntry("mode", "NORMAL"))
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType in setOf(
                        "SYSTEM_NETWORK_MODE", "SYSTEM_PRIVATE_DNS",
                        "SYSTEM_BLUETOOTH_DISCOVERABILITY", "SYSTEM_WIFI_SLEEP_POLICY",
                        "SYSTEM_BRIGHTNESS", "SYSTEM_DISPLAY_DENSITY", "SYSTEM_FONT_SCALE",
                        "SYSTEM_SCREENSAVER_TIMEOUT", "SYSTEM_SCREEN_TIMEOUT",
                        "SYSTEM_HAPTIC_INTENSITY", "SYSTEM_RING_VOLUME", "SYSTEM_STREAM_VOLUME",
                        "SYSTEM_VOLUME", "SYSTEM_SET_RINGTONE",
                        "SYSTEM_SET_NOTIFICATION_TONE", "SYSTEM_CALL_VIBRATION",
                        "SYSTEM_VIBRATE_PATTERN", "SYSTEM_BATTERY_SAVER_THRESHOLD",
                        "SYSTEM_CHARGING_LIMIT", "SYSTEM_CHARGING_FEEDBACK",
                        "SYSTEM_LOCATION_MODE",
                    ) -> listOf(LegacyConfigEntry("value", "50"))
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType in setOf(
                        "SYSTEM_WIFI_CONNECT", "SYSTEM_WIFI_FORGET",
                        "SYSTEM_WIFI_SCAN_NOW", "SYSTEM_BLUETOOTH_SCAN",
                    ) -> listOf(
                    LegacyConfigEntry("ssid", "HomeWiFi"),
                    LegacyConfigEntry("password", "correct-horse"),
                )
                rule.kind == LegacyNodeKind.TRIGGER &&
                    rule.legacyType == "LOCATION" ->
                    listOf(
                        LegacyConfigEntry("radius", "150"),
                        LegacyConfigEntry("transition", "ENTER"),
                    )
                rule.kind == LegacyNodeKind.TRIGGER &&
                    rule.legacyType == "PLUGIN_EVENT" ->
                    listOf(LegacyConfigEntry("plugin_id", "com.example.plugin"))
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType == "SYSTEM_SET_ALARM" ->
                    listOf(
                        LegacyConfigEntry("hour", "7"),
                        LegacyConfigEntry("minute", "30"),
                    )
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType == "SYSTEM_SET_TIMER" ->
                    listOf(
                        LegacyConfigEntry("seconds", "300"),
                        LegacyConfigEntry("message", "Timer"),
                        LegacyConfigEntry("skipUi", "false"),
                    )
                rule.kind == LegacyNodeKind.ACTION &&
                    rule.legacyType == "SYSTEM_WAIT" ->
                    listOf(LegacyConfigEntry("duration", "1000"))
                else -> emptyList()
            }
            val outcome = pipeline.canonicalize(
                LegacyNodeInput(rule.legacyType, rule.kind, config),
            )
            assertTrue(
                "cutover adapter rejected ${rule.legacyType}: $outcome",
                outcome is LegacyAdapterOutcome.Canonicalized,
            )
        }
    }

    @Test
    fun structuredSetValueUsesDeclaredPrimaryDefaultAsPayload() {
        val network = pipeline.planLegacy(
            runId = "structured-network",
            legacyType = "SYSTEM_NETWORK_MODE",
            kind = LegacyNodeKind.ACTION,
            schema = LegacyCatalogCanonicalContractNormalizer.normalize(
                definition = AutomationNodeCatalog.definitionFor(
                    com.nexaflow.domain.models.ActionType.SYSTEM_NETWORK_MODE,
                ),
                node = requireNotNull(
                    (pipeline.canonicalize(
                        LegacyNodeInput(
                            "SYSTEM_NETWORK_MODE",
                            LegacyNodeKind.ACTION,
                            emptyList(),
                        ),
                    ) as LegacyAdapterOutcome.Canonicalized).node,
                ),
                config = emptyList(),
                kind = NodeSchemaKind.ACTION,
            ).schema,
            config = emptyList(),
            semantics = openSemantics,
            capabilityRequirement = CapabilityRequirement.Capability(
                CapabilityId.SYSTEM_SETTING_WRITE,
            ),
        )
        assertTrue(network.node is SetValueNode)
        assertEquals(
            TextValue("AUTO"),
            (network.node as SetValueNode).value,
        )
    }

    @Test
    fun systemSettingDeclaresLegacyEmptyValueDefault() {
        val definition = AutomationNodeCatalog.definitionFor(
            com.nexaflow.domain.models.ActionType.SYSTEM_SET_SETTING,
        )
        val valueField = requireNotNull(
            definition.configuration.fields.firstOrNull { it.key == "value" },
        )
        assertEquals("", valueField.defaultValue)
    }

    @Test
    fun cutoverPathIsDeterministic() {
        val input = { runId: String ->
            pipeline.planLegacy(
                runId = runId,
                legacyType = "SYSTEM_OPEN_WIFI_SETTINGS",
                kind = LegacyNodeKind.ACTION,
                schema = openSchema,
                config = listOf(LegacyConfigEntry("page", "WIFI")),
                semantics = openSemantics,
                capabilityRequirement = openCapability,
            )
        }
        val first = input("run-a")
        val second = input("run-a")

        assertEquals(first.verdict, second.verdict)
        assertEquals(first.plan, second.plan)
        assertEquals(first.preservedConfig, second.preservedConfig)
    }
}
