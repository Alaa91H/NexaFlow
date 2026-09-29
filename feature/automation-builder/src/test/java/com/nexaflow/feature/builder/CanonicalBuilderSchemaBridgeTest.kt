package com.nexaflow.feature.builder

import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.NodeConfigValueType
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
        assertEquals(30, page.allowedTokens.size)
        assertTrue("WIFI" in page.allowedTokens)
        assertTrue("SETTINGS" in page.allowedTokens)
        assertTrue("SYSTEM_UPDATE" in page.allowedTokens)
    }
}
