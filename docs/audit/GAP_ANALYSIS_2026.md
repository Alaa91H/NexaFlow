# NexaFlow Production Gap Analysis — 2026

**Audit date:** 2026-09-04
**Method:** repository source, tests, Gradle modules, CI workflow, resource gates, and existing audit evidence were inspected before proposing implementation work.

## Decision summary

The prompt describes a target platform broader than the current codebase. NexaFlow already has a hardened and released automation runtime, but it would be inaccurate to claim that every target contract is complete. The correct strategy is staged convergence: retain the existing runtime, formalize missing contracts, add adapters around legacy paths, persist only serializable state, and prove each invariant with tests and CI.

| Priority | Gap | Evidence | Status | Required next action |
|---|---|---|---|---|
| P0 | Universal durable execution state machine | Existing lifecycle/occurrence state is strong on audited paths; v3.58.3 adds a canonical `WorkflowExecutionState`/`NodeExecutionState` compatibility adapter and transition tests, but persistence is not yet repository-wide | YELLOW | Persist canonical execution identity and node attempts, then add crash/recovery integration tests |
| P0 | Unknown outcome and side-effect recovery | Strict unknown handling exists in parts of the runtime; full action-wide verification matrix is not proven | YELLOW | Require idempotency, verification, compensation, and irreversibility metadata per executable capability |
| P0 | Cross-store atomicity | Room/DataStore/runtime stores coexist; failure behavior across boundaries needs explicit evidence | YELLOW | Add transaction boundary documentation and crash/failure-injection tests |
| P1 | Capability/backend uniformity | ROM, public Android, privileged, and compatibility paths exist but are not uniformly expressed as one contract | YELLOW | Introduce/complete capability request and backend result adapters without duplicating registries |
| P1 | Security boundary coverage | Safe command construction exists; all imported/plugin/user values need repository-wide taint-style review | YELLOW | Add architecture/security tests for command, package, hostname, settings, and plugin inputs |
| P1 | Immutable workflow revisions | Existing automation definitions and runtime identity are present; immutable revision semantics are not fully evidenced | YELLOW | Add revision identity to execution persistence with migration and compatibility tests |
| P1 | Trigger index/event normalization | Many monitors and trigger paths exist; a single canonical event/index contract is not fully evidenced | YELLOW | Inventory event adapters, normalize payloads, then add deduplication/cooldown invariants |
| P2 | Plugin trust and compatibility | Plugin SDK and sample plugin exist; complete trust lifecycle and quarantine semantics need proof | YELLOW | Specify verification states and compatibility matrix, then test discovery and rejection paths |
| P2 | Observability/replay/metrics | Logging and history exist; full structured trace/replay/metrics contract is not proven | YELLOW | Define correlation/causation fields and durable execution trace schema |
| P2 | Property/model/fuzz/crash testing | Conventional unit tests and CI exist; breadth requested by the prompt is incomplete | YELLOW | Add deterministic model tests for transitions, scheduler storms, retries, and malformed imports |
| GRAY | Universal OEM/device guarantees | Static code and CI cannot prove every Android version, OEM policy, Doze, reboot, or privileged runtime | GRAY | Execute a physical device matrix and record evidence; never convert this into a false software guarantee |

## Confirmed GREEN areas

The resource and translation gates are configured and have passed. The recent strict lifecycle work covers late time-range starts, alarm registration rollback, bounded receiver re-delivery, fixed-location lifecycle ownership, stale occurrence validation, and exit coordination. DNS support is implemented with current-state inspection, ROM-aware provider discovery, validated configuration requests, and truthful read-back. The v3.53.1 correction normalized empty/null non-hostname read-back and passed the tagged CI gates.

## Explicit non-claims

This audit does not claim a complete arbitrary workflow scheduler, universal backend implementation, safe blind retry of irreversible Android operations, or guaranteed execution when Android or an OEM denies the relevant capability. The prompt's desired states are target requirements; they are not evidence that the code already implements them.

## Recommended execution order

First, define the canonical execution-state and node-state contracts as adapters over existing lifecycle records. Second, add durable revision and checkpoint identity without breaking current automation APIs. Third, add capability metadata and verification outcomes to action execution. Fourth, test recovery, idempotency, concurrency, and database failure boundaries. Fifth, enforce architecture/security rules and expand plugin/import validation. Finally, run device-matrix acceptance tests and publish only the capabilities proven by evidence.

## Audit evidence locations

The architecture map is [`CURRENT_ARCHITECTURE_2026.md`](CURRENT_ARCHITECTURE_2026.md). The 2026-09-04 forensic inventory is [`FORENSIC_INVENTORY_2026.txt`](FORENSIC_INVENTORY_2026.txt). The existing lifecycle audit is [`../PRODUCTION_AUDIT.md`](../PRODUCTION_AUDIT.md). DNS platform boundaries are recorded in [`../dns-platform-findings.md`](../dns-platform-findings.md). CI and resource-gate definitions are in `.github/workflows/android-ci.yml` and `scripts/check_resources.py`.
