# ADR-008 — Execution Planner

**Status:** Accepted

## Context

A grouped UI action can represent several independent or ordered effects. A
handler-level forEach is insufficient for retries, rollback, diagnostics, and
safe parallelism.

## Decision

Compile canonical intent into explicit atomic commands and an execution plan.
The planner declares sequencing, parallel-safe groups, failure policy, retry
eligibility, and rollback/compensation where supported.

## Invariants

- Atomic commands have stable semantic identity.
- Parallel execution occurs only when explicitly proven safe.
- Ordered operations remain ordered.
- A rollback is attempted only for operations declaring reversible support.
- Planner output is deterministic and independently testable.

## Consequences

Multi-select and batching gain predictable runtime semantics and detailed
diagnostics.
