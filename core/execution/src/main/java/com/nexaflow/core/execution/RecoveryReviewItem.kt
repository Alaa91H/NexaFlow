package com.nexaflow.core.execution

import com.nexaflow.core.execution.recovery.RecoveryDisposition

/**
 * Read-only user-review projection of one durable recovery checkpoint.
 *
 * The UI never receives the mutable checkpoint object itself. These fields are
 * evidence only: reading them cannot claim, retry, compensate, or clear work.
 */
data class RecoveryReviewItem(
    val runId: String,
    val startedAt: Long,
    val updatedAt: Long,
    val sourceStatus: String,
    val disposition: RecoveryDisposition,
    val dispositionReason: String,
    val nodeId: String?,
    val nodeState: String?,
    val backend: String?,
    val verificationState: String?,
    val failureCode: String?,
    val message: String?
)
