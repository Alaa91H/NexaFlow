package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSchemaTest {

    private val wifiTarget = TargetId("core.connectivity.wifi")
    private val setState = OperationId("core.operation.set_state")

    private val wifiSchema = NodeSchema(
        schemaId = "core.schema.wifi.set_state",
        kind = NodeSchemaKind.ACTION,
        target = wifiTarget,
        operation = setState,
        title = "Wi-Fi state",
        summaryTemplate = "Wi-Fi {enabled}{{, until {until}}}{{, for {duration_ms}}}",
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("enabled"),
                type = NodeFieldType.BOOLEAN,
                alwaysRequired = true,
            ),
            NodeSchemaField(
                id = CanonicalFieldId("until"),
                type = NodeFieldType.BOOLEAN,
                level = NodeSchemaLevel.ADVANCED,
                visibleWhen = listOf(NodeFieldCondition.Equals(CanonicalFieldId("enabled"), false)),
            ),
            NodeSchemaField(
                id = CanonicalFieldId("duration_ms"),
                type = NodeFieldType.DURATION_MS,
                level = NodeSchemaLevel.ADVANCED,
                default = NodeFieldDefault.ofDuration(300_000L),
                requiredWhen = listOf(NodeFieldCondition.Equals(CanonicalFieldId("until"), true)),
                minimum = 1_000L,
                maximum = 3_600_000L,
            ),
        ),
        conflicts = listOf(
            NodeSchemaConflict(
                field = CanonicalFieldId("until"),
                againstField = CanonicalFieldId("enabled"),
                againstValue = true,
                message = "until cannot be combined with enabling Wi-Fi",
            ),
        ),
        capabilities = listOf(
            NodeSchemaCapability("core.capability.system_setting_write"),
        ),
    )

    @Test
    fun unknownFieldsAreRejected() {
        val violations = validateNodeValues(
            wifiSchema,
            listOf(
                NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(true)),
                NodeFieldValue(CanonicalFieldId("undeclared"), BooleanValue(true)),
            ),
        )

        assertTrue(violations.any { it is UnknownField && it.field.value == "undeclared" })
    }

    @Test
    fun typeMismatchesAreRejected() {
        val violations = validateNodeValues(
            wifiSchema,
            listOf(NodeFieldValue(CanonicalFieldId("enabled"), TextValue("true"))),
        )

        val violation = violations.firstOrNull { it is FieldTypeMismatch } as? FieldTypeMismatch
        assertEquals(CanonicalFieldId("enabled"), violation?.field)
        assertEquals(NodeFieldType.BOOLEAN, violation?.expected)
        assertEquals(CanonicalValueKind.TEXT, violation?.actual)
    }

    @Test
    fun boundsAreEnforced() {
        val violations = validateNodeValues(
            wifiSchema,
            listOf(
                NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(false)),
                NodeFieldValue(CanonicalFieldId("until"), BooleanValue(true)),
                NodeFieldValue(CanonicalFieldId("duration_ms"), DurationValue(500L)),
            ),
        )

        val violation = violations.firstOrNull { it is FieldValueOutOfBounds } as? FieldValueOutOfBounds
        assertEquals(CanonicalFieldId("duration_ms"), violation?.field)
    }

    @Test
    fun unconditionallyRequiredFieldsAreEnforced() {
        val violations = validateNodeValues(wifiSchema, emptyList())

        assertTrue(violations.any { it is MissingRequiredField && it.field.value == "enabled" })
    }

    @Test
    fun conditionallyRequiredFieldsAreEnforced() {
        val violations = validateNodeValues(
            wifiSchema,
            listOf(
                NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(false)),
                NodeFieldValue(CanonicalFieldId("until"), BooleanValue(true)),
            ),
        )

        assertTrue(violations.any { it is MissingRequiredField && it.field.value == "duration_ms" })
    }

    @Test
    fun invisibleFieldsAreRejectedWhenSupplied() {
        // until is only visible when enabled=false; supplying it while
        // enabled=true is a violation even though the type matches.
        val violations = validateNodeValues(
            wifiSchema,
            listOf(
                NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(true)),
                NodeFieldValue(CanonicalFieldId("until"), BooleanValue(true)),
            ),
        )

        assertTrue(violations.any { it is InvisibleFieldSupplied && it.field.value == "until" })
    }

    @Test
    fun declaredConflictsAreDetected() {
        val violations = validateNodeValues(
            wifiSchema,
            listOf(
                NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(true)),
                NodeFieldValue(CanonicalFieldId("until"), BooleanValue(true)),
            ),
        )

        assertTrue(violations.any { it is SchemaConflictDetected })
    }

    @Test
    fun declaredDefaultsAreTheOnlyDefaultSource() {
        val defaults = defaultsOf(wifiSchema)

        assertEquals(1, defaults.size)
        assertEquals(CanonicalFieldId("duration_ms"), defaults.first().field)
        assertEquals(DurationValue(300_000L), defaults.first().value)
    }

    @Test
    fun defaultFactoryRejectsExpressions() {
        try {
            NodeFieldDefault(ExpressionValue("battery.level", CanonicalValueKind.INTEGER))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun defaultFactoryRejectsCollectionsAndSecrets() {
        try {
            NodeFieldDefault(
                CollectionValue(
                    elementKind = CanonicalValueKind.TEXT,
                    values = listOf(TextValue("a")),
                ),
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            NodeFieldDefault(SecretReferenceValue("secret.http_token"))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun summaryRendersValuesAndMasksSecrets() {
        val schema = wifiSchema.copy(
            summaryTemplate = "Wi-Fi {enabled}{{, {token}}}",
            fields = wifiSchema.fields + NodeSchemaField(
                id = CanonicalFieldId("token"),
                type = NodeFieldType.SECRET_REFERENCE,
                level = NodeSchemaLevel.EXPERT,
            ),
        )

        val summary = NodeSummaryFormatter.summarize(
            schema,
            listOf(
                NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(true)),
                NodeFieldValue(CanonicalFieldId("token"), SecretReferenceValue("secret.http_token")),
            ),
        )

        assertEquals("Wi-Fi On, ••••", summary)
    }

    @Test
    fun summaryOmitsAbsentOptionalFields() {
        val summary = NodeSummaryFormatter.summarize(
            wifiSchema,
            listOf(NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(true))),
        )

        assertEquals("Wi-Fi On", summary)
    }

    @Test
    fun summaryTemplateReferencingUndeclaredFieldFailsClosed() {
        val broken = wifiSchema.copy(summaryTemplate = "Wi-Fi {nope}")

        try {
            NodeSummaryFormatter.summarize(broken, emptyList())
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun registryRejectsDuplicateRegistrations() {
        try {
            NodeSchemaRegistry(listOf(wifiSchema, wifiSchema))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun registryResolvesByTargetAndOperation() {
        val registry = NodeSchemaRegistry(listOf(wifiSchema))

        assertEquals(wifiSchema, registry.schemaFor(wifiTarget, operation = setState))
        assertEquals(null, registry.schemaFor(wifiTarget, predicate = PredicateId("core.predicate.match_state")))
    }

    @Test
    fun enumTokenAllowlistIsEnforced() {
        val schema = NodeSchema(
            schemaId = "core.schema.ringer.set_state",
            kind = NodeSchemaKind.ACTION,
            target = TargetId("core.audio.ringer_mode"),
            operation = setState,
            title = "Ringer mode",
            summaryTemplate = "Ringer {mode}",
            fields = listOf(
                NodeSchemaField(
                    id = CanonicalFieldId("mode"),
                    type = NodeFieldType.ENUM_TOKEN,
                    alwaysRequired = true,
                    enumType = "core.audio.ringer_mode",
                    allowedTokens = listOf("NORMAL", "SILENT", "VIBRATE"),
                ),
            ),
        )

        val ok = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("mode"),
                    EnumTokenValue("core.audio.ringer_mode", "VIBRATE"),
                ),
            ),
        )
        assertTrue(ok.isEmpty())

        val bad = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("mode"),
                    EnumTokenValue("core.audio.ringer_mode", "LOUD"),
                ),
            ),
        )
        assertTrue(bad.any { it is EnumTokenNotAllowed && it.token == "LOUD" })

        val wrongType = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("mode"),
                    EnumTokenValue("core.audio.other", "VIBRATE"),
                ),
            ),
        )
        assertTrue(wrongType.any { it is EnumTokenNotAllowed })
    }

    @Test
    fun collectionElementKindIsValidated() {
        val schema = NodeSchema(
            schemaId = "core.schema.application.packages",
            kind = NodeSchemaKind.ACTION,
            target = TargetId("core.application.package"),
            operation = OperationId("core.operation.uninstall"),
            title = "Packages",
            summaryTemplate = "Packages {packages}",
            fields = listOf(
                NodeSchemaField(
                    id = CanonicalFieldId("packages"),
                    type = NodeFieldType.COLLECTION,
                    collectionElementKind = CanonicalValueKind.PACKAGE_ID,
                    alwaysRequired = true,
                ),
            ),
        )

        val valid = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("packages"),
                    CollectionValue(
                        CanonicalValueKind.PACKAGE_ID,
                        listOf(PackageIdValue("com.example.app")),
                    ),
                ),
            ),
        )
        assertTrue(valid.isEmpty())

        val invalid = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(
                    CanonicalFieldId("packages"),
                    CollectionValue(
                        CanonicalValueKind.TEXT,
                        listOf(TextValue("com.example.app")),
                    ),
                ),
            ),
        )
        assertTrue(invalid.any { it is CollectionElementKindMismatch })
    }

    @Test
    fun ruleEvaluationIsDeterministic() {
        val values = listOf(
            NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(true)),
            NodeFieldValue(CanonicalFieldId("until"), BooleanValue(true)),
        )

        assertEquals(
            validateNodeValues(wifiSchema, values),
            validateNodeValues(wifiSchema, values),
        )
    }
}
