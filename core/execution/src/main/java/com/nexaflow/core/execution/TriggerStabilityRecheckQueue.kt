package com.nexaflow.core.execution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Schedules at most one stable-for recheck per trigger key, with a hard queue bound. */
class TriggerStabilityRecheckQueue(
    private val scope: CoroutineScope,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val lock = Any()
    private val jobs = LinkedHashMap<String, Job>(16, 0.75f, true)

    init {
        require(capacity > 0)
    }

    fun schedule(key: String, delayMs: Long, action: suspend () -> Unit): Boolean {
        require(key.isNotBlank())
        require(delayMs >= 0L)
        val job = synchronized(lock) {
            jobs[key]?.takeIf { it.isActive }?.let { return true }
            if (jobs.size >= capacity) return false
            lateinit var created: Job
            created = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    delay(delayMs)
                    action()
                } finally {
                    synchronized(lock) { jobs.remove(key, created) }
                }
            }
            jobs[key] = created
            created
        }
        job.start()
        return true
    }

    /** Replaces pending work for a noisy trigger while retaining the queue bound. */
    fun replace(key: String, delayMs: Long, action: suspend () -> Unit): Boolean {
        require(key.isNotBlank())
        require(delayMs >= 0L)
        lateinit var previous: Job
        var hadPrevious = false
        val job = synchronized(lock) {
            jobs.remove(key)?.also {
                previous = it
                hadPrevious = true
            }
            if (jobs.size >= capacity) return false
            lateinit var created: Job
            created = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    delay(delayMs)
                    action()
                } finally {
                    synchronized(lock) { jobs.remove(key, created) }
                }
            }
            jobs[key] = created
            created
        }
        if (hadPrevious) previous.cancel()
        job.start()
        return true
    }

    fun cancel(key: String) {
        synchronized(lock) { jobs.remove(key) }?.cancel()
    }

    fun cancelPrefix(prefix: String) {
        val removed = synchronized(lock) {
            val keys = jobs.keys.filter { it.startsWith(prefix) }
            keys.mapNotNull { key -> jobs.remove(key) }
        }
        removed.forEach(Job::cancel)
    }

    fun cancelExcept(retainedKeys: Set<String>) {
        val removed = synchronized(lock) {
            val keys = jobs.keys.filter { it !in retainedKeys }
            keys.mapNotNull { key -> jobs.remove(key) }
        }
        removed.forEach(Job::cancel)
    }

    internal fun pendingCountForTest(): Int = synchronized(lock) { jobs.size }

    companion object {
        const val DEFAULT_CAPACITY = 4_096
    }
}
