# NexaFlow Agent REST API (v1)

Provider-neutral HTTP control plane for agents. Loopback-only by default
(`127.0.0.1:8766`); a private-LAN listener exists behind an explicit toggle
plus the Android 17 local-network permission. Browser `Origin` requests are
rejected; `Host` must satisfy `AgentApiHostPolicy`.

The checked-in contracts are authoritative and parity-gated by
`AgentApiDocumentParityTest`:

- `docs/api/openapi-v1.json` — OpenAPI 3.1 document
- `docs/api/task-schema-v1.json` — `AgentTaskDraftV1` JSON Schema

## Authentication

1. `POST /api/v1/auth/pair/complete` with a one-time pairing challenge
   (Settings → AI & Agents → Generate pairing code, 5-minute expiry)
   returns a permanent refresh credential. Only its hash is stored.
2. `POST /api/v1/auth/session` exchanges it for a short-lived Bearer access
   session and rotates the refresh credential (replay of the old one fails).
3. Every other route requires `Authorization: Bearer <access-token>` plus an
   optional `x-nexaflow-transport-key` binding.

`GET /api/v1/openapi.json` and `/api/v1/schemas/task-v1.json` are public.

## Routes

```text
GET    /api/v1/status               API + agent-access status
GET    /api/v1/capabilities         live device capability observations
GET    /api/v1/catalog              canonical trigger/action/constraint schemas
GET    /api/v1/tasks                list tasks with revisions
GET    /api/v1/tasks/{id}           get one task (ETag = revision)
POST   /api/v1/tasks                create (Idempotency-Key required)
PATCH  /api/v1/tasks/{id}           update (If-Match + Idempotency-Key)
DELETE /api/v1/tasks/{id}           delete, lifecycle-cleaned (If-Match + Idempotency-Key)
POST   /api/v1/tasks/{id}/enable    enable at an exact revision
POST   /api/v1/tasks/{id}/disable   disable with lifecycle cleanup
POST   /api/v1/tasks/{id}/run       run once per idempotency key + revision
POST   /api/v1/validate             validate + dry-run a draft, no side effects
POST   /api/v1/simulate             no-side-effect execution simulation
POST   /api/v1/schedules/preview    next-N occurrences, DEVICE_LOCAL or FIXED IANA
GET    /api/v1/history              bounded execution history
GET    /api/v1/events               waitable bounded event stream (streamId/after/limit/waitMs)
GET    /api/v1/audit                bounded redacted agent audit
```

Mutations are atomic through `AutomationCommandService`: mapping, structural,
dependency and config validation, dry-run gating, optimistic-concurrency
(`ETag`/`If-Match` → `409 revision_conflict`), idempotency replays
(`X-Idempotent-Replay`) and lifecycle-consistent enable/disable/delete.
`POST /mcp` (MCP `2026-07-28` + `2025-11-25` compat) and `POST /a2a`
(A2A `message/send`, `tasks/get`) expose the same semantics; skill/tool
names are identical across protocols by construction.
`GET /.well-known/agent-card.json` is the public A2A discovery card.

## Versioning

`/api/v1` is independent of the app version; portable payloads carry
`schemaVersion = 1`. Fields are only added, deprecated, then removed in a
major API version — see `docs/AGENT_PLATFORM.md`.
