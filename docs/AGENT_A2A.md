# NexaFlow A2A Agent

Phase 14 of `docs/AGENT_PLATFORM.md`. NexaFlow acts as an A2A agent for
agent-to-agent delegation while keeping `AutomationCommandService` as the only
mutation authority.

## Discovery

Public, unauthenticated:

```text
GET /.well-known/agent-card.json
```

Returns the NexaFlow agent card: name, `protocolVersion 0.3.0`, RPC URL
(`/a2a`), capabilities and the full skill inventory. Skill ids are exactly the
MCP tool names (`nexaflow.create_task`, ...), so REST/MCP/A2A stay in parity by
construction (`AgentA2ACard.skillIds()` == `AgentMcpToolRegistry` names).

## RPC

Authenticated, loopback-only:

```text
POST /a2a   (JSON-RPC 2.0, Content-Type: application/json)
```

Bearer permanent-agent access session is required (same lifecycle as
REST/MCP). Browser `Origin` requests are rejected; `Host` must satisfy
`AgentApiHostPolicy`.

### message/send

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "message/send",
  "params": {
    "message": {
      "role": "user",
      "parts": [
        {"kind": "data", "data": {"skill": "nexaflow.list_tasks", "input": {}}}
      ]
    }
  }
}
```

- `parts` accepts 1..8 entries.
- Each part is either `{kind:"data", data:{skill, input}}` or
  `{kind:"text", text:"{\"skill\":..., \"input\":...}"}`.
- Skills execute in order through `AgentMcpToolExecutor` (the same forwarder
  MCP uses), so validation, idempotency (`idempotencyKey`), optimistic
  concurrency (`revision` / `If-Match`), dry-run gating, lifecycle handling and
  audit redaction are identical across protocols.
- Success returns an A2A task with `status.state == "completed"` and one
  artifact part per skill: `{skill, status, result}`.
- A failing skill short-circuits the remaining parts and returns
  `status.state == "failed"` with `{code:"skill_failed", skill, httpStatus}`
  plus any partial artifact parts.
- Unknown skills fail closed (`{code:"unknown_skill"}`) without execution.

### tasks/get

```json
{"jsonrpc": "2.0", "id": "2", "method": "tasks/get", "params": {"id": "<automationId>"}}
```

Read probe delegating to the `nexaflow.get_task` skill path. Unknown ids
return JSON-RPC `-32004`.

### Explicitly unsupported

- `message/stream` -> `-32601` (use synchronous `message/send`).
- `tasks/cancel` -> `-32601` (automation runs are not remotely cancellable).
- JSON-RPC batching -> HTTP 400.

## Example delegation

```text
Gemini Agent --A2A--> NexaFlow Agent --AutomationCommandService--> Repository
```

```json
{
  "jsonrpc": "2.0",
  "id": "9",
  "method": "message/send",
  "params": {
    "message": {
      "role": "user",
      "parts": [
        {"kind": "data", "data": {"skill": "nexaflow.validate_task", "input": {"task": {"name": "Night DND"}}}},
        {"kind": "data", "data": {"skill": "nexaflow.preview_schedule", "input": {"task": {"name": "Night DND"}, "fromEpochMillis": 0}}}
      ]
    }
  }
}
```

## Security invariants

- Agent card exposes schemas, never secrets or credentials.
- Every RPC call enforces scopes = union of invoked skill scopes, plus the
  shared payload/rate-limit gate (`AgentRequestAuthorizer`).
- Mutations still require `Idempotency-Key` equivalents and exact revisions;
  stale revisions return `409`-carrying `skill_failed` artifacts.
- Audit events are redacted exactly as in REST/MCP.
