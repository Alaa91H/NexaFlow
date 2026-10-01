package com.nexaflow.feature.builder

import androidx.compose.runtime.snapshots.SnapshotStateList

/** Pure-ish collection operations used by the builder UI coordinator. */
internal object BuilderDraftOperations {
    fun <T> move(items: SnapshotStateList<T>, from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        val item = items.removeAt(from)
        items.add(to, item)
        return true
    }

    fun movedExpandedIndex(expanded: Int?, from: Int, to: Int): Int? = when {
        expanded == null -> null
        expanded == from -> to
        from < expanded && to >= expanded -> expanded - 1
        from > expanded && to <= expanded -> expanded + 1
        else -> expanded
    }
}
