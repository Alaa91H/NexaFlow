# ADR-003 — Multi-selection semantics

**Status:** Accepted

## Context

Multi-select is required to simplify the builder, but overloading one ANY/ALL
flag for targets, events, conditions, and execution would create ambiguous
behavior.

## Decision

Keep selection concepts separate:

- `TargetSelectionMode`: SINGLE / MULTI
- `EventLogic`: ANY_OF
- `ConditionLogic`: ANY / ALL
- `ExecutionMode`: SINGLE / BATCH / ORDERED
- `FailurePolicy`: FAIL_FAST / CONTINUE_ON_ERROR / ROLLBACK_WHEN_SUPPORTED

Cardinality is defined per operation, not only per family.

## Invariants

- Mutually exclusive events cannot use ALL.
- A MULTI selection must declare explicit combination/execution semantics.
- A batch cannot contain contradictory writes to the same target.
- Ordered side effects are never silently converted to parallel execution.
- UI must expose the actual runtime semantics.

## Consequences

A single family can safely support rich selection without hiding ambiguity or
creating unpredictable execution.
