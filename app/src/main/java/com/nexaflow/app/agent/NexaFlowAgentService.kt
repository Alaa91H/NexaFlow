package com.nexaflow.app.agent

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.nexaflow.core.agentapi.AgentApiController
import com.nexaflow.core.agentapi.AgentHttpRequest
import com.nexaflow.core.agentapi.AgentHttpResponse
import com.nexaflow.core.agentsecurity.AgentAccessManager
import com.nexaflow.core.agentsecurity.AgentPairingCompletionResult
import com.nexaflow.core.agentsecurity.AgentSessionIssueResult
import dagger.hilt.android.AndroidEntryPoint
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

@AndroidEntryPoint
class NexaFlowAgentService : Service() {

    @Inject
    lateinit var accessManager: AgentAccessManager

    @Inject
    lateinit var controller: AgentApiController

    @Inject
    lateinit var callerIdentityResolver: AndroidAgentCallerIdentityResolver

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private val binder = object : INexaFlowAgentService.Stub() {

        override fun completePairing(
            packageName: String?,
            challengeId: String?,
            challengeSecret: String?,
            callback: INexaFlowAgentCallback?
        ) {
            val callingUid = Binder.getCallingUid()
            serviceScope.launch {
                val binding = resolveCaller(callingUid, packageName)
                    ?: return@launch callback.send(
                        errorEnvelope(401, "caller_identity_invalid")
                    )
                val id = challengeId?.takeIf { it.length in 1..MAX_SECRET_LENGTH }
                    ?: return@launch callback.send(
                        errorEnvelope(400, "invalid_pairing_challenge")
                    )
                val secret = challengeSecret
                    ?.takeIf { it.length in 1..MAX_SECRET_LENGTH }
                    ?: return@launch callback.send(
                        errorEnvelope(400, "invalid_pairing_challenge")
                    )

                val result = accessManager.completePairing(
                    challengeId = id,
                    challengeSecret = secret,
                    presentedBinding = binding
                )
                callback.send(
                    when (result) {
                        is AgentPairingCompletionResult.Granted -> envelope(
                            200,
                            JSONObject()
                                .put("agentId", result.credential.agentId)
                                .put(
                                    "refreshToken",
                                    result.credential.refreshToken
                                )
                        )
                        AgentPairingCompletionResult.Disabled ->
                            errorEnvelope(403, "agent_access_disabled")
                        AgentPairingCompletionResult.InvalidChallenge ->
                            errorEnvelope(400, "invalid_pairing_challenge")
                        AgentPairingCompletionResult.Expired ->
                            errorEnvelope(410, "pairing_expired")
                        AgentPairingCompletionResult.Locked ->
                            errorEnvelope(423, "pairing_locked")
                        AgentPairingCompletionResult.CapacityExceeded ->
                            errorEnvelope(409, "agent_capacity_exceeded")
                    }
                )
            }
        }

        override fun exchangeSession(
            packageName: String?,
            refreshToken: String?,
            callback: INexaFlowAgentCallback?
        ) {
            val callingUid = Binder.getCallingUid()
            serviceScope.launch {
                val binding = resolveCaller(callingUid, packageName)
                    ?: return@launch callback.send(
                        errorEnvelope(401, "caller_identity_invalid")
                    )
                val token = refreshToken
                    ?.takeIf { it.length in 1..MAX_TOKEN_LENGTH }
                    ?: return@launch callback.send(
                        errorEnvelope(400, "invalid_refresh_token")
                    )

                val result = accessManager.exchangeRefreshToken(
                    refreshToken = token,
                    presentedBinding = binding
                )
                callback.send(
                    when (result) {
                        is AgentSessionIssueResult.Issued -> envelope(
                            200,
                            JSONObject()
                                .put("agentId", result.credential.agentId)
                                .put(
                                    "accessToken",
                                    result.credential.accessToken
                                )
                                .put(
                                    "rotatedRefreshToken",
                                    result.credential.rotatedRefreshToken
                                )
                                .put("expiresAt", result.credential.expiresAt)
                        )
                        AgentSessionIssueResult.Disabled ->
                            errorEnvelope(403, "agent_access_disabled")
                        AgentSessionIssueResult.InvalidToken ->
                            errorEnvelope(401, "invalid_refresh_token")
                        AgentSessionIssueResult.Revoked ->
                            errorEnvelope(401, "agent_revoked")
                        AgentSessionIssueResult.BindingMismatch ->
                            errorEnvelope(401, "binding_mismatch")
                    }
                )
            }
        }

        override fun request(
            packageName: String?,
            accessToken: String?,
            method: String?,
            target: String?,
            bodyJson: String?,
            idempotencyKey: String?,
            ifMatch: String?,
            requestId: String?,
            callback: INexaFlowAgentCallback?
        ) {
            val callingUid = Binder.getCallingUid()
            serviceScope.launch {
                val binding = resolveCaller(callingUid, packageName)
                    ?: return@launch callback.send(
                        errorEnvelope(401, "caller_identity_invalid")
                    )
                val token = accessToken
                    ?.takeIf { it.length in 1..MAX_TOKEN_LENGTH }
                    ?: return@launch callback.send(
                        errorEnvelope(401, "invalid_access_token")
                    )
                val httpMethod = method
                    ?.uppercase()
                    ?.takeIf(ALLOWED_METHODS::contains)
                    ?: return@launch callback.send(
                        errorEnvelope(405, "method_not_allowed")
                    )
                val requestTarget = target
                    ?.takeIf {
                        it.startsWith("/api/v1/") &&
                            it.length <= MAX_TARGET_LENGTH
                    }
                    ?: return@launch callback.send(
                        errorEnvelope(400, "invalid_target")
                    )
                val body = bodyJson.orEmpty()
                    .toByteArray(StandardCharsets.UTF_8)
                if (body.size > MAX_REQUEST_BODY_BYTES) {
                    return@launch callback.send(
                        errorEnvelope(413, "payload_too_large")
                    )
                }

                val headers = buildMap {
                    put("host", "127.0.0.1")
                    idempotencyKey
                        ?.takeIf { it.length in 8..MAX_IDEMPOTENCY_LENGTH }
                        ?.let { put("idempotency-key", it) }
                    ifMatch
                        ?.takeIf { it.length in 1..MAX_REVISION_LENGTH }
                        ?.let { put("if-match", it) }
                    requestId
                        ?.takeIf { it.length in 1..MAX_REQUEST_ID_LENGTH }
                        ?.let { put("x-request-id", it) }
                    if (body.isNotEmpty()) {
                        put("content-type", "application/json")
                    }
                }

                val response = try {
                    controller.handleAuthenticated(
                        request = AgentHttpRequest(
                            method = httpMethod,
                            target = requestTarget,
                            headers = headers,
                            body = body
                        ),
                        accessToken = token,
                        presentedBinding = binding,
                        transport = TRANSPORT
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AgentHttpResponse(
                        status = 500,
                        body = """{"error":{"code":"ipc_request_failed"}}"""
                            .toByteArray(StandardCharsets.UTF_8),
                        headers = mapOf(
                            "Content-Type" to
                                "application/json; charset=utf-8"
                        )
                    )
                }

                callback.send(responseEnvelope(response))
            }
        }
    }

    private fun resolveCaller(
        callingUid: Int,
        packageName: String?
    ) = packageName
        ?.let { callerIdentityResolver.resolve(callingUid, it) }

    private fun responseEnvelope(response: AgentHttpResponse): String {
        if (response.body.size > MAX_RESPONSE_BODY_BYTES) {
            return errorEnvelope(413, "ipc_response_too_large")
        }
        val bodyText = response.body.toString(StandardCharsets.UTF_8)
        val parsedBody = if (bodyText.isBlank()) {
            JSONObject.NULL
        } else {
            runCatching { JSONTokener(bodyText).nextValue() }
                .getOrDefault(bodyText)
        }
        return envelope(
            status = response.status,
            body = parsedBody,
            headers = response.headers
        )
    }

    private fun errorEnvelope(status: Int, code: String): String =
        envelope(
            status = status,
            body = JSONObject().put(
                "error",
                JSONObject().put("code", code)
            )
        )

    private fun envelope(
        status: Int,
        body: Any,
        headers: Map<String, String> = emptyMap()
    ): String = JSONObject()
        .put("status", status)
        .put("headers", JSONObject(headers))
        .put("body", body)
        .toString()

    private fun INexaFlowAgentCallback?.send(payload: String) {
        if (this == null) return
        runCatching { onResult(payload) }
    }

    private companion object {
        const val TRANSPORT = "ANDROID_BINDER"
        const val MAX_SECRET_LENGTH = 512
        const val MAX_TOKEN_LENGTH = 4096
        const val MAX_TARGET_LENGTH = 4096
        const val MAX_REQUEST_BODY_BYTES = 256 * 1024
        const val MAX_RESPONSE_BODY_BYTES = 512 * 1024
        const val MAX_IDEMPOTENCY_LENGTH = 256
        const val MAX_REVISION_LENGTH = 64
        const val MAX_REQUEST_ID_LENGTH = 256
        val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
    }
}
