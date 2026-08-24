# NexaFlow v3.40.0 - The Automation Supremacy Architecture Upgrade

This release brings the highly anticipated **Phase 2 through 6 Architecture Upgrade**, elevating NexaFlow from a simple sequence runner to a robust, durable, and highly secure automation engine capable of competing with and surpassing industry standards like Tasker, MacroDroid, and Automate.

## Core Architecture Enhancements
* **Event Bus Architecture (Phase 2):** Implemented `NexaFlowEventBus` with advanced Deduplication, Throttling, and filtering policies via `EventDeliveryPolicy`.
* **Capability Resolver System (Phase 2):** Formalized the `CapabilityRequirementResolver` bridging domain concepts securely with legacy ExecutionEngine handlers.
* **Risk Engine & Diagnostics (Phase 3):** Introduced `RiskEngine` to classify capability risks (LOW to CRITICAL) with smart AI escalation pathways, and a highly structured `DiagnosticsSystem` for rich telemetry.
* **Execution Timeline (Phase 3):** Deployed a per-run `ExecutionTimeline` event journal. Every trigger, verification, branch, and failure is now precisely recorded for the Execution Inspector UI.
* **Plugin SDK Expansion (Phase 3):** Formalized `PluginLifecycle` state machine (DISCOVERED -> VALIDATING -> LOADED -> ACTIVE), eliminating untracked arbitrary plugin code execution.

## Reliability & Verification
* **Verification Engine (Phase 4):** Actions are no longer blindly trusted. The new `VerificationEngine` utilizes exponential backoff polling (`verify()`) to guarantee post-conditions (e.g. WiFi state changes) are actually applied by the OS.
* **Idempotent Recovery (Phase 4):** Deterministic `IdempotencyKey` generation ensures that if NexaFlow crashes, critical nodes are not re-executed during exactly-once recovery loops.
* **Safe Dry-Run Simulations (Phase 4):** Added `DryRunExecutor` allowing users (and the AI planner) to safely trace and simulate entire workflows without causing side effects.

## Security & AI Guardrails
* **AI Routine Policy (Phase 5):** `AiSecurityLayer` strictly restricts AI-generated routines. It enforces forced `HumanApprovalNode` injection for sensitive capabilities, bans dangerous capabilities entirely, and limits loop iterations to prevent infinite resource drain.
* **Android Keystore Secrets (Phase 5):** Fully decoupled `SecretVault` and `SecretStore`. Secrets are asynchronously interpolated strictly at execution time via `SecureVariableResolver` without ever leaking into the workflow schema or UI logs.

## Ecosystem & Scale
* **Marketplace Cryptography (Phase 6):** `WorkflowManifest` validation introduced. Workflows are now bundled with cryptographic SHA-256 checksum payloads and version constraints.
* **Distributed Mesh (Phase 6):** Groundwork laid for `RemoteNodeEndpoint` and `DistributedExecutionRouter`, paving the way for multi-device workflow execution (e.g. triggering an action on a tablet from your phone).

### Technical Notes
> No existing automations were broken. The legacy `ExecutionEngine` continues to run in parallel while all internal pipelines have been upgraded to support the new capability contracts.
