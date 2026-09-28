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
                "timeoutMs" to "%timeout"
            )
        )

        // URL accepts runtime expressions; transport policy values are deliberately
        // literal-only so retry/timeout semantics remain bounded and auditable.
        assertFalse(dynamic.any { it.key == "url" })
        assertTrue(dynamic.any { it.key == "timeoutMs" })
    }


    @Test
    fun everyStaticCatalogDefault_isValidAgainstItsOwnSchema() {
        AutomationNodeCatalog.all.forEach { definition ->
            val defaults = definition.configuration.fields
                .mapNotNull { field -> field.defaultValue?.let { field.key to it } }
                .toMap()

            val issues = NodeConfigurationValidator
                .validate(definition.configuration, defaults)
                .filter { it.key in defaults.keys }
            assertTrue(
                "Invalid defaults for ${definition.id}: $issues",
                issues.isEmpty()
            )
        }
    }

    @Test
    fun specializedRuntimeContracts_exposeTheirRealPersistedKeys() {
        val http = AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_HTTP_REQUEST).configuration
        assertTrue(
            http.knownKeys.containsAll(
                setOf(
                    "url", "method", "body", "headers", "allowPrivateNetwork",
                    "timeoutMs", "retryAttempts", "retryBaseDelayMs", "retryCapMs", "outputPath"
                )
            )
        )
        assertFalse("timeoutSeconds" in http.knownKeys)

        val density = AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_DISPLAY_DENSITY).configuration
        assertTrue("dpi" in density.knownKeys)

        val saver = AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD).configuration
        assertTrue("percent" in saver.knownKeys)

        val discoverability =
            AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY).configuration
        assertTrue("timeoutSeconds" in discoverability.knownKeys)

        val tap = AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_INPUT_TAP).configuration
        assertTrue(requireNotNull(tap.field("x")).required)
        assertTrue(requireNotNull(tap.field("y")).required)

        val swipe = AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_INPUT_SWIPE).configuration
        listOf("x1", "y1", "x2", "y2").forEach { key ->
            assertTrue("$key must be required", requireNotNull(swipe.field(key)).required)
        }
        assertEquals("300", requireNotNull(swipe.field("durationMs")).defaultValue)

        assertEquals(
            "GLOBAL",
            AutomationNodeCatalog.definitionFor(ActionType.SYSTEM_SET_SETTING)
                .configuration.field("namespace")?.defaultValue
        )
        assertEquals(
            "SECURE",
            AutomationNodeCatalog.definitionFor(ActionType.ROM_CUSTOM_SETTING)
                .configuration.field("namespace")?.defaultValue
        )

        val sensor = AutomationNodeCatalog.definitionFor(TriggerType.SENSOR).configuration
        assertTrue("upperThreshold" in sensor.knownKeys)
        assertTrue("GYROSCOPE" in requireNotNull(sensor.field("sensor")).allowedValues)

        val oneShotTriggers = mapOf(
            TriggerType.CLIPBOARD_CHANGED to "contains",
            TriggerType.SCREEN_TIMEOUT_CHANGED to "seconds",
            TriggerType.TIMEZONE_CHANGED to "zone",
            TriggerType.NFC_TAG_SCANNED to "contains",
            TriggerType.ALARM_SET_CHANGED to "event"
        )
        oneShotTriggers.forEach { (type, key) ->
            assertTrue(
                "${type.name} must expose $key",
                key in AutomationNodeCatalog.definitionFor(type).configuration.knownKeys
            )
        }
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
