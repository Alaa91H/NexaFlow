package com.nexaflow.core.engine

import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.domain.models.Automation
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow

/**
 * O(1) trigger index: maps a [TriggerSource.sourceId] to the ids of the
 * automations subscribed to that source, so an incoming event reaches only
 * its subscribers instead of a linear `filter { it.enabled }` scan over every
 * automation (the pattern each monitor used before).
 *
 * The index is a pure projection of the database flow: [start] collects
 * [automationsFlow] and rebuilds the index on every emission (including every
 * save/delete/enable toggle, since the repository's flow is Room-backed and
 * re-emits on change). Reads are lock-free snapshots via [bySource] and
 * [snapshot].
 *
 * Thread-safety: writes happen only inside the single collector coroutine;
 * reads may happen concurrently from monitor coroutines, so both maps are
 * concurrent and each rebuild swaps them atomically via [volatileRebuild].
 */
class TriggerIndex(
    private val automationsFlow: Flow<List<Automation>>,
) {
    /** sourceId → automation ids (subscribers). */
    private val index = ConcurrentHashMap<String, MutableSet<String>>()

    /** automation id → snapshot (latest enabled row seen). */
    private val all = ConcurrentHashMap<String, Automation>()

    /** Number of index rebuilds; exposed for tests and diagnostics. */
    @Volatile
    var version: Long = 0
        private set

    /**
     * Collects the database flow and rebuilds the index on every emission.
     * Suspends forever; cancel the collecting job to stop.
     */
    suspend fun start() {
        automationsFlow.collect { rebuild(it) }
    }

    /**
     * Atomically swaps the index for a fresh one built from [list]. Called on
     * every flow emission; never mutates the live maps in place so concurrent
     * readers always see a consistent snapshot.
     */
    private fun rebuild(list: List<Automation>) {
        val nextIndex = HashMap<String, MutableSet<String>>()
        val nextAll = HashMap<String, Automation>()
        for (automation in list) {
            if (!automation.enabled) continue
            nextAll[automation.id] = automation
            for (trigger in automation.triggers) {
                val sourceId = TriggerSource.forTrigger(trigger.type).sourceId
                nextIndex.computeIfAbsent(sourceId) { LinkedHashSet() }.add(automation.id)
            }
        }
        index.clear()
        index.putAll(nextIndex)
        all.clear()
        all.putAll(nextAll)
        version++
    }

    /**
     * O(1): returns the automations subscribed to the given source id, in
     * database emission order. Empty for an unknown source.
     */
    fun bySource(sourceId: String): List<Automation> =
        index[sourceId]?.mapNotNull { all[it] } ?: emptyList()

    /** Returns the latest snapshot of the automation with [id], or null. */
    fun snapshot(id: String): Automation? = all[id]

    /** True when no automation is currently indexed. */
    fun isEmpty(): Boolean = all.isEmpty()
}
