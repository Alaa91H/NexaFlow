package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType

/**
 * Presentation tier for an automation option. Tiers affect discoverability only:
 * they never change whether an option can be persisted or executed.
 */
internal enum class OptionTier {
    /** Everyday choices shown before the full catalog. */
    COMMON,

    /** Normal choices shown from category browsing or local search. */
    BROWSE,

    /** Expert choices that stay reachable but do not crowd the first decision. */
    ADVANCED
}

/**
 * One source of truth for progressive disclosure in the builder.
 *
 * Compatibility is deliberately evaluated outside this object by [CompatibilityGate].
 * This keeps device capability separate from the product decision about prominence.
 */
internal object AutomationOptionCatalog {
    private val commonTriggerOrder = listOf(
        TriggerType.TIME,
        TriggerType.BATTERY,
        TriggerType.CONNECTIVITY,
        TriggerType.BLUETOOTH_DEVICE,
        TriggerType.APPLICATION,
        TriggerType.LOCATION,
        TriggerType.CHARGER
    )

    private val advancedTriggers = setOf(
        TriggerType.WEBHOOK,
        TriggerType.ROM_SETTING,
        TriggerType.SENSOR,
        TriggerType.WIFI_SIGNAL_STRENGTH,
        TriggerType.CELL_SIGNAL_STRENGTH,
        TriggerType.BATTERY_TEMPERATURE,
        TriggerType.CLIPBOARD_CHANGED,
        TriggerType.NFC_TAG_SCANNED,
        TriggerType.ALARM_SET_CHANGED
    )

    private val commonActionOrder = listOf(
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_VOLUME,
        ActionType.SYSTEM_BRIGHTNESS,
        ActionType.SYSTEM_OPEN_APP,
        ActionType.SYSTEM_POWER_SAVER
    )

    private val advancedActions = setOf(
        ActionType.SYSTEM_HTTP_REQUEST,
        ActionType.SYSTEM_INPUT_TEXT,
        ActionType.SYSTEM_KEY_EVENT,
        ActionType.SYSTEM_INPUT_TAP,
        ActionType.SYSTEM_INPUT_SWIPE,
        ActionType.SYSTEM_SET_SETTING,
        ActionType.SYSTEM_INSTALL_APK,
        ActionType.SYSTEM_REBOOT,
        ActionType.SYSTEM_SHUTDOWN,
        ActionType.SYSTEM_RESTART_SYSTEM_UI,
        ActionType.ADVANCED_SHIZUKU,
        ActionType.ADVANCED_ROOT,
        ActionType.PLUGIN_FIRE
    )

    fun triggerTier(type: TriggerType): OptionTier = when {
        type in commonTriggerOrder -> OptionTier.COMMON
        type in advancedTriggers -> OptionTier.ADVANCED
        else -> OptionTier.BROWSE
    }

    fun actionTier(type: ActionType): OptionTier = when {
        type in commonActionOrder -> OptionTier.COMMON
        type in advancedActions -> OptionTier.ADVANCED
        else -> OptionTier.BROWSE
    }

    /**
     * Returns only options the compatibility gate already approved, in a stable
     * recommendation order. Unsupported options are never promoted to the
     * first screen and are never silently added to a routine.
     */
    fun commonTriggers(supported: Collection<TriggerType>): List<TriggerType> {
        val supportedSet = supported.toSet()
        return commonTriggerOrder.filter(supportedSet::contains)
    }

    /** Same progressive-disclosure contract as [commonTriggers] for actions. */
    fun commonActions(supported: Collection<ActionOption>): List<ActionOption> {
        val byType = supported.associateBy(ActionOption::actionType)
        return commonActionOrder.mapNotNull(byType::get)
    }

    /**
     * The full supported catalog remains reachable through browse/search.
     * Ordering stays owned by the caller's existing category order.
     */
    fun browseTriggers(supported: Collection<TriggerType>): List<TriggerType> =
        supported.filter { triggerTier(it) != OptionTier.COMMON }

    /** The full supported action catalog remains reachable through browse/search. */
    fun browseActions(supported: Collection<ActionOption>): List<ActionOption> =
        supported.filter { actionTier(it.actionType) != OptionTier.COMMON }
}
