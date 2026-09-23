package com.nexaflow.core.execution.capability

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityStatus

/**
 * Collapses the richer capability lifecycle into the legacy boolean action
 * result without inventing success, while retaining non-sensitive provenance
 * for execution diagnostics/history.
 */
internal fun CapabilityResult.toSystemControlResult(): SystemControlResult =
    SystemControlResult(
        success = status == CapabilityStatus.SUCCESS,
        message = message,
        executionChannel = backend?.name,
        errorCode = errorCode?.name,
        verificationAttempted = verification?.attempted == true,
        verified = verification?.takeIf { it.attempted }?.verified
    )
