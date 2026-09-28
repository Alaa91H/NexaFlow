# ADR-004 — Legacy migration strategy

**Status:** Accepted

## Context

There are 57 TriggerType and 176 ActionType values in released persistence.
Direct rewrite-on-read would make rollback risky and could hide data loss.

## Decision

Migration proceeds as:

`Legacy read -> in-memory canonicalization -> canonical validation/execution`

New canonical persistence is introduced only through controlled V3 writes.
Migration is one-to-one before any optimizer groups nodes.

## Invariants

- Migration is deterministic and idempotent.
- Unknown legacy fields are preserved until explicitly retired.
- Failed migration leaves the original data intact.
- Migration never performs opportunistic consolidation.
- Every legacy type has a reviewed mapping and golden test before retirement.
- Legacy meaning cannot change silently.

## Consequences

Compatibility and optimization are separate workstreams. Rollback remains
possible during the transition.
