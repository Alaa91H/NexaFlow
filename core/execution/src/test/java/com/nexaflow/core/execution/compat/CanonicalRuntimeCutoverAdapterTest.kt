package com.nexaflow.core.execution.compat

import com.nexaflow.domain.canonical.CanonicalFieldId
import com.nexaflow.domain.canonical.CanonicalNode
import com.nexaflow.domain.canonical.CanonicalActionNode
import com.nexaflow.domain.canonical.DurationValue
import com.nexaflow.domain.canonical.InvokeNode
import com.nexaflow.domain.canonical.LegacyAdapterOutcome
import com.nexaflow.domain.canonical.LegacyCanonicalAdapter
import com.nexaflow.domain.canonical.LegacyConfigEntry
import com.nexaflow.domain.canonical.LegacyMappingTable
import com.nexaflow.domain.canonical.LegacyNodeInput
import com.nexaflow.domain.canonical.LegacyNodeKind
import com.nexaflow.domain.canonical.ObserveNode
import com.nexaflow.domain.canonical.SecretReferenceValue
import com.nexaflow.domain.canonical.EnumTokenValue
import com.nexaflow.domain.canonical.ExpressionValue
import com.nexaflow.domain.canonical.IntegerValue
import com.nexaflow.domain.canonical.TextValue
import com.nexaflow.domain.canonical.TimeOfDayValue
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.AutomationNodeDefinition
import com.nexaflow.domain.catalog.NodeConfigField
import com.nexaflow.domain.catalog.NodeConfigValueType
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalRuntimeCutoverAdapterTest {

    private val cutover = CanonicalRuntimeCutoverAdapter()
    private val baseAdapter = LegacyCanonicalAdapter(LegacyMappingTable.all())

    @Test
    fun ringerModeUsesRealModeKeyAndDeclaredDefault() {
        val explicit = cutover.prepareAction(
            Action(ActionType.SYSTEM_RINGER_MODE, mapOf("mode" to "VIBRATE")),
            runId = "run-ringer-explicit",
            instanceId = "v3.action.0",
        )
        assertEquals(
            EnumTokenValue("compat.system_ringer_mode.mode", "VIBRATE"),
            explicit.command.arguments[CanonicalFieldId("mode")],
        )

        val defaulted = cutover.prepareAction(
            Action(ActionType.SYSTEM_RINGER_MODE, emptyMap()),
            runId = "run-ringer-default",
            instanceId = "v3.action.0",
        )
        assertEquals(
            EnumTokenValue("compat.system_ringer_mode.mode", "NORMAL"),
            defaulted.command.arguments[CanonicalFieldId("mode")],
        )
    }

    @Test
    fun alarmUsesHourMinuteInsteadOfInventedTimeKey() {
        val prepared = cutover.prepareAction(
            Action(
                ActionType.SYSTEM_SET_ALARM,
                mapOf("hour" to "7", "minute" to "30"),
            ),
            runId = "run-alarm",
            instanceId = "v3.action.0",
        )
        assertEquals(
            IntegerValue(7),
            prepared.command.arguments[CanonicalFieldId("hour")],
        )
        assertEquals(
            IntegerValue(30),
            prepared.command.arguments[CanonicalFieldId("minute")],
        )
    }

    @Test
    fun waitUsesCatalogSecondsDefaultAsTypedDuration() {
        val prepared = cutover.prepareAction(
            Action(ActionType.SYSTEM_WAIT, emptyMap()),
            runId = "run-wait",
            instanceId = "v3.action.0",
        )
        assertEquals(
            DurationValue(5_000),
            prepared.command.arguments[CanonicalFieldId("seconds")],
        )
    }

    @Test
    fun timerDurationBoundsUseCanonicalMilliseconds() {
        val prepared = cutover.prepareAction(
            Action(ActionType.SYSTEM_SET_TIMER, emptyMap()),
            runId = "run-timer-default",
            instanceId = "v3.action.timer",
        )
        assertEquals(
            DurationValue(300_000),
            prepared.command.arguments[CanonicalFieldId("seconds")],
        )
    }

    @Test
    fun installApkUsesPathContract() {
        val prepared = cutover.prepareAction(
            Action(
                ActionType.SYSTEM_INSTALL_APK,
                mapOf("path" to "/data/local/tmp/app.apk"),
            ),
            runId = "run-install",
            instanceId = "v3.action.0",
        )
        assertEquals(
            TextValue("/data/local/tmp/app.apk"),
            prepared.command.arguments[CanonicalFieldId("path")],
        )
    }

    @Test
    fun mediaTransportIdentitySurvivesIntoAtomicCommand() {
        val prepared = cutover.prepareAction(
            Action(ActionType.SYSTEM_MEDIA_NEXT, emptyMap()),
            runId = "run-media",
            instanceId = "v3.action.0",
        )
        assertEquals(
            EnumTokenValue("core.media.command", "NEXT"),
            prepared.command.arguments[CanonicalFieldId("command")],
        )
    }

    @Test
    fun expressionCapableBrightnessPromotesTypedExpressionIntoPayload() {
        val prepared = cutover.prepareAction(
            Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "%BATTERY")),
            runId = "run-expression",
            instanceId = "v3.action.0",
        )
        val payload = prepared.command.payload as ExpressionValue
        assertEquals("%BATTERY", payload.source)
        assertEquals(com.nexaflow.domain.canonical.CanonicalValueKind.INTEGER, payload.resultKind)
    }

    @Test
    fun invalidBrightnessIsRejectedBeforePlanning() {
        try {
            cutover.prepareAction(
                Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "999")),
                runId = "run-invalid",
                instanceId = "v3.action.0",
            )
            throw AssertionError("Expected canonical validation to reject brightness 999")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("cutover refused"))
        }
    }

    @Test
    fun chargerEventTriggerPassesCanonicalAdmissionWithRealEventKey() {
        val prepared = cutover.prepareTrigger(
            Trigger(TriggerType.CHARGER, mapOf("event" to "CONNECTED")),
            runId = "trigger-charger",
            instanceId = "v3.trigger.charger",
        )
        val node = prepared.node as ObserveNode
        assertEquals("v3.trigger.charger", node.id.value)
        assertEquals(
            EnumTokenValue("compat.charger.event", "CONNECTED"),
            node.arguments[CanonicalFieldId("event")],
        )
    }

    @Test
    fun timeRangeTriggerPassesCanonicalAdmissionWithRealRangeKeys() {
        val prepared = cutover.prepareTrigger(
            Trigger(
                TriggerType.TIME,
                mapOf(
                    "timeMode" to "RANGE",
                    "rangeStart" to "22:00",
                    "rangeEnd" to "07:00",
                ),
            ),
            runId = "trigger-time-range",
            instanceId = "v3.trigger.time",
        )
        val node = prepared.node as ObserveNode
        assertEquals("v3.trigger.time", node.id.value)
        assertEquals(TextValue("RANGE"), node.arguments[CanonicalFieldId("timeMode")])
        assertEquals(TimeOfDayValue(22 * 60), node.arguments[CanonicalFieldId("rangeStart")])
        assertEquals(TimeOfDayValue(7 * 60), node.arguments[CanonicalFieldId("rangeEnd")])
    }

    @Test
    fun all233CatalogContractsValidateAndPreserveReviewedIdentity() {
        ActionType.entries.forEachIndexed { index, type ->
            val definition = AutomationNodeCatalog.definitionFor(type)
            val config = sampleConfig(definition)
            val prepared = runCatching {
                cutover.prepareAction(
                    Action(type, config),
                    runId = "matrix-action-$index",
                    instanceId = "v3.action.$index",
                )
            }.getOrElse { failure ->
                throw AssertionError(
                    "canonical action matrix rejected ${type.name}: ${failure.message}",
                    failure,
                )
            }
            val base = baseAdapter.canonicalize(
                LegacyNodeInput(
                    legacyType = type.name,
                    kind = LegacyNodeKind.ACTION,
                    config = config.entries.sortedBy { it.key }
                        .map { LegacyConfigEntry(it.key, it.value) },
                ),
            ) as LegacyAdapterOutcome.Canonicalized
            val baseNode = base.node as InvokeNode
            assertEquals(type.name, baseNode.target, prepared.command.target)
            assertEquals(type.name, baseNode.operation, prepared.command.operation)
            assertEquals("v3.action.$index", prepared.command.commandId)
        }

        TriggerType.entries.forEachIndexed { index, type ->
            val definition = AutomationNodeCatalog.definitionFor(type)
            val config = sampleConfig(definition)
            val prepared = runCatching {
                cutover.prepareTrigger(
                    Trigger(type, config),
                    runId = "matrix-trigger-$index",
                    instanceId = "v3.trigger.$index",
                )
            }.getOrElse { failure ->
                throw AssertionError(
                    "canonical trigger matrix rejected ${type.name}: ${failure.message}",
                    failure,
                )
            }
            val base = baseAdapter.canonicalize(
                LegacyNodeInput(
                    legacyType = type.name,
                    kind = LegacyNodeKind.TRIGGER,
                    config = config.entries.sortedBy { it.key }
                        .map { LegacyConfigEntry(it.key, it.value) },
                ),
            ) as LegacyAdapterOutcome.Canonicalized
            val baseNode = base.node as ObserveNode
            val plannedNode = prepared.node as ObserveNode
            assertEquals(type.name, baseNode.target, plannedNode.target)
            assertEquals(type.name, baseNode.predicate, plannedNode.predicate)
            assertEquals("v3.trigger.$index", plannedNode.id.value)
        }

        assertEquals(233, ActionType.entries.size + TriggerType.entries.size)
    }

    @Test
    fun runtimeCanonicalMetadataNeverRetainsRawSecretFields() {
        val secret = "RAW_SECRET_MUST_NOT_ESCAPE"
        val prepared = cutover.prepareAction(
            Action(
                ActionType.SYSTEM_WIFI_CONNECT,
                mapOf("ssid" to "ExampleWifi", "password" to secret),
            ),
            runId = "run-secret",
            instanceId = "v3.action.secret",
        )

        assertTrue(prepared.preservedConfig.none { it.rawValue == secret })
        assertTrue(
            prepared.command.arguments.entries.any {
                it.value is SecretReferenceValue
            },
        )
        assertTrue(prepared.command.arguments.entries.none {
            it.value.toString().contains(secret)
        })
    }

    private fun sampleConfig(definition: AutomationNodeDefinition): Map<String, String> =
        buildMap {
            definition.configuration.fields.forEach { field ->
                val raw = field.defaultValue ?: if (field.required) sampleValue(field) else null
                if (raw != null) put(field.key, raw)
            }
        }

    private fun sampleValue(field: NodeConfigField): String = when (field.valueType) {
        NodeConfigValueType.STRING -> when (field.key) {
            "path" -> "/tmp/example"
            "receiver" -> "com.example.Receiver"
            "zone", "timezone" -> "Europe/Berlin"
            else -> "sample"
        }
        NodeConfigValueType.INTEGER -> boundedInteger(field).toString()
        NodeConfigValueType.DECIMAL,
        NodeConfigValueType.COORDINATE -> boundedDecimal(field)
        NodeConfigValueType.BOOLEAN -> "true"
        NodeConfigValueType.ENUM -> field.allowedValues.first()
        NodeConfigValueType.TIME -> "12:00"
        NodeConfigValueType.DATE -> "2026-09-29"
        NodeConfigValueType.DURATION_SECONDS -> "5"
        NodeConfigValueType.PACKAGE -> "com.example.app"
        NodeConfigValueType.URL -> "https://example.com"
        NodeConfigValueType.SECRET -> "matrix-secret"
        NodeConfigValueType.JSON -> "{}"
    }

    private fun boundedInteger(field: NodeConfigField): Long {
        val min = field.minValue?.toLong() ?: 0L
        val max = field.maxValue?.toLong() ?: Long.MAX_VALUE
        return 1L.coerceAtLeast(min).coerceAtMost(max)
    }

    private fun boundedDecimal(field: NodeConfigField): String {
        val min = field.minValue ?: -1.0
        val max = field.maxValue ?: 1.0
        return 0.0.coerceAtLeast(min).coerceAtMost(max).toString()
    }

}
