# Architecture

## Module boundaries

- `domain`: serializable task/action/trigger models, workflow validation, retry policy, bounded data transforms and shared external-access policy. No UI-specific state belongs here.
- `data`: repositories, database mapping and backup/single-task import/export. All external imports pass bounded preflight before persistence.
- `core/database`: Room schema 19 and explicit migrations.
- `core/automation-engine`: foreground monitoring, scheduling and platform event adapters, including authenticated local webhooks and sensor listeners.
- `core/execution`: admission, action dispatch, run context, exit behavior, capability routing and transport implementations. `ActionRegistry` maps action types to handlers.
- `core/rom-integration`, compatibility/capability modules: device probing and normal/Shizuku/root execution providers. An enum entry does not imply that a provider is available.
- `feature/*`: Compose screens and view models. The automation builder exposes configuration and aggregates permission requirements. Task details owns external-link enable/rotate/revoke controls.
- `app`: Hilt composition, manifest entry points, navigation, platform integration and app-level integration tests.

## Execution path

A platform monitor identifies candidates, applies event-specific matching and cooldown behavior, then invokes the execution engine. Admission and capability checks determine whether configured work can proceed. The registry resolves a handler; results are recorded and optional outputs are published in the per-run context. Stateful triggers can invoke exit/revert handling when their condition ends. Manual and force-run entry points have distinct admission policies; external links require confirmation and reauthorization.

`WorkflowRunContext` provides a bounded JSON-like document with a supported JSONPath subset. Data actions use explicit input/output paths. It is a per-run exchange, not a durable database. Not every exit path carries this context, so context-dependent actions fail explicitly when it is unavailable.

## Adding capabilities

An action requires a domain enum, registered production handler, compatibility specification, builder catalog/configuration, presentation resources and meaningful tests. A trigger requires a real event source or monitor, matching semantics, lifecycle registration, compatibility/permission behavior and editor configuration. Update the generated catalog and user documentation. Do not add placeholder enum values to increase counts.

Security decisions shared by runtime and UI belong in a common policy. Never substitute a helper-only unit test for verifying the actual entry point that uses it. See [required checks](REQUIRED_CHECKS.md).
