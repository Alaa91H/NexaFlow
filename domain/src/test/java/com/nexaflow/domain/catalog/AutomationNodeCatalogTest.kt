package com.nexaflow.domain.catalog

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationNodeCatalogTest {

    @Test
    fun catalog_coversEveryPersistedTriggerAndActionExactlyOnce() {
        assertEquals(TriggerType.entries.size, AutomationNodeCatalog.triggerDefinitions.size)
        assertEquals(ActionType.entries.size, AutomationNodeCatalog.actionDefinitions.size)

        assertEquals(
            TriggerType.entries.map { it.name }.toSet(),
            AutomationNodeCatalog.triggerDefinitions.map { it.legacyTypeName }.toSet()
        )
        assertEquals(
            ActionType.entries.map { it.name }.toSet(),
            AutomationNodeCatalog.actionDefinitions.map { it.legacyTypeName }.toSet()
        )

        val ids = AutomationNodeCatalog.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun catalog_keepsLegacyAndDedicatedPluginTriggersOutOfGenericDiscovery() {
        val connectivity = AutomationNodeCatalog.definitionFor(TriggerType.CONNECTIVITY)
        val plugin = AutomationNodeCatalog.definitionFor(TriggerType.PLUGIN_EVENT)

        assertEquals(AutomationNodeVisibility.LEGACY_HIDDEN, connectivity.visibility)
        assertEquals(AutomationNodeVisibility.CONFIGURATION_ONLY, plugin.visibility)
        assertFalse(connectivity in AutomationNodeCatalog.discoverableTriggers)
        assertFalse(plugin in AutomationNodeCatalog.discoverableTriggers)
    }

    @Test
    fun catalog_exposesStableSemanticFamilies() {
        assertEquals(
            AutomationNodeFamily.CONNECTIVITY,
            AutomationNodeCatalog.definitionFor(TriggerType.WIFI_CONNECTED).family
        )
        assertEquals(
            AutomationNodeFamily.NETWORK,
            AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_HTTP_REQUEST).family
        )
        assertEquals(
            AutomationNodeFamily.ROM,
            AutomationNodeCatalog.definitionFor(ActionType.ROM_CUSTOM_SETTING).family
        )
        assertEquals(
            AutomationNodeFamily.FLOW,
            AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_WAIT).family
        )
    }

    @Test
    fun webhookSchema_marksTokenRequiredAndSensitive() {
        val schema = AutomationNodeCatalog.definitionFor(TriggerType.WEBHOOK).configuration
        val token = schema.field("token")

        assertNotNull(token)
        assertEquals(NodeConfigValueType.SECRET, token?.valueType)
        assertTrue(token?.required == true)
        assertTrue(token?.sensitive == true)
        assertNull(token?.defaultValue)
    }

    @Test
    fun wifiPassword_isSensitiveAndNeverStoredInValidationIssue() {
        val schema = AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_WIFI_CONNECT).configuration
        val password = schema.field("password")
        assertNotNull(password)
        assertEquals(NodeConfigValueType.SECRET, password?.valueType)
        assertTrue(password?.sensitive == true)

        val secret = "do-not-copy-this-secret"
        val issues = NodeConfigurationValidator.validate(
            schema,
            mapOf("ssid" to "", "password" to secret)
        )

        assertTrue(issues.any { it.key == "ssid" && it.code == NodeConfigIssueCode.MISSING_REQUIRED })
        assertFalse(issues.toString().contains(secret))
    }

    @Test
    fun validator_rejectsOutOfRangeAndInvalidEnumLiterals() {
        val battery = AutomationNodeCatalog.definitionFor(TriggerType.BATTERY).configuration
        val issues = NodeConfigurationValidator.validate(
            battery,
            mapOf(
                "direction" to "SIDEWAYS",
                "above" to "250",
                "chargerType" to "ANY"
            )
        )

        assertTrue(issues.any { it.key == "direction" && it.code == NodeConfigIssueCode.INVALID_ENUM })
        assertTrue(issues.any { it.key == "above" && it.code == NodeConfigIssueCode.OUT_OF_RANGE })
    }

    @Test
    fun validator_allowsRuntimeTokensOnlyOnExpressionCapableFields() {
        val http = AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_HTTP_REQUEST).configuration

        val dynamic = NodeConfigurationValidator.validate(
            http,
            mapOf(
                "url" to "https://example.test/%host",
                "method" to "POST",
                "timeoutSeconds" to "%timeout"
            )
        )

        // URL is expression-capable, while timeout currently is deliberately
        // literal-only until retry/timeout policy moves into the node runtime contract.
        assertFalse(dynamic.any { it.key == "url" })
        assertTrue(dynamic.any { it.key == "timeoutSeconds" })
    }

    @Test
    fun definitionLookup_usesStableNamespacedIds() {
        val trigger = AutomationNodeCatalog.definitionFor(TriggerType.TIME)
        val action = AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_WIFI)

        assertEquals("trigger.time", trigger.id)
        assertEquals("action.system_wifi", action.id)
        assertEquals(trigger, AutomationNodeCatalog.definitionFor("trigger.time"))
        assertEquals(action, AutomationNodeCatalog.definitionFor("action.system_wifi"))
        assertNull(AutomationNodeCatalog.definitionFor("action.does_not_exist"))
    }
}
