# ADR-004: Legacy Migration Strategy

**Status:** Accepted  
**Date:** 2026-09-28

## Context

NexaFlow has 57 persisted TriggerType and 176 persisted ActionType values with
saved user configurations. An eager conversion could make rollback impossible
or reinterpret historical defaults.

## Decision

Use staged **dual-read, controlled-write** migration:

1. Read V1/V2 legacy data unchanged.
2. Canonicalize in memory through deterministic adapters.
3. Execute the canonical representation.
4. Write canonical V3 only for new/explicitly saved or controlled migrated data.
5. Preserve enough legacy identity/configuration during the migration window to
   recover or diagnose conversion failures.

There is **no rewrite-on-read**. Migration is one-to-one first; consolidation is
a later, independent optimizer. Unknown fields are retained until their producer
and consumer are understood.

Migration functions must be deterministic and idempotent:
`migrate(migrate(x)) == migrate(x)`.

## Invariants

- 233/233 legacy types require explicit mappings and golden parity tests.
- A failed migration leaves the original durable representation intact.
- Legacy defaults/aliases are modeled explicitly, never guessed from current UI.
- Migration does not merge adjacent actions.
- Optimization cannot run as a hidden side effect of deserialization.
- Downgrade/rollback policy is documented before canonical persistence becomes
  the only writable format.

## Consequences

Rollout can be stopped or reversed without losing existing automations. The cost
is a temporary compatibility layer and additional parity tests, intentionally
accepted in exchange for stability.
