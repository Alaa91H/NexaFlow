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
- [ ] Move existing first-party mutation callers to the command boundary once
      lifecycle side-effect parity is proven.
- [ ] Replace timestamp revision checks with transactional metadata CAS in the
      persistence phase.

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

Add Room migrations and dedicated tables for:

- agent grants
- credentials/sessions
- audit events
- idempotency keys
- automation API metadata
- conversations/messages
- execution traces
- subscriptions
- model providers

Automation metadata records origin, agent, provider/model, transport,
conversation/request identifiers, risk, revision and timestamps.

Agent audit must redact credentials, secret values and sensitive dynamic config.

## Phase 4 - scheduling and simulation

Add:

- schedule preview using the existing `TimeTriggerCalculator`
- next-N occurrence preview
- DEVICE_LOCAL timezone policy
- optional FIXED IANA timezone policy
- SimulationService built on dry-run/capability planning
- no-side-effect execution preview

All existing recurrence forms must remain representable.

## Phase 5 - REST/OpenAPI

Add local `/api/v1` endpoints for:

- status/capabilities/catalog
- list/get/create/update/delete tasks
- enable/disable/run
- validate/dry-run
- schedule preview
- history/audit

Requirements:

- loopback-only by default
- versioned schemas independent of app version
- Idempotency-Key on mutations
- optimistic concurrency
- generated OpenAPI 3.1 and JSON Schema
- bounded request parsing

## Phase 6 - MCP

Expose tools including:

- `nexaflow.get_capabilities`
- `nexaflow.list_triggers`
- `nexaflow.list_actions`
- `nexaflow.list_tasks`
- `nexaflow.get_task`
- `nexaflow.validate_task`
- `nexaflow.preview_schedule`
- `nexaflow.dry_run_task`
- `nexaflow.create_task`
- `nexaflow.update_task`
- `nexaflow.clone_task`
- `nexaflow.enable_task`
- `nexaflow.disable_task`
- `nexaflow.delete_task`
- `nexaflow.run_task`
- `nexaflow.get_history`

MCP must remain a thin adapter over the same command/control services.

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

Add a primary AI tab:

```text
Home | Tasks | AI | History | Settings
```

AI contains:

- Chat
- Conversations
- Agents
- Models
- Activity
- Settings

The chat is operational, not informational: it can inspect, create, edit,
enable, disable, run and diagnose automations through the same tools.

Also add:

- home-screen "Ask NexaFlow..." quick command bar
- streaming responses
- cancellation
- bounded tool-loop iterations
- conversation isolation
- optional voice input

## Phase 9 - local model providers

Create a provider abstraction supporting:

- Ollama
- LM Studio
- vLLM
- llama.cpp-compatible servers
- LocalAI
- generic OpenAI-compatible endpoints
- cloud providers through separate adapters where needed

Capability probing records tool calling, structured output, streaming, vision,
reasoning, context size and local/remote status.

A structured-JSON fallback allows weaker local models to request tools without
native function calling.

## Phase 10 - model routing

Modes:

- Automatic
- Local only
- Cloud only
- Selected provider

Routing may consider availability, offline state, privacy policy, capabilities,
task complexity, user preference and cost policy. Local/cloud fallback must be
explicit and deterministic.

## Phase 11 - Android IPC

Add `NexaFlowAgentService` using Binder/AIDL for same-device agents.

Authenticate with calling UID, package, signing certificate, pairing identity
and the persisted grant.

## Phase 12 - NexaFlow Bridge

Desktop bridge commands:

- `nexaflow-agent pair`
- `nexaflow-agent status`
- `nexaflow-agent devices`
- `nexaflow-agent mcp`

Support MCP stdio plus USB/ADB and trusted LAN transport.

## Phase 13 - event subscriptions

Support NexaFlow -> Agent events:

- automation.created/updated/deleted
- automation.triggered/completed/failed
- capability.changed
- device.state.changed
- agent.connected/disconnected

Local agents may wake on events instead of polling continuously.

## Phase 14 - A2A

Publish an agent card and expose automation-management skills through A2A.
Support task delegation and results/events while retaining the command service
as the only mutation authority.

## Phase 15 - remote relay

Use an outbound authenticated encrypted device connection.

Every remote request carries bounded identity/replay fields such as device,
agent, request, timestamp and nonce. Do not expose a public unauthenticated
phone port.

## Phase 16 - advanced agent behavior

After the control/security layers are proven:

- voice-first commands
- replayable redacted traces
- multi-agent delegation
- event-driven diagnosis
- bounded self-healing automation repair

Self-healing must never bypass validation, dry-run or command policy.

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
