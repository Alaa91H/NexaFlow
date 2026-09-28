# ADR-007 — Capability Resolver

**Status:** Accepted

## Context

The same intent may be executable through platform APIs, Shizuku, Root, OEM
features, or plugins. Embedding fallback chains inside handlers makes behavior
hard to reason about.

## Decision

Represent requirements and providers explicitly. A capability resolver selects
a compatible provider before execution.

Examples of capability dimensions include Android API level, runtime/special
permissions, roles, accessibility, Shizuku, Root, OEM support, hardware, and
plugin availability.

## Invariants

- Provider selection is deterministic for an equivalent capability snapshot.
- Unsupported intent returns CAPABILITY_MISSING/UNSUPPORTED; it never silently
  substitutes a different intent.
- UX reflects real support state.
- Provider-specific details do not leak into the canonical intent model.

## Consequences

Execution becomes portable across device privilege levels while preserving the
same user intent.
