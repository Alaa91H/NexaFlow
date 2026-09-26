package com.nexaflow.core.agentsecurity

import com.nexaflow.core.security.SecureStorage
import kotlinx.coroutines.test.runTest
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

        assertEquals(AgentSessionIssueResult.InvalidToken, replay)
        assertTrue(second is AgentSessionIssueResult.Issued)
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
