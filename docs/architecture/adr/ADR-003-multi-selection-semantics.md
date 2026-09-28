# ADR-003: Multi-selection Semantics

**Status:** Accepted  
**Date:** 2026-09-28

## Context

Combining several targets/events can simplify the builder, but one generic
"multi select + ANY/ALL" flag is ambiguous and can express impossible states.

## Decision

Model selection and combination as separate typed concepts:

- `TargetSelectionMode`: SINGLE | MULTI
- `ConditionLogic`: ANY | ALL
- `EventLogic`: ANY_OF
- `ExecutionMode`: SINGLE | BATCH | ORDERED
- `FailurePolicy`: FAIL_FAST | CONTINUE_ON_ERROR | ROLLBACK_WHEN_SUPPORTED

Schema/semantic rules explicitly declare which modes an operation supports.
Mutually exclusive states cannot use ALL. Multi-target operations require an
explicit BATCH/ORDERED meaning. A single canonical node may contain multiple
independent target operations only when the execution planner can preserve the
declared semantics.

## Invariants

- UI never enables MULTI merely because a widget supports checkboxes.
- ALL is illegal for mutually-exclusive event alternatives.
- A batch cannot write conflicting values to the same target.
- Ordered operations remain ordered through compilation.
- Target selection, condition logic and failure policy are never conflated.
- Existing legacy plurality (for example comma-separated package lists) is
  preserved during one-to-one migration before any optimization.

## Consequences

The builder can offer compact multi-selection without hiding behavioral changes.
Semantic validation can reject impossible or unsafe combinations before runtime.
