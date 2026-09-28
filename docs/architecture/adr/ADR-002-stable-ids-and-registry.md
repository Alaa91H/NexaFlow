# ADR-002 — Stable IDs and Registry

**Status:** Accepted

## Context

Persisting Kotlin enum/class names couples saved automations to implementation
details and makes renames risky.

## Decision

Canonical targets, operations, predicates, capabilities, and providers use
stable string IDs owned by registries, for example
`core.connectivity.wifi` and `core.system.settings`.

Registry entries define identity and supported semantic contracts. Kotlin class
names are implementation details only.

## Invariants

- Stable IDs are unique and immutable after release.
- Duplicate IDs fail tests/CI.
- Renaming a class must not alter persisted identity.
- Removing a released ID requires an explicit compatibility mapping.
- Unknown IDs fail closed unless an explicitly registered compatibility
  provider understands them.

## Consequences

Registries become the extension point for capabilities. New functionality should
normally add a target/operation/option rather than a legacy enum case.
