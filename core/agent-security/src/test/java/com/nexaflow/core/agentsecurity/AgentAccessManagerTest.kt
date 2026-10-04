package com.nexaflow.core.agentsecurity

import com.nexaflow.core.security.SecureStorage
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAccessManagerTest {

    @Test
    fun permanentGrantIsFailClosedUntilGlobalAccessIsEnabled() = runTest {
        val fixture = fixture()

        val result = fixture.manager.grantPermanentAccess(identity())

        assertEquals(AgentGrantResult.Disabled, result)
        assertTrue(fixture.store.read().grants.isEmpty())
    }

    @Test
    fun permanentGrantPersistsOnlyHashedRefreshSecretWithFullScopes() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)

        val result = fixture.manager.grantPermanentAccess(identity())
        val credential = (result as AgentGrantResult.Granted).credential
        val raw = fixture.storage.rawState()
        val state = fixture.store.read()

        assertFalse(raw.contains(credential.refreshToken.substringAfter('.')))
        assertEquals(AgentScope.FULL_ACCESS, state.grants.single().scopes)
        assertEquals("agent.test", state.grants.single().agentId)
        assertEquals(1, state.credentials.size)
    }

    @Test
    fun refreshCredentialSurvivesManagerRecreationAndRotatesOnUse() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val bootstrap = (
            fixture.manager.grantPermanentAccess(identity()) as AgentGrantResult.Granted
            ).credential

        val recreated = fixture.newManager()
        val first = recreated.exchangeRefreshToken(bootstrap.refreshToken)
            as AgentSessionIssueResult.Issued
        val replay = recreated.exchangeRefreshToken(bootstrap.refreshToken)
        val second = recreated.exchangeRefreshToken(first.credential.rotatedRefreshToken)

        assertEquals(AgentSessionIssueResult.Revoked, replay)
        assertEquals(AgentSessionIssueResult.InvalidToken, second)
        assertEquals(0, fixture.manager.status().activeSessionCount)
        assertTrue(fixture.manager.listActiveGrants().isEmpty())
    }

    @Test
    fun olderPersistedCredentialWithoutReplayFieldsDeserializesWithNullDefaults() {
        val stored = """
            {
              "credentialId":"credential-1",
              "agentId":"agent.test",
              "refreshSecretHash":"hash",
              "createdAt":1,
              "lastUsedAt":2,
              "rotatedAt":2,
              "revokedAt":null
            }
        """.trimIndent()

        val decoded = Json.Default
            .decodeFromString(AgentCredentialRecord.serializer(), stored)

        assertEquals(null, decoded.previousRefreshSecretHash)
        assertEquals(null, decoded.previousRotatedAt)
    }

    @Test
    fun unknownGrantModeDeserializesAsFailClosedUnknown() {
        val stored = """
            {"agentId":"agent.test","displayName":"Agent","mode":"FUTURE_MODE","createdAt":1}
        """.trimIndent()
        val decoded = Json.Default.decodeFromString(AgentGrantRecord.serializer(), stored)
        assertEquals(AgentGrantMode.UNKNOWN, decoded.mode)
    }

    @Test
    fun newPairingUsesStandardScopesAndElevatedPermissionIsDenied() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val started = fixture.manager.beginPairing(identity()) as AgentPairingStartResult.Started
        val paired = fixture.manager.completePairing(
            started.offer.challengeId,
            started.offer.challengeSecret
        ) as AgentPairingCompletionResult.Granted
        val session = fixture.manager.exchangeRefreshToken(paired.credential.refreshToken)
            as AgentSessionIssueResult.Issued
        val grant = fixture.manager.listActiveGrants().single()

        assertEquals(AgentGrantMode.STANDARD, grant.mode)
        assertTrue(grant.scopes.contains(AgentScope.TASKS_CREATE))
        assertFalse(grant.scopes.contains(AgentScope.ELEVATED_REQUEST))
        assertEquals(
            AgentAuthorizationResult.ScopeDenied,
            fixture.manager.authorize(session.credential.accessToken, setOf(AgentScope.ELEVATED_REQUEST))
        )
    }

    @Test
    fun everyOperationHasADeclaredScopeAndEveryScopeIsRepresented() {
        assertTrue(AgentOperation.entries.isNotEmpty())
        assertTrue(AgentOperation.entries.all { it.requiredScopes.isNotEmpty() })
        assertEquals(
            AgentScope.entries.toSet(),
            AgentOperation.entries.flatMap { it.requiredScopes }.toSet()
        )
        assertEquals(AgentScope.TASKS_ENABLE, AgentOperation.TASK_DISABLE.requiredScopes.single())
        assertEquals(AgentScope.TASKS_RUN, AgentOperation.TASK_RUN.requiredScopes.single())
    }

    @Test
    fun everyOperationIsDeniedWhenItsRequiredScopeIsAbsent() = runTest {
        AgentOperation.entries.forEachIndexed { index, operation ->
            val fixture = fixture()
            fixture.manager.setAccessEnabled(true)
            val bootstrap = (
                fixture.manager.grantAccess(
                    identity("agent.$index").copy(requestedMode = AgentGrantMode.READ_ONLY)
                ) as AgentGrantResult.Granted
                ).credential
            val session = fixture.manager.exchangeRefreshToken(bootstrap.refreshToken)
                as AgentSessionIssueResult.Issued

            val result = fixture.manager.authorize(
                session.credential.accessToken,
                operation.requiredScopes
            )
            val readOnlyScopes = setOf(AgentScope.TASKS_READ, AgentScope.CATALOG_READ, AgentScope.HISTORY_READ)
            val expected = if (readOnlyScopes.containsAll(operation.requiredScopes)) {
                AgentAuthorizationResult.Authorized(
                    "agent.$index",
                    readOnlyScopes
                )
            } else {
                AgentAuthorizationResult.ScopeDenied
            }
            assertEquals("operation=$operation scopes=${operation.requiredScopes}", expected, result)
        }
    }

    @Test
    fun pairingCompletionCannotUpgradeModeSelectedWhenChallengeWasCreated() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val started = fixture.manager.beginPairing(
            identity().copy(requestedMode = AgentGrantMode.STANDARD)
        ) as AgentPairingStartResult.Started

        val upgraded = fixture.manager.completePairing(
            started.offer.challengeId,
            started.offer.challengeSecret,
            requestedMode = AgentGrantMode.PERMANENT_FULL_ACCESS
        )

        assertEquals(AgentPairingCompletionResult.InvalidChallenge, upgraded)
        assertTrue(fixture.manager.listActiveGrants().isEmpty())
        val completed = fixture.manager.completePairing(
            started.offer.challengeId,
            started.offer.challengeSecret,
            requestedMode = AgentGrantMode.STANDARD
        )
        assertTrue(completed is AgentPairingCompletionResult.Granted)
        assertEquals(AgentGrantMode.STANDARD, fixture.manager.listActiveGrants().single().mode)
    }

    @Test
    fun timedFullAccessExpiresAndCanBeDowngraded() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val grant = (fixture.manager.grantPermanentAccess(identity()) as AgentGrantResult.Granted)
        val session = fixture.manager.exchangeRefreshToken(grant.credential.refreshToken)
            as AgentSessionIssueResult.Issued
        val current = fixture.store.read().grants.single()
        assertTrue(fixture.manager.updateGrantMode(current.agentId, AgentGrantMode.TIMED_FULL_ACCESS))
        val timed = fixture.manager.listActiveGrants().single()
        assertEquals(fixture.now + 60 * 60 * 1000L, timed.expiresAt)
        fixture.now = checkNotNull(timed.expiresAt)
        assertEquals(
            AgentAuthorizationResult.Revoked,
            fixture.manager.authorize(session.credential.accessToken, setOf(AgentScope.TASKS_READ))
        )
        assertTrue(fixture.manager.listActiveGrants().isEmpty())
    }

    @Test
    fun timedFullAccessCanBeDowngradedBeforeExpiry() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        fixture.manager.grantPermanentAccess(identity())
        val current = fixture.store.read().grants.single()
        assertTrue(fixture.manager.updateGrantMode(current.agentId, AgentGrantMode.TIMED_FULL_ACCESS))
        assertTrue(fixture.manager.updateGrantMode(current.agentId, AgentGrantMode.READ_ONLY))
        assertEquals(AgentGrantMode.READ_ONLY, fixture.manager.listActiveGrants().single().mode)
    }

    @Test
    fun replayListenerReceivesOnlyAgentAndTimestamp() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val bootstrap = (fixture.manager.grantPermanentAccess(identity()) as AgentGrantResult.Granted)
            .credential
        val rotated = fixture.manager.exchangeRefreshToken(bootstrap.refreshToken)
            as AgentSessionIssueResult.Issued
        var event: Pair<String, Long>? = null
        val observingManager = AgentAccessManager(
            store = fixture.store,
            clockMillis = { fixture.now },
            refreshReplayListener = { agentId, detectedAt -> event = agentId to detectedAt }
        )

        assertEquals(AgentSessionIssueResult.Revoked, observingManager.exchangeRefreshToken(bootstrap.refreshToken))
        assertEquals("agent.test" to fixture.now, event)
        assertFalse(rotated.credential.rotatedRefreshToken.isBlank())
    }

    @Test
    fun concurrentRefreshReplayRevokesTheCredentialFamily() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val bootstrap = (
            fixture.manager.grantPermanentAccess(identity()) as AgentGrantResult.Granted
            ).credential

        val results = coroutineScope {
            List(2) {
                async { fixture.manager.exchangeRefreshToken(bootstrap.refreshToken) }
            }.awaitAll()
        }

        assertEquals(1, results.count { it is AgentSessionIssueResult.Issued })
        assertEquals(1, results.count { it == AgentSessionIssueResult.Revoked })
        assertTrue(fixture.manager.listActiveGrants().isEmpty())
        assertEquals(0, fixture.manager.status().activeSessionCount)
    }

    @Test
    fun bindingMustMatchForSessionIssueAndAuthorization() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val binding = AgentIdentityBinding(
            packageName = "com.example.agent",
            signingCertificateSha256 = "abc123"
        )
        val bootstrap = (
            fixture.manager.grantPermanentAccess(identity(binding)) as AgentGrantResult.Granted
            ).credential

        val wrong = fixture.manager.exchangeRefreshToken(
            bootstrap.refreshToken,
            AgentIdentityBinding(packageName = "com.other.agent")
        )
        assertEquals(AgentSessionIssueResult.BindingMismatch, wrong)

        val issued = fixture.manager.exchangeRefreshToken(
            bootstrap.refreshToken,
            binding
        ) as AgentSessionIssueResult.Issued
        val authorized = fixture.manager.authorize(
            issued.credential.accessToken,
            setOf(AgentScope.TASKS_CREATE, AgentScope.ELEVATED_REQUEST),
            binding
        )
        val wrongAuthorization = fixture.manager.authorize(
            issued.credential.accessToken,
            setOf(AgentScope.TASKS_READ),
            AgentIdentityBinding(packageName = "com.other.agent")
        )

        assertTrue(authorized is AgentAuthorizationResult.Authorized)
        assertEquals(AgentAuthorizationResult.BindingMismatch, wrongAuthorization)
    }

    @Test
    fun killSwitchInvalidatesSessionsButPreservesPermanentGrantCredential() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val bootstrap = (
            fixture.manager.grantPermanentAccess(identity()) as AgentGrantResult.Granted
            ).credential
        val issued = fixture.manager.exchangeRefreshToken(bootstrap.refreshToken)
            as AgentSessionIssueResult.Issued

        fixture.manager.setAccessEnabled(false)
        assertEquals(
            AgentAuthorizationResult.Disabled,
            fixture.manager.authorize(
                issued.credential.accessToken,
                setOf(AgentScope.TASKS_READ)
            )
        )

        fixture.manager.setAccessEnabled(true)
        assertEquals(
            AgentAuthorizationResult.InvalidToken,
            fixture.manager.authorize(
                issued.credential.accessToken,
                setOf(AgentScope.TASKS_READ)
            )
        )
        assertTrue(
            fixture.manager.exchangeRefreshToken(
                issued.credential.rotatedRefreshToken
            ) is AgentSessionIssueResult.Issued
        )
        assertEquals(1, fixture.manager.status().activeAgentCount)
    }

    @Test
    fun pairingIsOneTimeAndCreatesPermanentAccess() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val started = fixture.manager.beginPairing(identity())
            as AgentPairingStartResult.Started

        val completed = fixture.manager.completePairing(
            started.offer.challengeId,
            started.offer.challengeSecret
        )
        val replay = fixture.manager.completePairing(
            started.offer.challengeId,
            started.offer.challengeSecret
        )

        assertTrue(completed is AgentPairingCompletionResult.Granted)
        assertEquals(AgentPairingCompletionResult.InvalidChallenge, replay)
        assertEquals(1, fixture.manager.listActiveGrants().size)
    }

    @Test
    fun pairingCompletionCanBindPermanentGrantToTransportIdentity() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val started = fixture.manager.beginPairing(identity())
            as AgentPairingStartResult.Started
        val binderIdentity = AgentIdentityBinding(
            packageName = "com.example.agent",
            signingCertificateSha256 = "binder-cert-sha256"
        )

        val completed = fixture.manager.completePairing(
            started.offer.challengeId,
            started.offer.challengeSecret,
            binderIdentity
        ) as AgentPairingCompletionResult.Granted
        val grant = fixture.manager.listActiveGrants().single()

        assertEquals(binderIdentity, grant.binding)
        assertEquals(
            AgentSessionIssueResult.BindingMismatch,
            fixture.manager.exchangeRefreshToken(
                completed.credential.refreshToken,
                AgentIdentityBinding(
                    packageName = "com.example.other",
                    signingCertificateSha256 = "binder-cert-sha256"
                )
            )
        )
        assertTrue(
            fixture.manager.exchangeRefreshToken(
                completed.credential.refreshToken,
                binderIdentity
            ) is AgentSessionIssueResult.Issued
        )
    }

    @Test
    fun pairingLocksAfterBoundedFailures() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val started = fixture.manager.beginPairing(identity())
            as AgentPairingStartResult.Started

        repeat(4) {
            assertEquals(
                AgentPairingCompletionResult.InvalidChallenge,
                fixture.manager.completePairing(started.offer.challengeId, "wrong-$it")
            )
        }
        assertEquals(
            AgentPairingCompletionResult.Locked,
            fixture.manager.completePairing(started.offer.challengeId, "wrong-final")
        )
        assertEquals(
            AgentPairingCompletionResult.InvalidChallenge,
            fixture.manager.completePairing(
                started.offer.challengeId,
                started.offer.challengeSecret
            )
        )
    }

    @Test
    fun expiredPairingAndExpiredSessionFailClosed() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val started = fixture.manager.beginPairing(identity())
            as AgentPairingStartResult.Started
        fixture.now = started.offer.expiresAt

        assertEquals(
            AgentPairingCompletionResult.Expired,
            fixture.manager.completePairing(
                started.offer.challengeId,
                started.offer.challengeSecret
            )
        )

        val bootstrap = (
            fixture.manager.grantPermanentAccess(identity()) as AgentGrantResult.Granted
            ).credential
        val issued = fixture.manager.exchangeRefreshToken(bootstrap.refreshToken)
            as AgentSessionIssueResult.Issued
        fixture.now = issued.credential.expiresAt

        assertEquals(
            AgentAuthorizationResult.Expired,
            fixture.manager.authorize(
                issued.credential.accessToken,
                setOf(AgentScope.TASKS_READ)
            )
        )
    }

    @Test
    fun scopeChecksAreEnforcedEvenThoughDefaultGrantIsFullAccess() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val bootstrap = (
            fixture.manager.grantPermanentAccess(identity()) as AgentGrantResult.Granted
            ).credential
        fixture.store.mutate { state ->
            state.copy(
                grants = state.grants.map {
                    it.copy(scopes = setOf(AgentScope.TASKS_READ))
                }
            ) to Unit
        }
        val issued = fixture.manager.exchangeRefreshToken(bootstrap.refreshToken)
            as AgentSessionIssueResult.Issued

        assertEquals(
            AgentAuthorizationResult.ScopeDenied,
            fixture.manager.authorize(
                issued.credential.accessToken,
                setOf(AgentScope.TASKS_RUN)
            )
        )
        assertTrue(
            fixture.manager.authorize(
                issued.credential.accessToken,
                setOf(AgentScope.TASKS_READ)
            ) is AgentAuthorizationResult.Authorized
        )
    }

    @Test
    fun revokeAgentAndRevokeAllDestroyCredentialsAndSessions() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val first = (
            fixture.manager.grantPermanentAccess(identity()) as AgentGrantResult.Granted
            ).credential
        val firstSession = fixture.manager.exchangeRefreshToken(first.refreshToken)
            as AgentSessionIssueResult.Issued

        assertTrue(fixture.manager.revokeAgent("agent.test"))
        assertFalse(fixture.manager.revokeAgent("agent.test"))
        assertEquals(
            AgentAuthorizationResult.InvalidToken,
            fixture.manager.authorize(
                firstSession.credential.accessToken,
                setOf(AgentScope.TASKS_READ)
            )
        )
        assertEquals(
            AgentSessionIssueResult.InvalidToken,
            fixture.manager.exchangeRefreshToken(firstSession.credential.rotatedRefreshToken)
        )

        fixture.manager.grantPermanentAccess(identity("agent.one"))
        fixture.manager.grantPermanentAccess(identity("agent.two"))
        assertEquals(2, fixture.manager.revokeAllAgents())
        assertEquals(0, fixture.manager.status().activeAgentCount)
    }

    @Test
    fun corruptPersistentStateFailsClosed() = runTest {
        val storage = RecordingSecureStorage()
        storage.put(EncryptedAgentSecurityStore.STORAGE_KEY, "{not-json")
        val store = EncryptedAgentSecurityStore(storage)
        val manager = AgentAccessManager(store)

        val status = manager.status()

        assertFalse(status.accessEnabled)
        assertEquals(0, status.activeAgentCount)
        assertEquals(0, status.activeSessionCount)
    }

    @Test
    fun activeAgentCapacityIsBoundedButExistingGrantCanRotate() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)

        repeat(32) { index ->
            assertTrue(
                fixture.manager.grantPermanentAccess(identity("agent.$index")) is
                    AgentGrantResult.Granted
            )
        }
        assertEquals(
            AgentGrantResult.CapacityExceeded,
            fixture.manager.grantPermanentAccess(identity("agent.overflow"))
        )
        assertTrue(
            fixture.manager.grantPermanentAccess(identity("agent.0")) is
                AgentGrantResult.Granted
        )
        assertEquals(
            32,
            fixture.manager.status().activeAgentCount
        )
    }

    @Test
    fun requestAuthorizerRejectsOversizedPayloadBeforeTokenUse() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val bootstrap = (
            fixture.manager.grantPermanentAccess(identity()) as AgentGrantResult.Granted
            ).credential
        val session = fixture.manager.exchangeRefreshToken(bootstrap.refreshToken)
            as AgentSessionIssueResult.Issued
        val authorizer = AgentRequestAuthorizer(
            accessManager = fixture.manager,
            maxPayloadBytes = 16,
            maxRequestsPerWindow = 10
        )

        assertEquals(
            AgentAuthorizationResult.PayloadTooLarge,
            authorizer.authorize(
                accessToken = session.credential.accessToken,
                operation = AgentOperation.TASK_CREATE,
                payloadBytes = 17
            )
        )
    }

    @Test
    fun requestAuthorizerEnforcesPerAgentWindowWithoutPersistingTokenMaterial() = runTest {
        val fixture = fixture()
        fixture.manager.setAccessEnabled(true)
        val bootstrap = (
            fixture.manager.grantPermanentAccess(identity()) as AgentGrantResult.Granted
            ).credential
        val session = fixture.manager.exchangeRefreshToken(bootstrap.refreshToken)
            as AgentSessionIssueResult.Issued
        var now = 10_000L
        val authorizer = AgentRequestAuthorizer(
            accessManager = fixture.manager,
            clockMillis = { now },
            maxRequestsPerWindow = 2,
            rateWindowMs = 1_000L
        )

        repeat(2) {
            assertTrue(
                authorizer.authorize(
                    accessToken = session.credential.accessToken,
                    operation = AgentOperation.TASK_LIST
                ) is AgentAuthorizationResult.Authorized
            )
        }
        assertEquals(
            AgentAuthorizationResult.RateLimited,
            authorizer.authorize(
                accessToken = session.credential.accessToken,
                operation = AgentOperation.TASK_LIST
            )
        )

        now += 1_000L
        assertTrue(
            authorizer.authorize(
                accessToken = session.credential.accessToken,
                operation = AgentOperation.TASK_LIST
            ) is AgentAuthorizationResult.Authorized
        )
        assertFalse(fixture.storage.rawState().contains(session.credential.accessToken))
    }

    private fun identity(
        binding: AgentIdentityBinding = AgentIdentityBinding()
    ): AgentIdentityRequest = identity("agent.test", binding)

    private fun identity(
        agentId: String,
        binding: AgentIdentityBinding = AgentIdentityBinding()
    ): AgentIdentityRequest = AgentIdentityRequest(
        agentId = agentId,
        displayName = "Test Agent",
        binding = binding
    )

    private fun fixture(): Fixture {
        val storage = RecordingSecureStorage()
        val store = EncryptedAgentSecurityStore(storage)
        return Fixture(storage, store)
    }

    private class Fixture(
        val storage: RecordingSecureStorage,
        val store: EncryptedAgentSecurityStore
    ) {
        var now: Long = 1_000L
        private var idCounter = 0
        private var secretCounter = 0

        val manager: AgentAccessManager = newManager()

        fun newManager(): AgentAccessManager = AgentAccessManager(
            store = store,
            clockMillis = { now },
            idGenerator = { "id-${++idCounter}" },
            secretGenerator = { "secret-${++secretCounter}" },
            sessionDurationMs = 1_000L,
            pairingDurationMs = 500L,
            maxPairingAttempts = 5
        )
    }

    private class RecordingSecureStorage : SecureStorage {
        private val values = linkedMapOf<String, String>()

        override suspend fun get(key: String): String? = values[key]

        override suspend fun put(key: String, value: String) {
            values[key] = value
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }

        override suspend fun clear() {
            values.clear()
        }

        fun rawState(): String = values.values.joinToString(separator = "\n")
    }
}
