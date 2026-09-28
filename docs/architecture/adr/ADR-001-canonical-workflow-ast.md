# ADR-001 — Canonical Workflow AST

**Status:** Accepted

## Context

Legacy automation persistence is centered on `TriggerType`, `ActionType`, and
string configuration maps. That is a compatibility surface, but it is not a
safe long-term extension model because every small capability can create a new
enum case and duplicated UI/runtime branches.

## Decision

Introduce a typed canonical workflow representation as the semantic center of
the system. UX families are presentation only. Runtime intent is represented by
typed primitives such as `SetState`, `SetValue`, `Invoke`, `Open`,
`Send`, `Transform`, `Observe`, `Compare`, `Batch`, `Sequence`,
`Branch`, `Wait`, and `Restore`.

Legacy nodes must cross a compatibility adapter before entering the canonical
compiler/runtime path.

## Invariants

- Canonical nodes do not depend on Compose.
- Canonical nodes do not depend on legacy enum names for identity.
- Typed values replace free-form string maps in the canonical core.
- UX family names must not affect runtime semantics.
- Canonicalization must preserve user intent exactly.

## Consequences

Legacy models remain readable during migration, but all new architecture is
built around the canonical AST. This enables stable compilation, validation,
planning, testing, and future provider extensions without enum growth.
