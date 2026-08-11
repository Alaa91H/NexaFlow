package com.nexaflow.feature.widgets

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Persists which task each quick-settings tile slot controls. An empty value
 * means "automatic" (the slot resolves the first/Nth enabled task).
 */
object TileBindingStore {

    private const val PREFS = "nexaflow_tile_bindings"
    private const val PREFIX = "tile_slot_"

    /** Number of quick-settings tile slots the app offers. */
    const val MAX_SLOTS = 8

    fun bindingFor(context: Context, slot: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREFIX + slot, null)
            ?.takeIf { it.isNotEmpty() }

    fun setBinding(context: Context, slot: Int, automationId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(PREFIX + slot, automationId.orEmpty())
        }
    }

    /** Snapshot of every slot's binding (slot number → automation id or null). */
    fun allBindings(context: Context): Map<Int, String?> =
        (1..MAX_SLOTS).associateWith { slot -> bindingFor(context, slot) }

    /**
     * Emits the full binding map whenever a binding changes, so the UI can
     * recompose instantly when the user pins a task to a tile.
     */
    fun bindingsFlow(context: Context): Flow<Map<Int, String?>> = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        trySend(allBindings(context))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(allBindings(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
