# NexaFlow Agent Pairing

New pairing requests default to standard scoped access. The client must
explicitly request timed or permanent full access when needed. Existing
grants retain their persisted mode until changed. There are two transports
to the same pairing backend (`AgentAccessManager`); both enforce the
5-minute, single-use, lockout-on-abuse challenge lifecycle.

## QR / payload pairing (any agent)

1. Settings → AI & Agents → **Generate pairing code**. The phone shows a
   one-time payload containing the server address, a `challengeId`, a
   `challengeSecret` and the `pairingPath`
   (`/api/v1/auth/pair/complete`).
2. The agent (or `nexaflow-agent pair --payload …`) POSTs the challenge to
   `pairingPath` and receives a refresh credential bounded by the requested
   grant mode.
3. The agent exchanges it at `/api/v1/auth/session` for short-lived Bearer
   sessions from then on.

Give the payload only to the agent you want to trust. It expires in
5 minutes and cannot be reused.

HIGH and CRITICAL agent-authored tasks are rejected with a content hash until
the user reviews and approves that exact definition in Settings → AI & Agents.
Approval ids expire after five minutes and can authorize only one matching
commit for that agent.

## Android IPC pairing (same-device agents)

Same-device agents bind the exported `NexaFlowAgentService` (gated by the
`com.nexaflow.app.permission.BIND_AGENT_SERVICE` permission, enforced in CI)
and complete pairing over Binder. UID → package ownership and the SHA-256
signing certificate bind the grant; refresh/session issuance re-checks the
same binding, so a repackaged app cannot reuse another agent's credential.
See `docs/AGENT_ANDROID_IPC.md`.

## Desktop bridge pairing

`tools/nexaflow-agent` consumes the same one-time payload (`pair` command),
stores the rotated refresh credential locally, and proxies MCP stdio to the
phone over `adb forward` (USB, no phone port exposed) or trusted LAN. See
`docs/AGENT_BRIDGE.md`.

## Revocation

Per-agent **Disconnect/Revoke** or **Revoke all agents** in Settings → AI &
Agents deletes credentials, sessions and challenges immediately. Created
automations keep working; secrets were never exposed.
