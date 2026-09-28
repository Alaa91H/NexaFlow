# ADR-013 — Versioning policy

**Status:** Accepted

## Context

One global workflow version is insufficient once canonical node schemas,
capability contracts, plugins, and execution plans evolve independently.

## Decision

Version compatibility dimensions separately:

- WorkflowSchemaVersion
- NodeSchemaVersion
- MigrationVersion
- CapabilityContractVersion
- PluginContractVersion
- ExecutionPlanVersion

Stable IDs remain independent of implementation version numbers.

## Invariants

- Readers explicitly declare supported version ranges.
- Unsupported future versions fail closed.
- Migrations are monotonic and tested.
- Version increments have documented compatibility meaning.
- A version bump never substitutes for a migration test.

## Consequences

Independent subsystems can evolve without forcing unrelated persistence changes.
