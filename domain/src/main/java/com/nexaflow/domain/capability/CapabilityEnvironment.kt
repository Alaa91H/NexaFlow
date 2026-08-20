package com.nexaflow.domain.capability

import kotlinx.serialization.Serializable

/** Privilege environment shown only in diagnostics; it never grants an operation by itself. */
@Serializable
enum class CapabilityEnvironmentId {
    STANDARD,
    SHIZUKU,
    ROOT,
    MANAGED_DEVICE,
    ADB
}

/**
 * Typed lifecycle of an optional execution environment. The state is more
 * precise than a package-presence boolean, so the UI can describe why a
 * capability is hidden without inferring that another backend is usable.
 */
@Serializable
enum class CapabilityEnvironmentState {
    AVAILABLE,
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    SERVICE_UNAVAILABLE,
    UNAVAILABLE,
    UNSUPPORTED
}

/** Read-only diagnostic result for one privilege environment. */
@Serializable
data class CapabilityEnvironmentReport(
    val environment: CapabilityEnvironmentId,
    val state: CapabilityEnvironmentState,
    val detailCode: String
)
