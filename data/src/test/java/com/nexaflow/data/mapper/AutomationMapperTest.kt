package com.nexaflow.data.mapper

import com.nexaflow.domain.canonical.CanonicalV3WriteState
import com.nexaflow.domain.canonical.CanonicalWorkflowV3Codec
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.MaintenanceKind
import com.nexaflow.domain.models.MaintenanceProfile
import com.nexaflow.domain.models.MaintenanceWindow
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationMapperTest {

    private val automation = Automation(
        id = "a1",
        name = "Night mode",
        description = "Dim the screen at night",
        icon = "dnd",
        iconColor = 0xFF1B62B7,
        backgroundColor = 0xFF111111,
        category = "custom",
        priority = 2,
        enabled = true,
        triggers = listOf(
            Trigger(TriggerType.TIME, config = mapOf("time" to "22:00")),
            Trigger(TriggerType.BATTERY, config = mapOf("direction" to "ABOVE", "above" to "80", "chargerType" to "WIRELESS")),
            Trigger(TriggerType.NOTIFICATION, config = mapOf("packages" to "com.whatsapp", "contains" to "order", "event" to "POSTED")),
            Trigger(TriggerType.CALENDAR, config = mapOf("calendar" to "Personal", "contains" to "meeting", "event" to "EVENT_START", "beforeMinutes" to "15"))
        ),
        actions = listOf(
            Action(ActionType.SYSTEM_BRIGHTNESS, config = mapOf("value" to "40")),
            Action(ActionType.SYSTEM_DND, config = mapOf("enabled" to "true")),
            Action(ActionType.SYSTEM_BLOCK_NOTIFICATION, config = mapOf("package" to "com.game", "enabled" to "true")),
            Action(ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS, config = mapOf("package" to "com.whatsapp"))
        ),
        constraints = listOf(
            Constraint(ConstraintType.WIFI),
            Constraint(ConstraintType.BATTERY, mapOf("direction" to "ABOVE", "level" to "30"))
        ),
        createdAt = 1000L,
        updatedAt = 2000L
    )

    @Test
    fun domainToEntityAndBackRoundTrips() {
        val entity = automation.toEntity()
        assertEquals(automation.id, entity.id)
        assertEquals(automation.name, entity.name)
        assertEquals(automation.actions.size, 4)
        assertEquals(automation.triggers.size, 4)

        assertEquals(automation, entity.toDomain())
    }

    @Test
    fun canonicalNativeWaitRoundTripsRoomWithoutLegacyActionIdentity() {
        val field = com.nexaflow.domain.canonical.CanonicalFieldId("duration_ms")
        val duration = com.nexaflow.domain.canonical.DurationValue(12_000L)
        val schema = com.nexaflow.domain.canonical.CanonicalNativeNodeSchemaRegistry.delay
        val nativeWait = com.nexaflow.domain.canonical.CanonicalWorkflowNode(
            kind = com.nexaflow.domain.canonical.NodeSchemaKind.ACTION,
            definitionId = schema.schemaId,
            schema = schema,
            node = com.nexaflow.domain.canonical.WaitNode(
                com.nexaflow.domain.canonical.CanonicalNodeId("native.action.room.delay"), duration,
            ),
            arguments = listOf(com.nexaflow.domain.canonical.NodeFieldValue(field, duration)),
        )
        val source = automation.copy(actions = emptyList(), canonicalNodes = listOf(nativeWait))
        val entity = source.toEntity()
        assertEquals(CanonicalV3WriteState.V3_READY.name, entity.canonicalWriteState)
        assertEquals(listOf(nativeWait), entity.toDomain().canonicalNodes)
    }

    @Test
    fun domainToEntityWritesTypedCanonicalV3() {
        val entity = automation.toEntity()
        val payload = requireNotNull(entity.canonicalWorkflowJson)
        val document = CanonicalWorkflowV3Codec.decode(payload)

        assertEquals(4, document.schemaVersion)
        assertEquals(automation.id, document.workflowId)
        assertEquals(automation.triggers.size, document.triggers.size)
        assertEquals(automation.actions.size, document.actions.size)
        assertTrue(document.triggers.all { it.node is com.nexaflow.domain.canonical.ObserveNode })
        assertEquals(CanonicalV3WriteState.V3_READY.name, entity.canonicalWriteState)
        assertEquals(null, entity.canonicalWriteErrorCode)
    }

    @Test
    fun numericLegacyEnumAndSparseConfigRoundTripThroughCanonicalV3() {
        val sparse = automation.copy(
            id = "numeric-enum-round-trip",
            triggers = listOf(
                Trigger(
                    TriggerType.TIME,
                    config = mapOf(
                        "time" to "08:15",
                        "weekOfMonth" to "1",
                    ),
                ),
            ),
            actions = emptyList(),
        )

        val entity = sparse.toEntity()
        assertEquals(CanonicalV3WriteState.V3_READY.name, entity.canonicalWriteState)

        val restored = entity.toDomain()
        assertEquals(sparse.triggers.single().config, restored.triggers.single().config)
        assertEquals("1", restored.triggers.single().config["weekOfMonth"])
        assertTrue("intervalUnit" !in restored.triggers.single().config)
        assertTrue("zonePolicy" !in restored.triggers.single().config)
    }

    @Test
    fun punctuationBearingLegacyEnumRoundTripsThroughCanonicalV3() {
        val hashed = automation.copy(
            id = "hash-enum-round-trip",
            triggers = emptyList(),
            actions = listOf(
                Action(
                    ActionType.DATA_HASH,
                    config = mapOf(
                        "operation" to "SHA-256",
                        "input" to "hello",
                    ),
                ),
            ),
        )

        val entity = hashed.toEntity()
        assertEquals(CanonicalV3WriteState.V3_READY.name, entity.canonicalWriteState)

        val restored = entity.toDomain()
        assertEquals(hashed.actions.single().config, restored.actions.single().config)
        assertEquals("SHA-256", restored.actions.single().config["operation"])
    }

    @Test
    fun privilegedAndHttpSecretsStayOutOfV3AndRoundTripThroughFallback() {
        val rootCommand = "settings put secure secret_key super-secret-value"
        val httpBody = "{\"password\":\"body-secret\"}"
        val httpHeaders = "Authorization: Bearer header-secret"
        val authToken = "auth-token-secret"
        val secured = automation.copy(
            id = "sensitive-round-trip",
            triggers = emptyList(),
            actions = listOf(
                Action(
                    ActionType.ADVANCED_ROOT,
                    config = mapOf("command" to rootCommand),
                ),
                Action(
                    ActionType.SYSTEM_HTTP_REQUEST,
                    config = mapOf(
                        "url" to "https://example.com/api",
                        "method" to "POST",
                        "body" to httpBody,
                        "headers" to httpHeaders,
                        "auth_token" to authToken,
                    ),
                ),
            ),
        )

        val entity = secured.toEntity()
        val payload = requireNotNull(entity.canonicalWorkflowJson)

        assertEquals(
            CanonicalV3WriteState.V3_WITH_LEGACY_FALLBACK.name,
            entity.canonicalWriteState,
        )
        assertTrue(!payload.contains(rootCommand))
        assertTrue(!payload.contains("body-secret"))
        assertTrue(!payload.contains("header-secret"))
        assertTrue(!payload.contains(authToken))

        val restored = entity.toDomain()
        assertEquals(secured.actions, restored.actions)
    }

    @Test
    fun invalidCanonicalWriteDegradesExplicitlyWithoutLosingLegacyColumns() {
        val invalid = automation.copy(
            actions = listOf(
                Action(ActionType.SYSTEM_BRIGHTNESS, config = mapOf("value" to "999"))
            )
        )
        val entity = invalid.toEntity()

        assertEquals(null, entity.canonicalWorkflowJson)
        assertEquals(
            CanonicalV3WriteState.LEGACY_ONLY_DEGRADED.name,
            entity.canonicalWriteState
        )
        assertEquals("CANONICAL_VALIDATION_REJECTED", entity.canonicalWriteErrorCode)
        assertTrue(entity.actionsJson.contains("SYSTEM_BRIGHTNESS"))
        assertTrue(entity.actionsJson.contains("999"))
    }

    @Test
    fun roundTripPreservesJsonColumns() {
        val entity = automation.toEntity()
        val restored = entity.toDomain()
        assertEquals(automation.triggers, restored.triggers)
        assertEquals(automation.actions, restored.actions)
    }

    @Test
    fun constraintsRoundTrip() {
        val entity = automation.toEntity()
        val restored = entity.toDomain()
        assertEquals(automation.constraints, restored.constraints)
        assertTrue(
            "constraintsJson must be persisted",
            entity.constraintsJson.contains("WIFI") && entity.constraintsJson.contains("BATTERY")
        )
    }

    @Test
    fun maintenanceProfileRoundTripsWithAutomation() {
        val maintenance = automation.copy(
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.NIGHT,
                window = MaintenanceWindow(
                    startTime = "02:00",
                    endTime = "05:00",
                    allowedDays = setOf(1, 2, 3, 4, 5),
                    minimumBatteryPercent = 50,
                    chargingRequired = true,
                    unmeteredWifiRequired = true
                )
            )
        )

        val entity = maintenance.toEntity()
        assertTrue(entity.maintenanceJson.orEmpty().contains("NIGHT"))
        assertEquals(maintenance, entity.toDomain())
    }

    @Test
    fun triggerMatchUsesCanonicalV3WhenPresentAndLegacyFallbackOtherwise() {
        val all = automation.copy(triggerMatch = TriggerMatchMode.ALL)
        val canonical = all.toEntity()
        assertEquals(TriggerMatchMode.ALL, canonical.toDomain().triggerMatch)

        // A valid V3 graph is authoritative even if the legacy scalar contains
        // a future/unknown value. This is the T27 dual-read contract.
        val futureWithV3 = canonical.copy(triggerMatch = "FUTURE_MODE")
        assertEquals(TriggerMatchMode.ALL, futureWithV3.toDomain().triggerMatch)

        // Pre-V3 / legacy-only rows retain the old defensive fallback.
        val futureLegacyOnly = futureWithV3.copy(
            canonicalWorkflowJson = null,
            canonicalWriteState = "LEGACY_ONLY",
            canonicalWriteErrorCode = null,
        )
        assertEquals(TriggerMatchMode.ANY, futureLegacyOnly.toDomain().triggerMatch)
    }

    @Test
    fun emptyListsRoundTrip() {
        val plain = automation.copy(
            triggers = emptyList(),
            actions = emptyList(),
            constraints = emptyList()
        )
        assertEquals(plain, plain.toEntity().toDomain())
    }
}
