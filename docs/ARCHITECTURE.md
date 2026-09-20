# Architecture

## Module boundaries

- `domain`: serializable task/action/trigger models, workflow validation, retry policy, bounded data transforms and shared external-access policy. No UI-specific state belongs here.
- `data`: repositories, database mapping and backup/single-task import/export. All external imports pass bounded preflight before persistence.
- `core/database`: Room schema 19 and explicit migrations.
- `core/automation-engine`: foreground monitoring, scheduling and platform event adapters, including authenticated local webhooks and sensor listeners.
- `core/execution`: admission, action dispatch, run context, exit behavior, capability routing and transport implementations. `ActionRegistry` maps action types to handlers. The semantic layer (`core/execution/capability/semantic`) owns the single execution decision for migrated device-state operations: `OperationRegistry` declares typed `OperationSpec` contracts, and `CapabilityRouter` selects a concrete strategy with explainable, evidence-aware ranking.
- `core/rom-integration`, compatibility/capability modules: device probing and normal/Shizuku/root execution providers. An enum entry does not imply that a provider is available.
- `feature/*`: Compose screens and view models. The automation builder exposes configuration and aggregates permission requirements. Task details owns external-link enable/rotate/revoke controls.
- `app`: Hilt composition, manifest entry points, navigation, platform integration and app-level integration tests.

## Execution path

A platform monitor identifies candidates, applies event-specific matching and cooldown behavior, then invokes the execution engine. Admission and capability checks determine whether configured work can proceed. The registry resolves a handler; results are recorded and optional outputs are published in the per-run context. Stateful triggers can invoke exit/revert handling when their condition ends. Manual and force-run entry points have distinct admission policies; external links require confirmation and reauthorization.

`WorkflowRunContext` provides a bounded JSON-like document with a supported JSONPath subset. Data actions use explicit input/output paths. It is a per-run exchange, not a durable database. Not every exit path carries this context, so context-dependent actions fail explicitly when it is unavailable.

## Capability-Adaptive Execution

Migrated device-state actions flow through one decision path:

```
Action → SemanticActionMapper (strict typed parsing)
       → CapabilityRouter
       → OperationRegistry.lookup(SemanticOperationId)
       → StrategyCandidate ranking (availability, evidence, health, least privilege)
       → Strategy execution (typed operations only; never workflow-supplied shell text)
       → Post-condition read-back verification
       → Evidence + health recording
```

Routing rules, in order: operation compatibility; privilege eligibility (Shizuku/root strategies require explicit policy opt-in); user preference; live availability; previously verified evidence on the same device fingerprint; health cooldown after repeated failures; confidence; least privilege. Root is never selected merely because it is available.

Failure semantics: a transport-level failure may advance to the next candidate; an `UNKNOWN` outcome (a transport timeout after a side effect may have occurred) is reconciled by reading the actual device state through the operation's paired GET — never retried blindly; a contradicted post-condition fails with `VERIFICATION_FAILED`. See [capability-adaptive-execution](architecture/capability-adaptive-execution.md) for the full contract, and the parity gates in `OperationRegistryParityTest` that keep the registry truthful.

## Adding capabilities

An action requires a domain enum, registered production handler, compatibility specification, builder catalog/configuration, presentation resources and meaningful tests. A trigger requires a real event source or monitor, matching semantics, lifecycle registration, compatibility/permission behavior and editor configuration. Update the generated catalog and user documentation. Do not add placeholder enum values to increase counts.

Security decisions shared by runtime and UI belong in a common policy. Never substitute a helper-only unit test for verifying the actual entry point that uses it. See [required checks](REQUIRED_CHECKS.md).
