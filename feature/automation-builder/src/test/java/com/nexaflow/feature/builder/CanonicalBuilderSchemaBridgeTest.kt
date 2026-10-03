package com.nexaflow.feature.builder

import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.NodeConfigValueType
import com.nexaflow.domain.canonical.CanonicalFieldId
import com.nexaflow.domain.canonical.CanonicalValueKind
import com.nexaflow.domain.canonical.NodeFieldType
import com.nexaflow.domain.canonical.NodeSchemaField
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalBuilderSchemaBridgeTest {

    private val genericTypes = setOf(
        NodeConfigValueType.STRING,
        NodeConfigValueType.INTEGER,
        NodeConfigValueType.BOOLEAN,
        NodeConfigValueType.ENUM,
        NodeConfigValueType.URL,
    )

    @Test
    fun everyNonAdvancedSimpleActionUsesCanonicalSchemaEditor() {
        val expected = ActionType.entries.filter { type ->
            type != ActionType.SYSTEM_NETWORK_MODE &&
                AutomationOptionCatalog.tierFor(type) != OptionTier.ADVANCED &&
                AutomationNodeCatalog.definitionFor(type)
                    .configuration.fields
                    .all { it.valueType in genericTypes }
        }

        assertTrue(expected.isNotEmpty())
        expected.forEach { type ->
            assertNotNull(
                "$type should use the schema-driven editor",
                CanonicalBuilderSchemaBridge.editingBindingForAction(type),
            )
        }
    }

    @Test
    fun everyNonAdvancedSimpleTriggerUsesCanonicalSchemaEditor() {
        val expected = TriggerType.entries.filter { type ->
            AutomationOptionCatalog.tierFor(type) != OptionTier.ADVANCED &&
                AutomationNodeCatalog.definitionFor(type)
                    .configuration.fields
                    .all { it.valueType in genericTypes }
        }

        assertTrue(expected.isNotEmpty())
        expected.forEach { type ->
            assertNotNull(
                "$type should use the schema-driven editor",
                CanonicalBuilderSchemaBridge.editingBindingForTrigger(type),
            )
        }
    }

    @Test
    fun specializedContractsStayOnPurposeBuiltEditors() {
        assertNull(
            CanonicalBuilderSchemaBridge.editingBindingForAction(
                ActionType.SYSTEM_NETWORK_MODE,
            ),
        )
        assertNull(
            CanonicalBuilderSchemaBridge.editingBindingForAction(
                ActionType.SYSTEM_WIFI_CONNECT,
            ),
        )
        assertNull(
            CanonicalBuilderSchemaBridge.editingBindingForAction(
                ActionType.SYSTEM_OPEN_APP,
            ),
        )
        assertNull(
            CanonicalBuilderSchemaBridge.editingBindingForAction(
                ActionType.SYSTEM_HTTP_REQUEST,
            ),
        )
        assertNull(
            CanonicalBuilderSchemaBridge.editingBindingForTrigger(TriggerType.TIME),
        )
        assertNull(
            CanonicalBuilderSchemaBridge.editingBindingForTrigger(TriggerType.LOCATION),
        )
    }

    @Test
    fun unifiedSettingsBindingIsOneCanonicalPageContract() {
        val binding = requireNotNull(
            CanonicalBuilderSchemaBridge.editingBindingForAction(
                ActionType.SYSTEM_OPEN_SETTINGS,
            ),
        )
        val page = requireNotNull(
            binding.schema.fields.singleOrNull { it.id.value == "page" },
        )
        assertEquals(29, page.allowedTokens.size)
        assertTrue("WIFI" in page.allowedTokens)
        assertTrue("SETTINGS" in page.allowedTokens)
        assertTrue("SYSTEM_UPDATE" in page.allowedTokens)
    }

    @Test
    fun schemaEditorSurfacesMalformedTypedInputAndDoesNotSilentlyDropIt() {
        val binding = requireNotNull(
            CanonicalBuilderSchemaBridge.editingBindingForAction(ActionType.SYSTEM_BRIGHTNESS),
        )

        val errors = canonicalFieldParseErrors(
            schema = binding.schema,
            legacyKeys = binding.legacyKeys,
            config = mapOf("value" to "not-a-number"),
        )

        assertTrue("value" in errors)
    }

    @Test
    fun specializedTypedValuesRoundTripThroughLegacyEditorBoundary() {
        val cases = listOf(
            NodeFieldType.TIME_OF_DAY to "08:35",
            NodeFieldType.DATE to "2026-10-03",
            NodeFieldType.TIMEZONE_ID to "Europe/Berlin",
            NodeFieldType.PACKAGE_ID to "com.example.app",
            NodeFieldType.URI to "content://com.example/items/1",
            NodeFieldType.COORDINATE to "52.52,13.405",
            NodeFieldType.JSON to "{\"enabled\":true}",
        )

        cases.forEach { (type, raw) ->
            val parsed = requireNotNull(parseCanonicalField(NodeSchemaField(CanonicalFieldId("value"), type), raw))
            assertEquals("round trip for $type", raw, canonicalValueToLegacy(parsed))
        }

        val collection = NodeSchemaField(
            id = CanonicalFieldId("packages"),
            type = NodeFieldType.COLLECTION,
            collectionElementKind = CanonicalValueKind.PACKAGE_ID,
        )
        val parsedList = requireNotNull(parseCanonicalField(collection, "com.one.app|org.two.app"))
        assertEquals("com.one.app|org.two.app", canonicalValueToLegacy(parsedList))
    }

    @Test
    fun secretReferenceEditorPersistsOnlyOpaqueReferenceId() {
        val field = NodeSchemaField(CanonicalFieldId("credential"), NodeFieldType.SECRET_REFERENCE)
        val reference = requireNotNull(parseCanonicalField(field, "service.primary"))
        assertEquals("service.primary", canonicalValueToLegacy(reference))
        assertNull(parseCanonicalField(field, "raw secret value"))
        assertNull(parseCanonicalField(field, "bad/identifier"))
    }

    @Test
    fun durationEditorConvertsDisplayUnitsWithoutLosingCanonicalMilliseconds() {
        assertEquals(1_500L, durationInputToMillis("1.5", 1_000L))
        assertEquals(90_000L, durationInputToMillis("1.5", 60_000L))
        assertEquals("1.5", durationDisplayValue("90000", 60_000L))
        assertEquals(null, durationInputToMillis("1.5", 1L))
        assertEquals(null, durationInputToMillis("9223372036854775808", 1L))
    }
}
