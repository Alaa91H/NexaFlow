package com.nexaflow.feature.widgets

import android.content.Context
import androidx.core.content.edit

/**
 * Persists which task each quick-settings tile slot controls. An empty value
 * means "automatic" (the slot resolves the first/Nth enabled task).
 */
object TileBindingStore {

    private const val PREFS = "nexaflow_tile_bindings"
    private const val PREFIX = "tile_slot_"

    fun bindingFor(context: Context, slot: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREFIX + slot, null)
            ?.takeIf { it.isNotEmpty() }

    fun setBinding(context: Context, slot: Int, automationId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(PREFIX + slot, automationId.orEmpty())
        }
    }
}
