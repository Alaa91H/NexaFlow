package com.nexaflow.core.engine

import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.domain.models.Automation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared lifecycle boundary for condition-ending executions.
 * Exit workflows are queued independently of normal-run cooldowns and are
 * serialized so a burst of monitor callbacks cannot lose an end event.
 */
@Singleton
class ExitExecutionCoordinator @Inject constructor(
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val mutex = Mutex()

    /** Enqueue an end workflow without applying the normal run cooldown. */
    fun submit(source: String, automation: Automation, clearActive: Boolean = true) {
        scope.launch {
            mutex.withLock {
                if (clearActive) activeStore.clearAutomation(source, automation.id)
                try {
                    executionEngine.runExit(automation)
                } catch (_: Throwable) {
                    // An individual exit failure must not cancel the queue.
                }
            }
        }
    }
}
