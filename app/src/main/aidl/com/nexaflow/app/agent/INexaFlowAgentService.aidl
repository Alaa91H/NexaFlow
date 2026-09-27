package com.nexaflow.app.agent;

import com.nexaflow.app.agent.INexaFlowAgentCallback;

interface INexaFlowAgentService {
    void completePairing(
        String packageName,
        String challengeId,
        String challengeSecret,
        INexaFlowAgentCallback callback
    );

    void exchangeSession(
        String packageName,
        String refreshToken,
        INexaFlowAgentCallback callback
    );

    void request(
        String packageName,
        String accessToken,
        String method,
        String target,
        String bodyJson,
        String idempotencyKey,
        String ifMatch,
        String requestId,
        INexaFlowAgentCallback callback
    );
}
