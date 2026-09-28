# ADR-015 — Legacy removal policy

**Status:** Accepted

## Context

Removing legacy paths too early can break saved automations, rollback, imports,
or old backups.

## Decision

Legacy TriggerType/ActionType runtime paths are retired only after canonical
V3 stability is proven. Import adapters may remain longer than execution paths.

## Required evidence before removal

- 57/57 trigger mappings pass.
- 176/176 action mappings pass.
- 233/233 golden migration tests pass.
- Canonical runtime no longer depends on legacy enum semantics.
- Upgrade/migration/device integration tests pass.
- No unresolved migration data-loss defects exist.
- At least one stable release cycle validates V3 compatibility.

## Invariants

- Legacy data is never deleted merely because a canonical equivalent exists.
- Removal commits include explicit compatibility evidence.
- Old imports fail with actionable diagnostics rather than silent corruption.

## Consequences

Cleanup is delayed intentionally in exchange for safer releases and reliable
backward compatibility.
