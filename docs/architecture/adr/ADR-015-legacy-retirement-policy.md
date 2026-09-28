# ADR-015: Legacy Retirement Policy

**Status:** Accepted  
**Date:** 2026-09-28

## Context

Removing legacy TriggerType/ActionType paths too early would make rollback,
imports or existing automations unsafe. Keeping them forever would preserve the
maintenance burden the migration is intended to remove.

## Decision

Retire legacy runtime paths only after objective evidence is complete. At
minimum:

- 57/57 Trigger mappings reviewed and golden-tested.
- 176/176 Action mappings reviewed and golden-tested.
- **233/233** behavior-parity mappings pass.
- Canonical read/write and controlled migration are stable.
- No silent config loss or unresolved migration cases.
- Critical integration/device matrix passes.
- Security/performance/accessibility gates pass.
- At least one stable release cycle has exercised canonical V3.
- Runtime no longer needs legacy type dispatch for migrated workflows.
- Rollback/import compatibility policy is proven.

Removal is staged: hide legacy creation first, stop canonical runtime dependence
next, then remove dead runtime/UI code. Import adapters may remain longer when
needed to read historical exports.

## Invariants

- Legacy data remains readable until a documented compatibility cutoff.
- No removal in the same change that first introduces a migration.
- A compatibility adapter is not treated as dead code while supported inputs
  still depend on it.
- Retirement requires measured evidence, not only a green compile.

## Consequences

Technical debt is eventually removed without gambling user workflows or
rollback safety.
