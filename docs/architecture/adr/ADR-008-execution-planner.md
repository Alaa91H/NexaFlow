# ADR-008: Execution Planner

**Status:** Accepted  
**Date:** 2026-09-28

## Context

A grouped UI node may represent several atomic effects. Directly looping inside
a family handler would hide ordering, failure policy, capability preflight and
retry semantics.

## Decision

Extend the existing `SemanticWorkflowPlanner` concept into the canonical
planning path. A planner converts validated canonical intent into an explicit
side-effect-free plan of atomic commands before execution.

The plan records command order/dependencies, selected or admissible capability
routes, execution mode, failure policy, verification requirement, idempotency,
retry safety and compensation support. Existing `WorkflowInterpreter`
Sequence/Parallel/Rollback constructs execute the resulting structure rather
than a new scheduler.

Parallel execution is allowed only for commands explicitly marked independent
and parallel-safe. Otherwise the default is deterministic sequence.

## Invariants

- Planning never executes a side effect.
- The plan is deterministic for the same canonical input and capability snapshot.
- BATCH and ORDERED remain distinguishable.
- FAIL_FAST/CONTINUE/ROLLBACK behavior is explicit.
- UNKNOWN outcomes prevent unsafe subsequent retry/fallback.
- Replanning immediately before privileged side effects remains allowed when
  environment state changed.

## Consequences

Multi-select remains transparent and diagnosable; grouped UX does not weaken
runtime correctness.
