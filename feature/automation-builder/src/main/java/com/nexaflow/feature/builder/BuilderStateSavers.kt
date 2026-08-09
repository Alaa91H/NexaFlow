package com.nexaflow.feature.builder

import android.os.Bundle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.TriggerType

/**
 * Bundle round-trips for the builder drafts (P2-11): every editable piece of
 * the unsaved task is serialized into Bundle-compatible primitives so
 * `rememberSaveable` survives rotation AND process death, instead of `remember`
 * which is lost whenever the activity is recreated.
 *
 * The drafts are frozen/immutable by contract, so each saver rebuilds fresh
 * instances via the factories (TriggerDraft/ConstraintDraft companions), never
 * reusing saved references.
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

// ---- selected actions (rebuilt from the canonical options list) ---------------

val ActionOptionListSaver: Saver<SnapshotStateList<ActionOption>, ArrayList<String>> = Saver(
    save = { list -> ArrayList(list.map { it.actionType.name }) },
    restore = { names ->
        val byType = actionOptions.associateBy { it.actionType }
        names.mapNotNull { name ->
            runCatching { ActionType.valueOf(name) }.getOrNull()?.let { byType[it] }
        }.toMutableStateList()
    }
)

// ---- action configs (type -> config map) -------------------------------------

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

// ---- per-action end behaviors (type -> EndBehavior?) -------------------------

private fun SnapshotStateMap<ActionType, EndBehavior?>.toEndBehaviorBundles(): ArrayList<Bundle> =
    ArrayList(entries.map { (type, behavior) ->
        Bundle().apply {
            putString("type", type.name)
            if (behavior != null) {
                putString("mode", behavior.mode.name)
                putAll(mapToBundle(behavior.config.keys, behavior.config.values))
            }
        }
    })

private fun bundlesToEndBehaviorMap(bundles: ArrayList<Bundle>): SnapshotStateMap<ActionType, EndBehavior?> {
    val map = mutableStateMapOf<ActionType, EndBehavior?>()
    bundles.forEach { bundle ->
        enumFrom(bundle, "type", ActionType::class.java)?.let { type ->
            val mode = enumFrom(bundle, "mode", EndMode::class.java)
            map[type] = if (mode == null) null else EndBehavior(mode, bundleToStringMap(bundle))
        }
    }
    return map
}

val ActionEndBehaviorMapSaver: Saver<SnapshotStateMap<ActionType, EndBehavior?>, ArrayList<Bundle>> = Saver(
    save = { it.toEndBehaviorBundles() },
    restore = { bundles -> bundlesToEndBehaviorMap(bundles) }
)
