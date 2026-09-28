package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeConfiguratorStateTest {

    private val schema = NodeSchema(
        schemaId = "core.schema.wifi.set_state",
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.connectivity.wifi"),
        operation = OperationId("core.operation.set_state"),
        title = "Wi-Fi state",
        summaryTemplate = "Wi-Fi {enabled}{{, for {duration_ms}}}",
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
        capabilities = listOf(NodeSchemaCapability("core.capability.system_setting_write")),
    )

    /** Schema copy without the duration default, for missing-field scenarios. */
    private val schemaWithoutDefault = schema.copy(
        fields = schema.fields.map { field ->
            if (field.id.value == "duration_ms") field.copy(default = null) else field
        },
    )

    @Test
    fun tabsAreDerivedFromTheSchema() {
        val tabIds = configuratorStateFor(schema).tabs().map { it.id }

        assertTrue("type" in tabIds)
        assertTrue("target" in tabIds)
        assertTrue("conditions" in tabIds)
        assertTrue("advanced" in tabIds)
    }

    @Test
    fun standardSchemaHasNoAdvancedTab() {
        val simple = schema.copy(
            conflicts = emptyList(),
            securityClass = NodeSecurityClass.STANDARD,
        )
        val tabIds = configuratorStateFor(simple).tabs().map { it.id }

        assertFalse("advanced" in tabIds)
    }

    @Test
    fun defaultsSeedTheDraft() {
        val state = configuratorStateFor(schema)

        assertEquals(
            DurationValue(300_000L),
            state.values.first { it.field.value == "duration_ms" }.value,
        )
    }

    @Test
    fun progressiveDisclosureRevealsAdvancedFields() {
        val disabled = configuratorStateFor(schema)
            .setValue(CanonicalFieldId("enabled"), BooleanValue(false))

        assertTrue(disabled.visibleFields().none { it.id.value == "until" })

        val expanded = disabled.expand()
        assertTrue(expanded.visibleFields().any { it.id.value == "until" })
        assertTrue(expanded.disclosure.showsAdvanced)
    }

    @Test
    fun expertLevelShowsExpertFields() {
        val withExpert = schema.copy(
            fields = schema.fields + NodeSchemaField(
                id = CanonicalFieldId("raw"),
                type = NodeFieldType.TEXT,
                level = NodeSchemaLevel.EXPERT,
            ),
        )
        val state = configuratorStateFor(withExpert).expand().expand()

        assertTrue(state.disclosure.showsExpert)
        assertTrue(state.visibleFields().any { it.id.value == "raw" })
    }

    @Test
    fun invisibleFieldsAreNotShown() {
        val enabled = configuratorStateFor(schema)
            .setValue(CanonicalFieldId("enabled"), BooleanValue(true))

        assertTrue(enabled.visibleFields().none { it.id.value == "until" })
    }

    @Test
    fun conditionalRequirementSurfacesAsMissing() {
        // No default duration: once until=true, duration_ms becomes required.
        val withoutDuration = configuratorStateFor(schemaWithoutDefault)
            .setValue(CanonicalFieldId("enabled"), BooleanValue(false))
            .expand()
            .setValue(CanonicalFieldId("until"), BooleanValue(true))

        assertTrue(withoutDuration.missingRequired().any { it.value == "duration_ms" })
        assertFalse(withoutDuration.submittable)

        val complete = withoutDuration.setValue(
            CanonicalFieldId("duration_ms"),
            DurationValue(60_000L),
        )
        assertTrue(complete.missingRequired().isEmpty())
        assertTrue(complete.submittable)
    }

    @Test
    fun liveValidationFlagsConflicts() {
        val conflicting = configuratorStateFor(schema)
            .setValue(CanonicalFieldId("enabled"), BooleanValue(true))
            .expand()
            .setValue(CanonicalFieldId("until"), BooleanValue(true))

        assertTrue(conflicting.validationIssues().any { it is SchemaConflictDetected })
        assertFalse(conflicting.submittable)
    }

    @Test
    fun settingAnUndeclaredValueFailsClosed() {
        val state = configuratorStateFor(schema)

        try {
            state.setValue(CanonicalFieldId("nope"), BooleanValue(true))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun selectionStateFiltersSearchesSortsAndCounts() {
        val selection = SelectionState(
            options = listOf(
                SelectionOption("app.b", "Banana"),
                SelectionOption("app.a", "Apple"),
                SelectionOption("app.c", "Cherry"),
            ),
        )

        assertEquals(listOf("app.a", "app.b", "app.c"), selection.visibleOptions.map { it.id })
        assertEquals(0, selection.selectedCount)

        val toggled = selection.toggle("app.a").toggle("app.c")
        assertEquals(2, toggled.selectedCount)
        assertEquals(1, toggled.toggle("app.c").selectedCount)

        assertEquals(setOf("app.a", "app.b", "app.c"), toggled.selectAll().selectedIds)
        assertTrue(toggled.clearAll().selectedIds.isEmpty())

        // Substring search is case-insensitive and deterministic.
        assertEquals(
            listOf("app.a"),
            selection.copy(searchQuery = "app").visibleOptions.map { it.id },
        )
        assertEquals(
            listOf("app.b"),
            selection.copy(searchQuery = "an").visibleOptions.map { it.id },
        )
    }

    @Test
    fun duplicateSelectionOptionsAreRejected() {
        try {
            SelectionState(
                options = listOf(
                    SelectionOption("x", "X"),
                    SelectionOption("x", "X again"),
                ),
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun selectionAttachesToFieldAndPersists() {
        val selection = SelectionState(
            options = listOf(SelectionOption("app.a", "Apple")),
            selectedIds = setOf("app.a"),
        )
        val state = configuratorStateFor(schema)
            .withSelection(CanonicalFieldId("packages"), selection)

        assertEquals(1, state.selectionFor(CanonicalFieldId("packages")).selectedCount)
        assertEquals(0, state.selectionFor(CanonicalFieldId("unknown")).selectedCount)
    }

    @Test
    fun summaryRendersThroughTheSharedFormatter() {
        val state = configuratorStateFor(schema)
            .setValue(CanonicalFieldId("enabled"), BooleanValue(true))

        // The declared duration default is seeded, so the optional group renders.
        assertEquals("Wi-Fi On, for 300000ms", state.summary())
    }

    @Test
    fun cleanDraftIsSubmittable() {
        val state = configuratorStateFor(schema)
            .setValue(CanonicalFieldId("enabled"), BooleanValue(true))

        assertTrue(state.validationIssues().isEmpty())
        assertTrue(state.missingRequired().isEmpty())
        assertTrue(state.submittable)
    }

    @Test
    fun stateTransitionsAreDeterministic() {
        val build = {
            configuratorStateFor(schema)
                .setValue(CanonicalFieldId("enabled"), BooleanValue(false))
                .expand()
        }

        assertEquals(build(), build())
    }
}
