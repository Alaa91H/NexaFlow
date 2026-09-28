# ADR-013: Versioning Policy

**Status:** Accepted  
**Date:** 2026-09-28

## Context

The current `Automation.workflowVersion` covers trigger semantics. Canonical
storage, node schemas, capability contracts and plugins evolve at different
rates; one integer cannot safely version all of them.

## Decision

Keep the current workflowVersion compatibility meaning and introduce explicit
version axes as needed:

- Workflow schema version
- Canonical node schema version
- Migration version
- Capability/operation contract version
- Plugin/provider contract version
- Execution-plan format version when persisted

Stable IDs identify semantics; versions identify compatible representation/
contract revisions. Readers reject unsupported future versions rather than
guessing. Migrations are ordered, pure where possible, deterministic and tested.

## Invariants

- Existing V1/V2 data keeps its historical interpretation.
- Version bumps accompany semantic/serialization contract changes, not cosmetic
  refactors.
- Unknown future versions fail closed and preserve source payload.
- Stable IDs are not renamed to simulate a version change.
- Import/export records enough version metadata for deterministic migration.

## Consequences

Different subsystems can evolve independently without overloading one global
version or silently changing saved workflow meaning.
