# NexaFlow Universal Agent Platform

This document is the implementation contract for NexaFlow's provider-neutral AI
agent platform. The automation engine remains the authority for scheduling and
execution; agent protocols are adapters, never alternate execution paths.

## Architectural invariants

1. No agent adapter writes Room directly.
2. No agent adapter schedules AlarmManager work directly.
3. No agent adapter invokes privileged backends directly.
4. Every mutation crosses `AutomationCommandService`.
5. Every agent-visible node/schema is derived from the canonical automation
   catalog rather than maintained as a second enum inventory.
6. Agent-created tasks default to disabled unless an explicit trusted policy
   requests activation.
7. Permanent agent access removes per-task approval prompts, but it never makes
   secrets readable and never bypasses Android permission/capability checks.
8. Existing scheduler, monitor, execution, exit/revert and CapabilityRouter
   ownership remains unchanged.

## Target architecture

```text
NexaFlow Chat / Cloud Agents / Local Agents / Local Models
                         |
        MCP / A2A / REST / Binder / Bridge
                         |
              Universal Agent Gateway
                         |
             Persistent Agent Grant
                         |
              Authentication + Audit
                         |
              AutomationCommandService
                         |
       Validate -> Dry-run -> Transaction
                         |
                AutomationRepository
                         |
                Existing Scheduler
                         |
                 Execution Engine
                         |
                 CapabilityRouter
                         |
            Android API / Shizuku / Root
```

## Protocol strategy

- MCP is the primary tool protocol.
- REST/OpenAPI 3.1 is the universal compatibility API.
- A2A is used for agent-to-agent discovery and delegation.
- Android Binder/AIDL is used for same-device agents.
- NexaFlow Bridge supports desktop/local agents over stdio MCP, USB/ADB and
  trusted LAN connections.
- Remote access uses an outbound encrypted relay. The phone is never exposed as
  an unauthenticated public listener.

All protocol adapters must expose the same command semantics.

## Phase 1 - automation-control foundation

- [x] Add `:core:automation-control`.
- [x] Add versioned `AgentTaskDraftV1`.
- [x] Keep the persisted `Automation` model private behind a mapper.
- [x] Project agent schemas from `AutomationNodeCatalog`.
- [x] Reuse structural, dependency and typed node-config validation.
- [x] Add centralized create/update/enable/disable/delete command boundary.
- [x] Gate agent writes behind read-only dry-run.
- [x] Add a revision foundation using monotonic `updatedAt`.
- [x] Block dependency-breaking deletes.
- [x] Add unit coverage for mapping, catalog parity, preflight and conflicts.
- [x] First-party mutation callers moved to the command boundary:
      dashboard, details, builder, quick tiles, widgets, Wear listener and
      backup import/export all cross `AutomationCommandService` with HUMAN /
      IMPORT provenance. Deep-link capability-token rotation stays direct by
      design (identity plumbing, not definitions) and is documented at each
      call site.
- [x] Revision foundation uses transactional metadata CAS in the Room
      persistence phase; UI callers pass no expected revision
      (last-writer-wins, as before) while agents keep strict revisions.

## Phase 2 - persistent agent security

Add:

- `AgentGrant`
- `AgentCredential`
- `AgentSession`
- permanent full-access mode
- global Agent Access kill switch
- per-agent revoke
- revoke-all
- short-lived access tokens plus revocable long-lived refresh credentials
- credential hashing at rest
- pairing attempt expiry and replay protection
- rate limits and payload limits
- immutable secret-reference rule: agents may use `secret://...` but may not
  retrieve plaintext secrets

The default product surface stays intentionally simple:

```text
AI Agent Access
- Disabled
- Full permanent access
```

Fine-grained scopes remain internal policy primitives even when the UI grants
the complete scope set with one choice.

## Phase 3 - persistence, provenance and audit

Implemented foundation:

- [x] Room schema 21 agent control-plane ledger
- [x] automation API provenance metadata
- [x] redacted agent audit events
- [x] hashed idempotency keys with bounded replay metadata
- [x] transactional API revisions / CAS
- [x] atomic definition + metadata + audit + idempotency commits
- [x] transaction-local dependency revalidation
- [x] detection of out-of-band first-party definition edits
- [x] permanent agent grants/credentials/sessions remain Keystore-backed

Later phases add their own dedicated persistence as required for:

- conversations/messages
- execution traces
- subscriptions
- model providers

Automation metadata records origin, agent, provider/model, transport,
conversation/request identifiers, risk, revision and timestamps.

Agent audit redacts credentials, secret values and sensitive dynamic config.

## Phase 4 - scheduling and simulation

Implemented:

- [x] schedule preview using the existing `TimeTriggerCalculator`
- [x] next-N occurrence preview
- [x] DEVICE_LOCAL timezone policy
- [x] optional FIXED IANA timezone policy
- [x] SimulationService built on command preflight + dry-run/capability planning
- [x] no-side-effect execution preview
- [x] machine-readable requirement gaps
- [x] semantic action strategy/candidate preview
- [x] optional schedule preview composed into simulation
- [x] explicit `noSideEffects=true` transport contract

All existing recurrence forms remain represented by the production calculator.
Simulation never writes Room, schedules alarms, invokes action handlers, executes
capability backends, Root commands, or Shizuku operations.

## Phase 5 - REST/OpenAPI

Implemented foundation:

- [x] local `/api/v1` loopback transport
- [x] status/capabilities/catalog
- [x] list/get/create/update/delete tasks
- [x] enable/disable/run
- [x] validate + no-side-effect simulation/dry-run
- [x] schedule preview
- [x] bounded history and redacted audit reads
- [x] permanent-agent refresh credential -> short-lived bearer session exchange
- [x] scope authorization and shared abuse controls
- [x] `Idempotency-Key` for task mutations and side-effecting manual runs
- [x] atomic run reservation prevents timeout/retry duplicate side effects
- [x] `ETag` / `If-Match` optimistic concurrency, including manual runs
- [x] lifecycle-consistent enable/disable/delete behavior
- [x] host/origin hardening for loopback requests
- [x] bounded HTTP/1.1 request parsing; chunked transfer is rejected
- [x] OpenAPI 3.1 and JSON Schema runtime documents
- [x] checked-in API documents protected by contract-parity tests

The REST adapter never writes Room task definitions or invokes AlarmManager
directly. Mutations flow through `AutomationCommandService`; execution flows
through the production `ExecutionEngine`; authorization flows through the
shared permanent Agent Access boundary. The server binds only to
`127.0.0.1`; LAN and remote transports remain separate later phases.

## Phase 6 - MCP

Implemented foundation:

- [x] loopback-only `/mcp` on the existing bounded HTTP server
- [x] stateless MCP `2026-07-28` with `server/discover`
- [x] MCP `2025-11-25` initialize compatibility
- [x] modern per-request metadata and routing-header validation
- [x] deterministic tool inventory with read/destructive/idempotent annotations
- [x] status, capabilities and trigger/action/constraint catalogs
- [x] list/get/create/update/clone/enable/disable/delete/run task tools
- [x] validation, schedule preview and no-side-effect simulation
- [x] history and redacted audit tools
- [x] permanent-agent bearer authorization and scope checks
- [x] REST/MCP parity for idempotency, revisions and lifecycle semantics
- [x] modern result discrimination and conservative cache hints

MCP remains a thin adapter over the same REST/control services. It does not
write Room, schedule alarms, execute privileged backends or implement a second
automation engine. The Android build intentionally reuses the bounded
ServerSocket transport instead of adding a second Ktor HTTP server stack.

## Phase 7 - AI & Agents UI

Add Settings -> AI & Agents with:

- Agent Access
- connected agents
- permanent access state
- QR pairing
- local API/MCP status
- model providers
- activity/audit
- kill switch
- per-agent revoke
- revoke-all

## Phase 8 - in-app AI chat

Implemented foundation:

- [x] dedicated NexaFlow AI chat destination
- [x] home-screen "Ask NexaFlow..." quick command surface
- [x] provider-neutral conversation engine
- [x] streaming assistant deltas
- [x] cancellation through the ViewModel/coroutine lifecycle
- [x] bounded transcript, output, tool-call and tool-iteration limits
- [x] isolated conversation IDs and per-chat transcript state
- [x] tool progress surfaced in the chat UI
- [x] in-process trusted tool adapter that reuses the MCP/REST/control pipeline
- [x] create/update/enable/disable/delete/run tools retain revision and idempotency rules
- [x] tool results are preserved in assistant history for subsequent model turns
- [ ] persisted conversation history
- [ ] optional voice input

NexaFlow intentionally keeps the existing single-dashboard navigation model
instead of introducing a permanent bottom bar solely for AI. The dashboard
opens the dedicated chat surface directly, while agent/model management remains
under Settings -> AI & Agents.

## Phase 9 - local model providers

Implemented foundation:

- [x] generic OpenAI-compatible provider abstraction
- [x] Ollama-compatible `/v1/chat/completions` configuration
- [x] LM Studio, vLLM, llama.cpp-compatible and LocalAI-compatible endpoints
- [x] local/private HTTP endpoints without enabling app-wide cleartext traffic
- [x] HTTPS endpoints for remote/cloud-compatible servers
- [x] DNS/private-address validation for local endpoints
- [x] redirect rejection and bounded request/response parsing
- [x] API keys stored through SecureStorage and never echoed into model context
- [x] real SSE/NDJSON streaming with incremental tool-call reconstruction
- [x] buffered JSON fallback for servers that ignore streaming
- [x] Android 17 local-network runtime permission wiring for LAN model servers
- [x] user-triggered provider connectivity test
- [x] capability probing for native tool calling, structured JSON and streaming
- [x] capability state published through the provider registry
- [x] structured-JSON tool fallback for models without native function calling
      (`AiStructuredToolParser`: `{"tool", "arguments"}` extraction, known-tool
      allow-list, object-shape and bound checks, same execution pipeline and
      trace coverage as native calls; instruction stays provider-bound, never
      stored)
- [ ] provider-specific model discovery endpoints
- [ ] vision/reasoning/context-window capability discovery where the backend
      exposes reliable metadata

The implementation deliberately avoids separate Ollama/LM Studio/vLLM classes.
They share `OpenAiCompatibleProvider`; provider-specific convenience discovery
can be layered on top without forking the conversation or tool execution path.

## Phase 10 - model routing

Implemented foundation:

- [x] Automatic, Local only, Cloud only and Selected provider modes
- [x] persisted routing policy in DataStore
- [x] deterministic provider ordering
- [x] capability-aware preference for tool/structured-output capable models
- [x] Automatic mode prefers local providers
- [x] cloud fallback in Automatic mode is explicit and disabled by default
- [x] Local only never routes to cloud providers
- [x] Cloud only never routes to local providers
- [x] Selected provider never silently falls back to another provider
- [x] routing policy is applied live to NexaFlow Chat
- [x] settings UI exposes routing policy and explicit cloud fallback

Future routing can additionally consider connectivity, task complexity, model
cost, latency and richer privacy policy. Those signals must refine the same
deterministic router rather than create provider-specific selection paths.

## Phase 11 - Android IPC

Implemented foundation:

- [x] exported `NexaFlowAgentService` with explicit reviewed permission gate
- [x] async AIDL callback contract; Binder threads are not held during execution
- [x] caller UID -> claimed package ownership verification
- [x] SHA-256 signing-certificate binding for permanent agent grants
- [x] one-time pairing can bind an initially unbound challenge to Android caller identity
- [x] refresh/session issuance requires the same package/certificate binding
- [x] all authenticated Binder requests reuse `AgentRequestAuthorizer`
- [x] all Binder operations reuse `AgentApiController` route semantics
- [x] REST/MCP/Binder share scopes, rate limits, idempotency, revisions and lifecycle behavior
- [x] bounded request/response payloads below Binder transaction limits
- [x] exported-component CI gate requires the exact Binder service permission
- [x] identity-binding and signer-fingerprint unit coverage

The custom Android permission is only a coarse discovery/binding gate. Trust is
established by the persisted NexaFlow grant and enforced with UID/package/signing
certificate identity plus the rotating refresh/access credential lifecycle.

## Phase 12 - NexaFlow Bridge

Implemented USB/desktop foundation:

- [x] zero-dependency Python desktop CLI
- [x] `nexaflow-agent pair`
- [x] `nexaflow-agent status`
- [x] `nexaflow-agent devices`
- [x] `nexaflow-agent mcp`
- [x] MCP stdio -> phone Streamable HTTP proxy
- [x] temporary ADB/USB TCP forwarding to the phone's loopback-only server
- [x] automatic short-lived session exchange and rotated refresh-token storage
- [x] retry-once session renewal after HTTP 401
- [x] MCP 2025-11-25 and 2026-07-28 routing-header support
- [x] bounded stdin/HTTP payloads and redirect rejection
- [x] direct endpoint policy: public endpoints require HTTPS; HTTP is private/loopback only
- [x] bridge unit tests in CI
- [x] explicit Android-side trusted-LAN listener toggle with Android 17 permission gate
- [x] private numeric Host policy; DNS/public Host headers remain rejected
- [x] global Agent Access kill switch collapses LAN exposure back to loopback
- [ ] QR ingestion convenience on desktop

The bridge intentionally keeps Android loopback-only by default. USB mode uses
`adb forward`, so no phone port is exposed to the LAN. Settings -> AI & Agents
contains the separate private-LAN switch; enabling it requires the Android 17
local-network permission when applicable and rebinds the authenticated server.
Disabling Agent Access also disables persisted LAN exposure.

## Phase 13 - event subscriptions

Support NexaFlow -> Agent events:

- automation.created/updated/deleted
- automation.triggered/completed/failed
- capability.changed
- device.state.changed
- agent.connected/disconnected

Implemented foundation:

- [x] `AutomationMutationObserver` fan-out on every committed definition
      mutation, bridged to `AgentEventHub` in the app layer, so all
      transports (UI, MCP, REST, A2A, Binder, relay, import) produce
      created/updated/deleted events from one place
- [x] execution lifecycle events via `AutomationRunListener` in
      `:core:execution`: admitted runs publish triggered, every persisted
      history record publishes completed/failed, bridged to `AgentEventHub`
      with redacted reasons; listener failures never affect execution

Local agents may wake on events instead of polling continuously.

## Phase 14 - A2A

Implemented foundation:

- [x] public `/.well-known/agent-card.json` with provider-neutral capabilities
- [x] loopback-only `/a2a` JSON-RPC adapter (`message/send`, `tasks/get`)
- [x] skill ids exactly equal MCP tool names (REST/MCP/A2A parity by construction)
- [x] skill execution reuses `AgentMcpToolExecutor` -> `AgentApiController` ->
      `AutomationCommandService`; no direct Room/AlarmManager/privileged access
- [x] permanent-agent Bearer auth with union-of-skills scopes plus shared
      payload/rate-limit gates
- [x] explicit rejection of `message/stream`, `tasks/cancel` and JSON-RPC batches
- [x] unit coverage for card parity, skill execution, unknown-skill fail-closed,
      auth boundary and unsupported methods

Task delegation and results/events flow through the command service as the only
mutation authority. See `docs/AGENT_A2A.md`.

## Phase 15 - remote relay

Implemented device-side foundation (`:core:agent-relay`, `docs/AGENT_RELAY.md`):

- [x] outbound-only client: the phone dials out, never a public listener
- [x] versioned JSON frames (`hello`/`request`/`response`/`ping`/`pong`, 256 KB bound)
- [x] HMAC-SHA256 link-key request signatures over canonical bytes
- [x] bounded identity/replay fields: device, agent, request, timestamp, nonce
- [x] timestamp skew window (5 min) + bounded 4096-nonce replay guard
- [x] route allow-list (`/api/v1/*`, `/mcp`, `/a2a`, public JSON docs)
- [x] SecureStorage link-key provisioning/rotation/import/deprovisioning
- [x] heartbeat + bounded exponential backoff reconnect until stopped
- [x] validated requests forward into the unchanged local agent API pipeline
      (bearer/scope/rate-limit/idempotency/revision semantics preserved)
- [x] unit coverage for signing, replay, validation order, framing and the
      serve/reconnect loop

The relay server itself is out of scope; it must speak the documented wire
contract. Deprovisioning or the global kill switch stops remote access
without touching grants or automations.

## Phase 16 - advanced agent behavior

Implemented foundation:

- [x] replayable redacted traces (`AiAgentTraceRecorder` in `:core:ai-runtime`):
      per-turn shape only (tool names, redacted argument/output previews,
      char counts, outcome); raw prompt/assistant text and secrets never stored
- [x] opt-in engine tracing (`AiConversationEngine(traceSink=...)`, default
      off, zero behavior change without a sink)
- [x] side-effect-free re-analysis (`AiTraceReplayAnalyzer`: findings +
      suggested next step, never re-executes tools)
- [x] event-driven diagnosis (`AgentFailureDiagnoser` in
      `:core:automation-control`): classifies AUTOMATION_FAILED signals
      (validation drift / non-executable / capability change / unknown) with
      bounded evidence
- [x] bounded self-healing proposals: at most an `enabled=false` draft for
      validation/dry-run failures on enabled tasks; applying it always goes
      through `AutomationCommandService` with optimistic concurrency
- [x] voice-first commands: system speech recognizer fills the AI chat
      composer (no RECORD_AUDIO permission; transcript uses the normal send
      path with validation/dry-run policy unchanged)
- [x] unit coverage for redaction, trace lifecycle/eviction, replay analysis
      and diagnosis branches

Remaining future work: persisted conversation/trace history, multi-agent
delegation beyond A2A task delegation, and richer self-healing beyond
safe-disable proposals. Self-healing never bypasses validation, dry-run or
command policy.

## Security invariants

- Full permanent access means no repetitive per-task prompt after pairing.
- Android runtime/special permissions are still authoritative.
- Secrets are references, never model-readable values.
- Prefer semantic operations; agents should not generate arbitrary root/Shizuku
  shell when a typed operation exists.
- CapabilityRouter chooses Android API, Shizuku, root or user handoff.
- All network actions retain SSRF/private-network protections.
- All imports/agent payloads retain size/depth/count limits.
- Audit data is redacted.
- A global kill switch immediately blocks all agent sessions without deleting
  automations they created.
- Every committed definition carries a system-computed risk level
  (`AgentRiskEvaluator`, LOW–CRITICAL) for audit/telemetry/debugging; it
  never gates mutations and never trusts caller-supplied hints.

## Required CI coverage

Add/maintain gates for:

- Agent API contract
- schema registry parity
- auth and permanent grants
- scope policy
- risk policy
- idempotency
- optimistic concurrency
- transaction rollback
- secret redaction
- MCP/REST/A2A parity
- provider capability probing
- schedule preview
- simulation
- event subscriptions
- scheduler integration after agent mutations
- fuzzing malformed/huge/deep/replayed payloads

## V1 definition of done

V1 is complete when a user can:

1. enable AI Agent Access;
2. pair an agent once;
3. grant permanent full access once;
4. receive no per-task approval prompts after that grant;
5. let the agent read the real capability/catalog inventory;
6. preview and validate a schedule;
7. dry-run a workflow;
8. create/update/enable it atomically;
9. have the existing scheduler execute it;
10. inspect the action in Agent Activity;
11. revoke one agent or all agents;
12. keep already-created automations after revocation;
13. prove that no plaintext secret was exposed to the agent.

The next milestone adds the same experience directly inside NexaFlow Chat with
local models such as Ollama as well as external agents.
