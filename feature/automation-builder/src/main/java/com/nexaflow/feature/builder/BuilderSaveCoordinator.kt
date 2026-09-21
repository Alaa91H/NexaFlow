package com.nexaflow.feature.builder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs the builder's post-save flow only after the ViewModel-owned persistence
 * job has completed.
 *
 * The builder may pop its navigation destination immediately after a successful
 * save. Without this ordering gate, popping clears the destination-scoped
 * ViewModel and can cancel an in-flight Room write, making edits such as trigger
 * removal appear to save and then reappear on the next edit.
 */
internal fun CoroutineScope.launchAfterSave(
    saveJob: Job,
    block: suspend CoroutineScope.() -> Unit
): Job = launch {
    saveJob.join()
    if (saveJob.isCancelled) return@launch
    block()
}
