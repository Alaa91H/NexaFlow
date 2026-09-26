package com.nexaflow.core.agentsecurity

import java.util.UUID

/**
 * Persistent authority boundary for every future REST/MCP/A2A/Binder adapter.
 *
 * Long-lived refresh credentials and short-lived access credentials are opaque
 * bearer tokens. Only SHA-256 hashes are persisted. Refresh tokens rotate on
 * every successful exchange, so replay of an older credential fails closed.
 */
class AgentAccessManager(
    private val store: AgentSecurityStore,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val secretGenerator: () -> String = AgentTokenCodec::newSecret,
    private val sessionDurationMs: Long = DEFAULT_SESSION_DURATION_MS,
    private val pairingDurationMs: Long = DEFAULT_PAIRING_DURATION_MS,
    private val maxPairingAttempts: Int = DEFAULT_MAX_PAIRING_ATTEMPTS
) {

    init {
        require(sessionDurationMs > 0L) { "Session duration must be positive" }
        require(pairingDurationMs > 0L) { "Pairing duration must be positive" }
        require(maxPairingAttempts >= 1) { "Pairing attempts must be at least one" }
    }

    suspend fun setAccessEnabled(enabled: Boolean) {
        val now = clockMillis()
        store.mutate { state ->
            val next = if (enabled) {
                state.copy(accessEnabled = true)
            } else {
                state.copy(
                    accessEnabled = false,
                    sessions = emptyList(),
                    pairingChallenges = state.pairingChallenges.map { challenge ->
                        if (challenge.consumedAt == null) challenge.copy(consumedAt = now)
                        else challenge
                    }
                )
            }
            next to Unit
        }
    }

    suspend fun grantPermanentAccess(
        request: AgentIdentityRequest
    ): AgentGrantResult {
        validateIdentity(request)
        val now = clockMillis()
        return store.mutate { state ->
            if (!state.accessEnabled) {
                return@mutate state to AgentGrantResult.Disabled
            }
            val (next, credential) = applyPermanentGrant(state, request, now)
            next to AgentGrantResult.Granted(credential)
        }
    }

    suspend fun beginPairing(
        request: AgentIdentityRequest
    ): AgentPairingStartResult {
        validateIdentity(request)
        val now = clockMillis()
        return store.mutate { state ->
            if (!state.accessEnabled) {
                return@mutate state to AgentPairingStartResult.Disabled
            }
            val activeChallenges = state.pairingChallenges
                .filter { it.consumedAt == null && it.expiresAt > now }
                .takeLast(MAX_ACTIVE_PAIRING_CHALLENGES - 1)
            val challengeId = nextId()
            val challengeSecret = secretGenerator()
            val challenge = AgentPairingChallengeRecord(
                challengeId = challengeId,
                request = request,
                challengeSecretHash = AgentTokenCodec.hash(challengeSecret),
                createdAt = now,
                expiresAt = now + pairingDurationMs
            )
            val next = state.copy(
                pairingChallenges = activeChallenges + challenge
            )
            next to AgentPairingStartResult.Started(
                AgentPairingOffer(
                    challengeId = challengeId,
                    challengeSecret = challengeSecret,
                    expiresAt = challenge.expiresAt
                )
            )
        }
    }

    suspend fun completePairing(
        challengeId: String,
        challengeSecret: String
    ): AgentPairingCompletionResult {
        if (challengeId.isBlank() || challengeSecret.isBlank()) {
            return AgentPairingCompletionResult.InvalidChallenge
        }
        val now = clockMillis()
        return store.mutate { state ->
            if (!state.accessEnabled) {
                return@mutate state to AgentPairingCompletionResult.Disabled
            }
            val challenge = state.pairingChallenges
                .firstOrNull { it.challengeId == challengeId }
                ?: return@mutate state to AgentPairingCompletionResult.InvalidChallenge
            if (challenge.consumedAt != null) {
                return@mutate state to AgentPairingCompletionResult.InvalidChallenge
            }
            if (challenge.expiresAt <= now) {
                val next = state.replaceChallenge(
                    challenge.copy(consumedAt = now)
                )
                return@mutate next to AgentPairingCompletionResult.Expired
            }
            if (challenge.failedAttempts >= maxPairingAttempts) {
                return@mutate state to AgentPairingCompletionResult.Locked
            }
            if (!AgentTokenCodec.matches(challenge.challengeSecretHash, challengeSecret)) {
                val attempts = challenge.failedAttempts + 1
                val locked = attempts >= maxPairingAttempts
                val next = state.replaceChallenge(
                    challenge.copy(
                        failedAttempts = attempts,
                        consumedAt = if (locked) now else null
                    )
                )
                return@mutate next to if (locked) {
                    AgentPairingCompletionResult.Locked
                } else {
                    AgentPairingCompletionResult.InvalidChallenge
                }
            }

            val consumedState = state.replaceChallenge(
                challenge.copy(consumedAt = now)
            )
            val (next, credential) = applyPermanentGrant(
                consumedState,
                challenge.request,
                now
            )
            next to AgentPairingCompletionResult.Granted(credential)
        }
    }

    suspend fun exchangeRefreshToken(
        refreshToken: String,
        presentedBinding: AgentIdentityBinding = AgentIdentityBinding()
    ): AgentSessionIssueResult {
        val parsed = AgentTokenCodec.parse(refreshToken)
            ?: return AgentSessionIssueResult.InvalidToken
        val now = clockMillis()
        return store.mutate { state ->
            if (!state.accessEnabled) {
                return@mutate state to AgentSessionIssueResult.Disabled
            }
            val credential = state.credentials.firstOrNull {
                it.credentialId == parsed.id && it.revokedAt == null
            } ?: return@mutate state to AgentSessionIssueResult.InvalidToken
            if (!AgentTokenCodec.matches(credential.refreshSecretHash, parsed.secret)) {
                return@mutate state to AgentSessionIssueResult.InvalidToken
            }
            val grant = state.grants.firstOrNull {
                it.agentId == credential.agentId && it.revokedAt == null
            } ?: return@mutate state to AgentSessionIssueResult.Revoked
            if (!grant.binding.matches(presentedBinding)) {
                return@mutate state to AgentSessionIssueResult.BindingMismatch
            }

            val rotatedRefreshSecret = secretGenerator()
            val accessSecret = secretGenerator()
            val sessionId = nextId()
            val session = AgentSessionRecord(
                sessionId = sessionId,
                agentId = grant.agentId,
                accessSecretHash = AgentTokenCodec.hash(accessSecret),
                createdAt = now,
                expiresAt = now + sessionDurationMs
            )
            val refreshedCredential = credential.copy(
                refreshSecretHash = AgentTokenCodec.hash(rotatedRefreshSecret),
                lastUsedAt = now,
                rotatedAt = now
            )
            val nextSessions = boundedSessions(
                state.sessions.filter {
                    it.revokedAt == null && it.expiresAt > now
                },
                session
            )
            val next = state.copy(
                grants = state.grants.map {
                    if (it.agentId == grant.agentId) it.copy(lastUsedAt = now) else it
                },
                credentials = state.credentials.map {
                    if (it.credentialId == credential.credentialId) refreshedCredential else it
                },
                sessions = nextSessions
            )
            next to AgentSessionIssueResult.Issued(
                AgentSessionCredential(
                    agentId = grant.agentId,
                    accessToken = AgentTokenCodec.compose(sessionId, accessSecret),
                    rotatedRefreshToken = AgentTokenCodec.compose(
                        credential.credentialId,
                        rotatedRefreshSecret
                    ),
                    expiresAt = session.expiresAt
                )
            )
        }
    }

    suspend fun authorize(
        accessToken: String,
        requiredScopes: Set<AgentScope>,
        presentedBinding: AgentIdentityBinding = AgentIdentityBinding()
    ): AgentAuthorizationResult {
        val parsed = AgentTokenCodec.parse(accessToken)
            ?: return AgentAuthorizationResult.InvalidToken
        val now = clockMillis()
        return store.mutate { state ->
            if (!state.accessEnabled) {
                return@mutate state to AgentAuthorizationResult.Disabled
            }
            val session = state.sessions.firstOrNull {
                it.sessionId == parsed.id && it.revokedAt == null
            } ?: return@mutate state to AgentAuthorizationResult.InvalidToken
            if (!AgentTokenCodec.matches(session.accessSecretHash, parsed.secret)) {
                return@mutate state to AgentAuthorizationResult.InvalidToken
            }
            if (session.expiresAt <= now) {
                val next = state.copy(
                    sessions = state.sessions.filterNot { it.sessionId == session.sessionId }
                )
                return@mutate next to AgentAuthorizationResult.Expired
            }
            val grant = state.grants.firstOrNull {
                it.agentId == session.agentId && it.revokedAt == null
            } ?: return@mutate state to AgentAuthorizationResult.Revoked
            if (!grant.binding.matches(presentedBinding)) {
                return@mutate state to AgentAuthorizationResult.BindingMismatch
            }
            if (!grant.scopes.containsAll(requiredScopes)) {
                return@mutate state to AgentAuthorizationResult.ScopeDenied
            }

            val next = state.copy(
                grants = state.grants.map {
                    if (it.agentId == grant.agentId) it.copy(lastUsedAt = now) else it
                },
                sessions = state.sessions.map {
                    if (it.sessionId == session.sessionId) it.copy(lastUsedAt = now) else it
                }
            )
            next to AgentAuthorizationResult.Authorized(
                agentId = grant.agentId,
                scopes = grant.scopes
            )
        }
    }

    suspend fun revokeAgent(agentId: String): Boolean {
        if (agentId.isBlank()) return false
        val now = clockMillis()
        return store.mutate { state ->
            val found = state.grants.any { it.agentId == agentId && it.revokedAt == null }
            if (!found) return@mutate state to false
            val next = state.copy(
                grants = state.grants.map {
                    if (it.agentId == agentId && it.revokedAt == null) it.copy(revokedAt = now)
                    else it
                },
                credentials = state.credentials.filterNot { it.agentId == agentId },
                sessions = state.sessions.filterNot { it.agentId == agentId },
                pairingChallenges = state.pairingChallenges.filterNot {
                    it.request.agentId == agentId
                }
            )
            next to true
        }
    }

    suspend fun revokeAllAgents(): Int {
        val now = clockMillis()
        return store.mutate { state ->
            val activeCount = state.grants.count { it.revokedAt == null }
            val next = state.copy(
                grants = state.grants.map {
                    if (it.revokedAt == null) it.copy(revokedAt = now) else it
                },
                credentials = emptyList(),
                sessions = emptyList(),
                pairingChallenges = emptyList()
            )
            next to activeCount
        }
    }

    suspend fun listActiveGrants(): List<AgentGrantRecord> =
        store.read().grants.filter { it.revokedAt == null }

    suspend fun status(): AgentSecurityStatus {
        val state = store.read()
        val now = clockMillis()
        val activeAgents = state.grants.filter { it.revokedAt == null }
        val activeAgentIds = activeAgents.mapTo(hashSetOf()) { it.agentId }
        return AgentSecurityStatus(
            accessEnabled = state.accessEnabled,
            activeAgentCount = activeAgents.size,
            activeSessionCount = state.sessions.count {
                it.revokedAt == null &&
                    it.expiresAt > now &&
                    it.agentId in activeAgentIds
            },
            pendingPairingCount = state.pairingChallenges.count {
                it.consumedAt == null && it.expiresAt > now
            }
        )
    }

    private fun applyPermanentGrant(
        state: AgentSecurityStateV1,
        request: AgentIdentityRequest,
        now: Long
    ): Pair<AgentSecurityStateV1, AgentBootstrapCredential> {
        val refreshSecret = secretGenerator()
        val credentialId = nextId()
        val grant = AgentGrantRecord(
            agentId = request.agentId,
            displayName = request.displayName,
            binding = request.binding,
            createdAt = now
        )
        val credential = AgentCredentialRecord(
            credentialId = credentialId,
            agentId = request.agentId,
            refreshSecretHash = AgentTokenCodec.hash(refreshSecret),
            createdAt = now
        )
        val next = state.copy(
            grants = state.grants.filterNot {
                it.agentId == request.agentId && it.revokedAt == null
            } + grant,
            credentials = state.credentials.filterNot {
                it.agentId == request.agentId
            } + credential,
            sessions = state.sessions.filterNot {
                it.agentId == request.agentId
            }
        )
        return next to AgentBootstrapCredential(
            agentId = request.agentId,
            refreshToken = AgentTokenCodec.compose(credentialId, refreshSecret)
        )
    }

    private fun boundedSessions(
        current: List<AgentSessionRecord>,
        newSession: AgentSessionRecord
    ): List<AgentSessionRecord> {
        val otherAgents = current.filterNot { it.agentId == newSession.agentId }
        val sameAgent = current
            .filter { it.agentId == newSession.agentId }
            .sortedByDescending { it.createdAt }
            .take(MAX_ACTIVE_SESSIONS_PER_AGENT - 1)
        return otherAgents + sameAgent + newSession
    }

    private fun AgentSecurityStateV1.replaceChallenge(
        replacement: AgentPairingChallengeRecord
    ): AgentSecurityStateV1 = copy(
        pairingChallenges = pairingChallenges.map {
            if (it.challengeId == replacement.challengeId) replacement else it
        }
    )

    private fun validateIdentity(request: AgentIdentityRequest) {
        require(request.agentId.matches(AGENT_ID_PATTERN)) {
            "Agent id has an invalid format"
        }
        require(request.displayName.isNotBlank() && request.displayName.length <= MAX_DISPLAY_NAME_LENGTH) {
            "Agent display name has an invalid length"
        }
        validateBindingValue(request.binding.packageName, "packageName")
        validateBindingValue(
            request.binding.signingCertificateSha256,
            "signingCertificateSha256"
        )
        validateBindingValue(
            request.binding.transportKeyFingerprint,
            "transportKeyFingerprint"
        )
    }

    private fun validateBindingValue(value: String?, label: String) {
        require(value == null || value.length <= MAX_BINDING_VALUE_LENGTH) {
            "Agent binding $label is too long"
        }
    }

    private fun nextId(): String = idGenerator().also {
        require(it.isNotBlank()) { "Generated agent identifier must not be blank" }
    }

    private companion object {
        const val DEFAULT_SESSION_DURATION_MS = 15 * 60 * 1000L
        const val DEFAULT_PAIRING_DURATION_MS = 5 * 60 * 1000L
        const val DEFAULT_MAX_PAIRING_ATTEMPTS = 5
        const val MAX_ACTIVE_SESSIONS_PER_AGENT = 8
        const val MAX_ACTIVE_PAIRING_CHALLENGES = 16
        const val MAX_DISPLAY_NAME_LENGTH = 128
        const val MAX_BINDING_VALUE_LENGTH = 256
        val AGENT_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
    }
}
