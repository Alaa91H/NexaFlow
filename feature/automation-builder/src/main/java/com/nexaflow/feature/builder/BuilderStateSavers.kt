package com.nexaflow.feature.builder

import android.os.Bundle
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.TriggerType

/**
 * Bundle round-trips for the builder drafts: every editable piece of the
 * unsaved task is serialized into Bundle-compatible primitives so
 * `rememberSaveable` survives rotation and process death.
 *
 * Drafts are immutable by contract. Every restore rebuilds fresh instances and
 * every configuration map is copied before becoming visible to Compose.
 */

// ---- string-map <-> Bundle helpers ---------------------------------------

private fun mapToBundle(keys: Collection<String>, values: Collection<String>): Bundle = Bundle().apply {
    putStringArrayList("keys", ArrayList(keys))
    putStringArrayList("values", ArrayList(values))
}

private fun bundleToStringMap(bundle: Bundle): Map<String, String> {
    val keys = bundle.getStringArrayList("keys") ?: return emptyMap()
    val values = bundle.getStringArrayList("values") ?: return emptyMap()
    return keys.zip(values).toMap()
}

private fun <T : Enum<T>> enumFrom(bundle: Bundle, key: String, clazz: Class<T>): T? =
    bundle.getString(key)?.let { name -> runCatching { java.lang.Enum.valueOf(clazz, name) }.getOrNull() }

// ---- fixed catalogue selections --------------------------------------------

val TriggerTypeSelectionSaver: Saver<SnapshotStateList<TriggerType>, ArrayList<String>> = Saver(
    save = { list -> ArrayList(list.map { it.name }) },
    restore = { names ->
        names.mapNotNull { name ->
            runCatching { TriggerType.valueOf(name) }.getOrNull()
        }.toMutableStateList()
    }
)

val ActionTypeSelectionSaver: Saver<SnapshotStateList<ActionType>, ArrayList<String>> = Saver(
    save = { list -> ArrayList(list.map { it.name }) },
    restore = { names ->
        names.mapNotNull { name ->
            runCatching { ActionType.valueOf(name) }.getOrNull()
        }.toMutableStateList()
    }
)

// ---- triggers --------------------------------------------------------------

private fun TriggerDraft.toBundle(): Bundle = Bundle().apply {
    putString("type", type.name)
    putAll(mapToBundle(config.keys, config.values))
}

private fun bundleToTriggerDraft(bundle: Bundle): TriggerDraft = TriggerDraft(
    type = enumFrom(bundle, "type", TriggerType::class.java) ?: TriggerType.TIME,
    config = bundleToStringMap(bundle)
)

val TriggerDraftListSaver: Saver<SnapshotStateList<TriggerDraft>, ArrayList<Bundle>> = Saver(
    save = { list -> ArrayList(list.map { it.toBundle() }) },
    restore = { bundles -> bundles.map { bundleToTriggerDraft(it) }.toMutableStateList() }
)

// ---- constraints ------------------------------------------------------------

private fun ConstraintDraft.toBundle(): Bundle = Bundle().apply {
    putString("type", type.name)
    putAll(mapToBundle(config.keys, config.values))
}

private fun bundleToConstraintDraft(bundle: Bundle): ConstraintDraft = ConstraintDraft(
    type = enumFrom(bundle, "type", ConstraintType::class.java) ?: ConstraintType.WIFI,
    config = bundleToStringMap(bundle)
)

val ConstraintDraftListSaver: Saver<SnapshotStateList<ConstraintDraft>, ArrayList<Bundle>> = Saver(
    save = { list -> ArrayList(list.map { it.toBundle() }) },
    restore = { bundles -> bundles.map { bundleToConstraintDraft(it) }.toMutableStateList() }
)

// ---- executions -------------------------------------------------------------

/** Legacy type list retained only for the optional exit-action editor. */
val ActionOptionListSaver: Saver<SnapshotStateList<ActionOption>, ArrayList<String>> = Saver(
    save = { list -> ArrayList(list.map { it.actionType.name }) },
    restore = { names ->
        val byType = actionOptions.associateBy { it.actionType }
        names.mapNotNull { name ->
            runCatching { ActionType.valueOf(name) }.getOrNull()?.let { byType[it] }
        }.toMutableStateList()
    }
)

private fun ActionDraft.toBundle(): Bundle = Bundle().apply {
    putString("id", id)
    putString("type", option.actionType.name)
    putAll(mapToBundle(config.keys, config.values))
    endBehavior?.let { behavior ->
        putString("endMode", behavior.mode.name)
        putBundle("endConfig", mapToBundle(behavior.config.keys, behavior.config.values))
    }
}

private fun bundleToActionDraft(bundle: Bundle): ActionDraft? {
    val type = enumFrom(bundle, "type", ActionType::class.java) ?: return null
    val option = actionOptions.firstOrNull { it.actionType == type } ?: return null
    val endMode = enumFrom(bundle, "endMode", EndMode::class.java)
    val endBehavior = endMode?.let { EndBehavior(it, bundleToStringMap(bundle.getBundle("endConfig") ?: Bundle())) }
    return ActionDraft(
        id = bundle.getString("id").orEmpty().ifBlank { java.util.UUID.randomUUID().toString() },
        option = option,
        config = bundleToStringMap(bundle),
        endBehavior = endBehavior
    )
}

/**
 * Per-card action drafts, rather than type-keyed maps. This preserves two
 * instances of the same action type with different configs/end behavior.
 */
val ActionDraftListSaver: Saver<SnapshotStateList<ActionDraft>, ArrayList<Bundle>> = Saver(
    save = { list -> ArrayList(list.map { it.toBundle() }) },
    restore = { bundles -> bundles.mapNotNull { bundleToActionDraft(it) }.toMutableStateList() }
)

// Kept for the existing optional exit-action editor, which still intentionally
// exposes one entry per type. The primary execution list above is per-card.
private fun SnapshotStateMap<ActionType, Map<String, String>>.toActionConfigBundles(): ArrayList<Bundle> =
    ArrayList(entries.map { (type, config) ->
        Bundle().apply {
            putString("type", type.name)
            putAll(mapToBundle(config.keys, config.values))
        }
    })

private fun bundlesToActionConfigMap(bundles: ArrayList<Bundle>): SnapshotStateMap<ActionType, Map<String, String>> {
    val map = mutableStateMapOf<ActionType, Map<String, String>>()
    bundles.forEach { bundle ->
        enumFrom(bundle, "type", ActionType::class.java)?.let { type ->
            map[type] = bundleToStringMap(bundle)
        }
    }
    return map
}

val ActionConfigMapSaver: Saver<SnapshotStateMap<ActionType, Map<String, String>>, ArrayList<Bundle>> = Saver(
    save = { it.toActionConfigBundles() },
    restore = { bundles -> bundlesToActionConfigMap(bundles) }
)
