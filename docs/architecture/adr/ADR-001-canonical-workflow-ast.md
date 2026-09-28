# ADR-001: Canonical Workflow AST

**Status:** Accepted  
**Date:** 2026-09-28

## Context

Persisted automations still encode leaf behavior as legacy `TriggerType` and
`ActionType` plus string maps. The repository already has a production runtime
tree in `WorkflowNode`, a versioned workflow document/compiler, typed runtime
values, and a semantic capability layer. Creating another workflow engine would
duplicate safety, cancellation, rollback and recovery behavior.

## Decision

Introduce a **typed canonical semantic representation** between persisted/user
intent and the existing runtime graph. Canonical nodes describe *what* the user
means using stable target, operation/predicate, typed parameters, selection
semantics, execution policy and capability requirements.

The canonical AST is compiled into the existing `WorkflowNode` /
`WorkflowInterpreter` runtime. It is not an interpreter and contains no Android
objects, handlers, Binder references, shell text, Compose types, or provider
implementations.

Legacy actions/triggers enter through compatibility adapters. New canonical
storage will be additive and versioned; legacy storage remains readable.

## Invariants

- `WorkflowInterpreter` remains the single control-flow runtime.
- A canonical leaf is platform-independent semantic intent.
- UI family/category names never become runtime dispatch identifiers.
- Typed values replace new stringly-typed contracts; compatibility adapters may
  still read legacy maps.
- Unknown or invalid intent fails closed before side effects.
- Canonicalization is deterministic and side-effect-free.

## Consequences

Existing Sequence/Parallel/Branch/Retry/Timeout/Rollback behavior is reused.
Migration can proceed leaf-by-leaf without a big-bang runtime rewrite. Additional
normalization/type/semantic passes may be introduced before compilation without
changing the execution engine.
