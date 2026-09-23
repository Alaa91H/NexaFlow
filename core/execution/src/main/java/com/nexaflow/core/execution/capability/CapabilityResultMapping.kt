package com.nexaflow.core.execution.capability

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityStatus

/**
 * Collapses the richer capability lifecycle into the legacy boolean action
 * result without inventing success.
 *
 * Only a completed [CapabilityStatus.SUCCESS] is a successful action. PARTIAL
 * and PENDING_USER_ACTION remain non-successful so history, workflow success
 * and follow-up logic cannot claim that a side effect completed when it did
 * not. Intent-only capabilities must therefore report SUCCESS once the
 * requested handoff itself has been launched.
 */
internal fun CapabilityResult.toSystemControlResult(): SystemControlResult =
    if (status == CapabilityStatus.SUCCESS) {
        SystemControlResult.ok(message)
    } else {
        SystemControlResult.fail(message)
    }
