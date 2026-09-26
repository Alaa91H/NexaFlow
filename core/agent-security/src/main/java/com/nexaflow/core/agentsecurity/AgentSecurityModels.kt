package com.nexaflow.core.agentsecurity

import kotlinx.serialization.Serializable

@Serializable
enum class AgentScope {
    TASKS_READ,
    TASKS_CREATE,
    TASKS_UPDATE,
    TASKS_DELETE,
    TASKS_ENABLE,
    TASKS_RUN,
    CATALOG_READ,
    HISTORY_READ,
    NETWORK_REQUEST,
    PLUGINS_USE,
    ELEVATED_REQUEST,
    SECRETS_REFERENCE;

    companion object {
        val FULL_ACCESS: Set<AgentScope> = entries.toSet()
    }
}

@Serializable
enum class AgentGrantMode {
    PERMANENT_FULL_ACCESS
}

/**
 * Optional transport identity bound to an agent grant.
 *
 * Android IPC can bind package + signing certificate; bridge/relay transports
 * can bind a public-key fingerprint. Empty bindings remain bearer-token only.
 */
@Serializable
data class AgentIdentityBinding(
    val packageName: String? = null,
    val signingCertificateSha256: String? = null,
    val transportKeyFingerprint: String? = null
) {
    fun matches(presented: AgentIdentityBinding): Boolean =
        (packageName == null || packageName == presented.packageName) &&
            (signingCertificateSha256 == null ||
                signingCertificateSha256 == presented.signingCertificateSha256) &&
            (transportKeyFingerprint == null ||
                transportKeyFingerprint == presented.transportKeyFingerprint)
}

@Serializable
data class AgentIdentityRequest(
    val agentId: String,
    val displayName: String,
    val binding: AgentIdentityBinding = AgentIdentityBinding()
)

@Serializable
data class AgentGrantRecord(
    val agentId: String,
    val displayName: String,
    val mode: AgentGrantMode = AgentGrantMode.PERMANENT_FULL_ACCESS,
    val scopes: Set<AgentScope> = AgentScope.FULL_ACCESS,
    val binding: AgentIdentityBinding = AgentIdentityBinding(),
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    val revokedAt: Long? = null
)

@Serializable
data class AgentCredentialRecord(
    val credentialId: String,
    val agentId: String,
    val refreshSecretHash: String,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    val rotatedAt: Long? = null,
    val revokedAt: Long? = null
)

@Serializable
data class AgentSessionRecord(
    val sessionId: String,
    val agentId: String,
    val accessSecretHash: String,
    val createdAt: Long,
    val expiresAt: Long,
    val lastUsedAt: Long? = null,
    val revokedAt: Long? = null
)

@Serializable
data class AgentPairingChallengeRecord(
    val challengeId: String,
    val request: AgentIdentityRequest,
    val challengeSecretHash: String,
    val createdAt: Long,
    val expiresAt: Long,
    val failedAttempts: Int = 0,
    val consumedAt: Long? = null
)

@Serializable
data class AgentSecurityStateV1(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val accessEnabled: Boolean = false,
    val grants: List<AgentGrantRecord> = emptyList(),
    val credentials: List<AgentCredentialRecord> = emptyList(),
    val sessions: List<AgentSessionRecord> = emptyList(),
    val pairingChallenges: List<AgentPairingChallengeRecord> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class AgentBootstrapCredential(
    val agentId: String,
    val refreshToken: String
) {
    override fun toString(): String =
        "AgentBootstrapCredential(agentId=$agentId, refreshToken=<redacted>)"
}

data class AgentPairingOffer(
    val challengeId: String,
    val challengeSecret: String,
    val expiresAt: Long
) {
    override fun toString(): String =
        "AgentPairingOffer(challengeId=$challengeId, challengeSecret=<redacted>, expiresAt=$expiresAt)"
}

data class AgentSessionCredential(
    val agentId: String,
    val accessToken: String,
    val rotatedRefreshToken: String,
    val expiresAt: Long
) {
    override fun toString(): String =
        "AgentSessionCredential(agentId=$agentId, accessToken=<redacted>, " +
            "rotatedRefreshToken=<redacted>, expiresAt=$expiresAt)"
}

data class AgentSecurityStatus(
    val accessEnabled: Boolean,
    val activeAgentCount: Int,
    val activeSessionCount: Int,
    val pendingPairingCount: Int
)

sealed interface AgentGrantResult {
    data class Granted(
        val credential: AgentBootstrapCredential
    ) : AgentGrantResult

    data object Disabled : AgentGrantResult
    data object CapacityExceeded : AgentGrantResult
}

sealed interface AgentPairingStartResult {
    data class Started(
        val offer: AgentPairingOffer
    ) : AgentPairingStartResult

    data object Disabled : AgentPairingStartResult
}

sealed interface AgentPairingCompletionResult {
    data class Granted(
        val credential: AgentBootstrapCredential
    ) : AgentPairingCompletionResult

    data object Disabled : AgentPairingCompletionResult
    data object InvalidChallenge : AgentPairingCompletionResult
    data object Expired : AgentPairingCompletionResult
    data object Locked : AgentPairingCompletionResult
    data object CapacityExceeded : AgentPairingCompletionResult
}

sealed interface AgentSessionIssueResult {
    data class Issued(
        val credential: AgentSessionCredential
    ) : AgentSessionIssueResult

    data object Disabled : AgentSessionIssueResult
    data object InvalidToken : AgentSessionIssueResult
    data object Revoked : AgentSessionIssueResult
    data object BindingMismatch : AgentSessionIssueResult
}

sealed interface AgentAuthorizationResult {
    data class Authorized(
        val agentId: String,
        val scopes: Set<AgentScope>
    ) : AgentAuthorizationResult

    data object Disabled : AgentAuthorizationResult
    data object InvalidToken : AgentAuthorizationResult
    data object Expired : AgentAuthorizationResult
    data object Revoked : AgentAuthorizationResult
    data object BindingMismatch : AgentAuthorizationResult
    data object ScopeDenied : AgentAuthorizationResult
    data object PayloadTooLarge : AgentAuthorizationResult
    data object RateLimited : AgentAuthorizationResult
}
