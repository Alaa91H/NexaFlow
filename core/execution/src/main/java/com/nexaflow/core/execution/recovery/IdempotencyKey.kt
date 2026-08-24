package com.nexaflow.core.execution.recovery

/**
 * Ensures exactly-once execution semantics for critical workflow nodes.
 * 
 * An idempotency key is deterministically generated based on the workflow run ID,
 * the specific node ID, and the inputs to that node. If the execution engine crashes
 * or is killed during a run, recovery will check this key against the journal.
 * If the key exists and the status was SUCCESS, the node is skipped during replay.
 */
data class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) { "IdempotencyKey cannot be blank" }
        require(value.length <= 256) { "IdempotencyKey must be <= 256 characters" }
    }

    companion object {
        /**
         * Generates a deterministic key for a specific node execution attempt.
         */
        fun generate(runId: String, nodeId: String, iteration: Int = 0): IdempotencyKey {
            return IdempotencyKey("$runId:$nodeId:$iteration")
        }
    }
}
