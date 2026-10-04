# NexaFlow Agent Security

## Access model

New pairing requests default to scoped standard access. Existing grants keep
their stored mode for compatibility and are shown with a warning until the
user reviews or reduces them. Available modes are read-only, standard, timed
full access (one hour), and permanent full access. Permanent full access must
be selected explicitly in the client and should be reduced when no longer
needed.

```text
AI Agent Access
○ Disabled
● Read-only / Standard / Timed full / Permanent full
```

After one pairing, the agent operates without per-task prompts until the
user revokes it. Internally, access is still least-privilege machinery:

- **Scopes** (`TASKS_READ/CREATE/UPDATE/DELETE/ENABLE/RUN`,
  `CATALOG_READ`, `HISTORY_READ`, `NETWORK_REQUEST`, `PLUGINS_USE`,
  `ELEVATED_REQUEST`, `SECRETS_REFERENCE`) are checked on every request by
  the shared `AgentRequestAuthorizer` across REST, MCP, A2A and Binder. Full
  access grants the complete set at once.
- **Kill switch**: disabling Agent Access drops all sessions and consumes
  pending pairings immediately, without deleting pairings or automations.
- **Revoke one / revoke all**: deletes credentials, sessions and pairing
  challenges. Automations created earlier keep running.

## Credential lifecycle

- Pairing challenges are single-use, expire in 5 minutes and lock after
  bounded failures.
- The long-lived credential is a refresh token; only its SHA-256 hash is
  stored. Every session exchange **rotates** it. Reuse of the immediately
  previous token revokes the agent credential family and active sessions,
  and emits a redacted audit record.
- Access sessions are short-lived (15 minutes default, max 8 per agent).
- Transport binding (package/certificate/transport key) is verified on
  issuance and on every use; mismatches fail closed.

## Abuse controls

Even trusted agents are rate-limited (120 requests/window by default) and
payload-limited (256 KB bodies, bounded headers, schema depth/count caps)
before full parsing. These stop runaway agent loops; they are not user
approvals.

## Secrets

Agents reference secrets (`secret://…`, `vault:…`) but can never read them:

- `SecretVault` has no enumeration, export or logging; values resolve only
  inside action execution.
- Audit events, execution traces, agent transcripts, relay frames and API
  error payloads are redacted (`SecretRedactor` + audit redaction); stored
  traces keep shapes and counts, never raw text or values.

## Risk

Every committed definition carries a system-computed risk level
(`LOW/MEDIUM/HIGH/CRITICAL` from `AgentRiskEvaluator`: shell/package/destructive
actions, remote triggers, graph size). Agent-originated HIGH and CRITICAL
definitions are rejected until the exact content hash receives a one-time
approval in the app. Approval records are agent-bound, expire after five
minutes, and are consumed when used. Runtime capability-level enforcement
stays in the domain `RiskEngine`, and Android runtime/special permissions
remain authoritative regardless of grants.

## Network boundary

Agent API listeners bind to loopback. LAN access is deliberately disabled
until an authenticated TLS listener with client certificate fingerprint
pinning is available. A stored LAN preference cannot widen the bind address.
