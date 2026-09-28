package com.nexaflow.domain.canonical

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CanonicalAstTest {

    private val json = Json {
        classDiscriminator = "_type"
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun typedValuesRoundTripWithoutLegacyStringMaps() {
        val values: List<CanonicalValue> = listOf(
            BooleanValue(true),
            IntegerValue(42),
            DecimalValue("12.50"),
            TextValue("hello"),
            PercentageValue("35.5"),
            DurationValue(1500),
            TimestampValue(123456789),
            TimeOfDayValue(9 * 60 + 30),
            DateValue("2026-09-28"),
            TimezoneValue("Europe/Berlin"),
            PackageIdValue("com.example.app"),
            UriValue("https://example.com/path"),
            CoordinateValue(51.43, 6.76),
            EnumTokenValue("core.audio.ringer_mode", "NORMAL"),
            JsonValue(JsonPrimitive("value")),
            SecretReferenceValue("secret.http_token"),
            CollectionValue(
                elementKind = CanonicalValueKind.PACKAGE_ID,
                values = listOf(
                    PackageIdValue("com.example.one"),
                    PackageIdValue("com.example.two")
                )
            ),
            ExpressionValue(
                source = "battery.level",
                resultKind = CanonicalValueKind.INTEGER
            )
        )

        values.forEach { value ->
            val encoded = json.encodeToString<CanonicalValue>(value)
            val decoded = json.decodeFromString<CanonicalValue>(encoded)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun canonicalWorkflowAstRoundTripsWithTypedNodes() {
        val conditionId = CanonicalNodeId("cond.wifi")
        val workflow = CanonicalWorkflowAst(
            root = SequenceNode(
                id = CanonicalNodeId("root"),
                children = listOf(
                    BranchNode(
                        id = CanonicalNodeId("branch.network"),
                        condition = ObservedConditionNode(
                            id = conditionId,
                            observation = ObserveNode(
                                id = conditionId,
                                target = TargetId("core.connectivity.wifi"),
                                predicate = PredicateId("core.predicate.match_state"),
                                arguments = CanonicalArguments(
                                    listOf(
                                        CanonicalArgument(
                                            CanonicalFieldId("connected"),
                                            BooleanValue(true)
                                        )
                                    )
                                )
                            )
                        ),
                        ifTrue = SetStateNode(
                            id = CanonicalNodeId("action.bluetooth"),
                            target = TargetId("core.connectivity.bluetooth"),
                            state = BooleanValue(true)
                        ),
                        ifFalse = SetValueNode(
                            id = CanonicalNodeId("action.brightness"),
                            target = TargetId("core.display.brightness"),
                            value = PercentageValue("30")
                        )
                    ),
                    WaitNode(
                        id = CanonicalNodeId("wait.1"),
                        duration = DurationValue(500)
                    ),
                    SendNode(
                        id = CanonicalNodeId("notify.1"),
                        target = TargetId("core.notification"),
                        arguments = CanonicalArguments(
                            listOf(
                                CanonicalArgument(
                                    CanonicalFieldId("text"),
                                    TextValue("done")
                                )
                            )
                        )
                    )
                )
            )
        )

        val encoded = json.encodeToString(workflow)
        val decoded = json.decodeFromString<CanonicalWorkflowAst>(encoded)

        assertEquals(workflow, decoded)
        assertTrue(encoded.contains("core.connectivity.wifi"))
        assertTrue(encoded.contains("percentage"))
        assertTrue(encoded.contains("_type"))
    }

    @Test
    fun duplicateNodeIdsFailClosed() {
        expectIllegalArgument {
            CanonicalWorkflowAst(
                root = SequenceNode(
                    id = CanonicalNodeId("root"),
                    children = listOf(
                        WaitNode(CanonicalNodeId("same"), DurationValue(1)),
                        WaitNode(CanonicalNodeId("same"), DurationValue(2))
                    )
                )
            )
        }
    }

    @Test
    fun invalidTypedValuesFailClosed() {
        expectIllegalArgument { PercentageValue("-1") }
        expectIllegalArgument { PercentageValue("101") }
        expectIllegalArgument { DurationValue(-1) }
        expectIllegalArgument { TimeOfDayValue(1440) }
        expectIllegalArgument { DateValue("28-09-2026") }
        expectIllegalArgument { TimezoneValue("Mars/Olympus") }
        expectIllegalArgument { PackageIdValue("not-a-package") }
        expectIllegalArgument { UriValue("example.com/no-scheme") }
        expectIllegalArgument { CoordinateValue(91.0, 0.0) }
        expectIllegalArgument {
            CollectionValue(
                elementKind = CanonicalValueKind.INTEGER,
                values = listOf(TextValue("wrong"))
            )
        }
        expectIllegalArgument {
            ExpressionValue("x", CanonicalValueKind.EXPRESSION)
        }
    }

    @Test
    fun canonicalArgumentsRejectDuplicateFieldIds() {
        expectIllegalArgument {
            CanonicalArguments(
                listOf(
                    CanonicalArgument(CanonicalFieldId("value"), IntegerValue(1)),
                    CanonicalArgument(CanonicalFieldId("value"), IntegerValue(2))
                )
            )
        }
    }

    @Test
    fun secretValuePersistsOnlyAReference() {
        val value: CanonicalValue = SecretReferenceValue("secret.webhook_token")
        val encoded = json.encodeToString(value)

        assertTrue(encoded.contains("secret.webhook_token"))
        assertTrue(!encoded.contains("actual-token"))
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
