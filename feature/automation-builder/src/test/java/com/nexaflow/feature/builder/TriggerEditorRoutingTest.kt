package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins which trigger types the builder renders from the canonical catalog
 * schema and which still fall back to the hand-written legacy sections.
 *
 * [TriggerEditorCard] renders the legacy `when` block only when
 * [CanonicalBuilderSchemaBridge.editingBindingForTrigger] returns null. 37 of
 * the 57 trigger kinds are already schema-driven, so their hand-written arms in
 * the card can never execute (recorded as NFA-19 in the remediation plan).
 * Keeping the split explicit means:
 *
 *  - deleting a legacy arm is provably behaviour-neutral while its type stays
 *    in [schemaRendered];
 *  - moving a type to the schema renderer cannot silently drop its editor,
 *    because this list has to be updated in the same change;
 *  - the remaining legacy surface is measured, not assumed.
 */
class TriggerEditorRoutingTest {

    private val triggerTypes = TriggerType.entries.toList()

    private fun schemaRendered(): List<TriggerType> =
        triggerTypes.filter { CanonicalBuilderSchemaBridge.editingBindingForTrigger(it) != null }

    private fun legacyRendered(): List<TriggerType> =
        triggerTypes.filter { CanonicalBuilderSchemaBridge.editingBindingForTrigger(it) == null }

    @Test
    fun everyTriggerTypeHasExactlyOneRenderer() {
        val schema = schemaRendered()
        val legacy = legacyRendered()

        assertEquals(
            "Schema and legacy routing must partition the catalog",
            triggerTypes.toSet(),
            (schema + legacy).toSet()
        )
        assertEquals(
            "No trigger type may be rendered twice",
            0,
            schema.intersect(legacy.toSet()).size
        )
    }

    @Test
    fun schemaRenderedTriggerTypesStayStable() {
        assertEquals(
            listOf(
                TriggerType.DEVICE,
                TriggerType.CONNECTIVITY,
                TriggerType.WIFI_CONNECTED,
                TriggerType.MOBILE_DATA_CONNECTED,
                TriggerType.HOTSPOT,
                TriggerType.SMS,
                TriggerType.BLUETOOTH_DEVICE,
                TriggerType.RINGER_MODE,
                TriggerType.NETWORK_MODE,
                TriggerType.CALENDAR,
                TriggerType.HEADPHONE,
                TriggerType.AIRPLANE_MODE,
                TriggerType.DARK_MODE,
                TriggerType.CALL_STATE,
                TriggerType.INCOMING_CALL,
                TriggerType.MEDIA_PLAYING,
                TriggerType.POWER_SAVER,
                TriggerType.BLUETOOTH_STATE,
                TriggerType.AUTO_ROTATE,
                TriggerType.DATA_SAVER_STATE,
                TriggerType.DEVICE_LOCKED,
                TriggerType.WIFI_STATE,
                TriggerType.NFC_STATE,
                TriggerType.LOCATION_STATE,
                TriggerType.SCREEN_ROTATION_STATE,
                TriggerType.USB_CONNECTED,
                TriggerType.HDMI_CONNECTED,
                TriggerType.ETHERNET_CONNECTED,
                TriggerType.VPN_CONNECTED,
                TriggerType.DND_STATE,
                TriggerType.STAY_AWAKE_STATE,
                TriggerType.AUTO_BRIGHTNESS_STATE,
                TriggerType.DATA_ROAMING_STATE,
                TriggerType.TIMEZONE_CHANGED,
                TriggerType.BOOT_COMPLETED,
                TriggerType.ALARM_SET_CHANGED,
            ),
            schemaRendered()
        )
    }

    /**
     * The card must not keep a hand-written arm for a type the schema editor
     * already owns (NFA-19): those arms cannot execute and only enlarge the
     * file. Reading the source here is deliberate — a compilable arm proves
     * nothing about reachability.
     */
    @Test
    fun cardHasNoArmForSchemaRenderedTypes() {
        val source = File("src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt").readText()
        val whenBlock = source.substringAfter("when (draft.type) {").substringBefore("else -> Unit")
        val arms = Regex("^ {16}TriggerType\\.([A-Z0-9_]+)", RegexOption.MULTILINE)
            .findAll(whenBlock)
            .map { it.groupValues[1] }
            .toSet()

        val schemaOwned = schemaRendered().map { it.name }.toSet()
        val overlap = arms intersect schemaOwned

        assertTrue("Card lost its legacy arms entirely", arms.isNotEmpty())
        assertEquals(
            "Arms for schema-rendered triggers cannot execute; delete them (NFA-19)",
            emptySet<String>(),
            overlap
        )
    }

    /** The same reachability contract for action editors (NFA-20). */
    @Test
    fun cardHasNoActionArmForSchemaRenderedActions() {
        val source = File("src/main/java/com/nexaflow/feature/builder/ActionConfigEditor.kt").readText()
        val whenBlock = source.substringAfter("when (option.actionType) {").substringBefore("else -> Unit")
        val arms = Regex("^ {8}ActionType\\.([A-Z0-9_]+)", RegexOption.MULTILINE)
            .findAll(whenBlock)
            .map { it.groupValues[1] }
            .toSet()

        val schemaOwned = ActionType.entries
            .filter { CanonicalBuilderSchemaBridge.editingBindingForAction(it) != null }
            .map { it.name }
            .toSet()

        assertTrue("Action editor lost its legacy arms entirely", arms.isNotEmpty())
        assertEquals(
            "Arms for schema-rendered actions cannot execute; delete them (NFA-20)",
            emptySet<String>(),
            arms intersect schemaOwned
        )
    }

    @Test
    fun legacyRenderedTriggerTypesStayStable() {
        assertEquals(
            listOf(
                TriggerType.TIME,
                TriggerType.BATTERY,
                TriggerType.APPLICATION,
                TriggerType.LOCATION,
                TriggerType.NOTIFICATION,
                TriggerType.SENSOR,
                TriggerType.WEBHOOK,
                TriggerType.ROM_SETTING,
                // Charger keeps a dedicated editor for its charge-source filter.
                TriggerType.CHARGER,
                TriggerType.APP_INSTALLED,
                TriggerType.VOLUME_CHANGED,
                TriggerType.BRIGHTNESS_LEVEL,
                TriggerType.STORAGE_LOW,
                TriggerType.WIFI_SIGNAL_STRENGTH,
                TriggerType.CELL_SIGNAL_STRENGTH,
                TriggerType.BATTERY_TEMPERATURE,
                TriggerType.CLIPBOARD_CHANGED,
                TriggerType.SCREEN_TIMEOUT_CHANGED,
                TriggerType.NFC_TAG_SCANNED,
                TriggerType.WEAR_EVENT,
                TriggerType.PLUGIN_EVENT,
            ),
            legacyRendered()
        )
    }
}
