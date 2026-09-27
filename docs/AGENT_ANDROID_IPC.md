# NexaFlow Android Agent IPC

NexaFlow exposes a same-device Binder service for Android agent apps that cannot
or should not use the loopback REST/MCP transport.

## Discovery

Service action:

```text
com.nexaflow.agent.BIND
```

Binding permission:

```text
com.nexaflow.app.permission.BIND_AGENT_SERVICE
```

The permission is intentionally only a coarse Android binding/discovery gate.
It is not the authorization boundary. Every sensitive call still requires a
persisted NexaFlow agent grant and cryptographic credential.

## Identity binding

For every Binder call NexaFlow captures `Binder.getCallingUid()` before moving
work to a coroutine. The client also supplies its package name. NexaFlow then:

1. confirms the package belongs to the calling UID;
2. reads the installed package signing certificates;
3. computes stable SHA-256 certificate fingerprints;
4. presents package + certificate identity to `AgentAccessManager`.

A one-time pairing challenge created in NexaFlow may start unbound. When the
Android client completes that challenge through Binder, the permanent grant is
bound to the verified package/certificate identity. Future refresh-token
exchange and access-token authorization must present the same identity.

## AIDL v1

`INexaFlowAgentService` exposes asynchronous callback methods:

- `completePairing(packageName, challengeId, challengeSecret, callback)`
- `exchangeSession(packageName, refreshToken, callback)`
- `request(packageName, accessToken, method, target, bodyJson, idempotencyKey, ifMatch, requestId, callback)`

Callbacks return one JSON envelope:

```json
{
  "status": 200,
  "headers": {},
  "body": {}
}
```

`request` accepts only versioned `/api/v1/` routes and the same HTTP verbs
supported by the loopback API. Mutations still require the same idempotency and
revision preconditions as REST/MCP.

## Security invariants

- Binder transport never writes task Room tables directly.
- Binder transport never calls AlarmManager or privileged execution backends directly.
- `AgentRequestAuthorizer` remains the shared scope, payload and rate boundary.
- `AgentApiController` remains the shared route/validation/mutation boundary.
- Calling UID is captured synchronously; it is never reconstructed after coroutine dispatch.
- Pairing/refresh/access secrets are never logged.
- Request and response sizes are bounded below Binder transaction limits.
- Revoking an agent or disabling global AI Agent Access immediately invalidates Binder use as well.
