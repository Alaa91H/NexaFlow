# ADR-006 — Workflow Compiler

**Status:** Accepted

## Context

A validator alone cannot guarantee that structurally valid workflow input is
type-correct, semantically coherent, capability-compatible, and safe to plan.

## Decision

Canonical workflows pass through a compiler pipeline:

`Parse -> Normalize -> Type Check -> Schema Check -> Semantic Check ->
Capability Check -> Security Check -> Plan`

Existing workflow compilation infrastructure is extended rather than replaced
with a competing engine.

## Invariants

- Invalid input never reaches side-effect execution.
- Compiler stages fail closed with structured errors.
- Compilation is deterministic for the same workflow/capability snapshot.
- Runtime handlers do not compensate for compiler-invalid configuration.
- Optimizations must preserve observable semantics.

## Consequences

The execution boundary becomes explicit and testable. Runtime code receives a
validated plan rather than loosely interpreted configuration.
