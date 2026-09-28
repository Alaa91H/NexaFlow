# Canonical automation inventory — T01

T01 is the behavior and migration inventory for every persisted automation node.
It is deliberately separated from the architectural implementation so no legacy
behavior is guessed during migration.

## Current totals

| Kind | Count | Coverage target |
|---|---:|---:|
| TriggerType | 57 | 57/57 |
| ActionType | 176 | 176/176 |
| Total | 233 | 233/233 |

## Existing foundations discovered during inventory

The repository already has a partial semantic execution architecture. The
canonical migration must extend these components instead of creating competing
abstractions:

- `domain/.../catalog/AutomationNodeCatalog.kt`: exhaustive legacy family map.
- `domain/.../catalog/ActionNodeSchemas.kt`: action configuration contracts.
- `domain/.../catalog/TriggerNodeSchemas.kt`: trigger configuration contracts.
- `core/execution/.../capability/semantic/OperationRegistry.kt`: typed semantic
  operations for an initial subset of device state/package operations.
- `SemanticWorkflowPlanner.kt`: pre-side-effect semantic planning.
- `SemanticActionRouter`: semantic action routing.
- `ActionRegistry.kt`: legacy handler ownership and collision detection.
- `WorkflowDocumentCompiler.kt`: workflow compilation infrastructure.
- `scripts/check_node_contracts.py`: runtime→schema config contract audit.

## Required inventory fields

Every one of the 233 entries must eventually have all columns below populated
and reviewed before T01 may close:

| Field | Meaning |
|---|---|
| legacyType | Persisted TriggerType/ActionType name |
| kind | TRIGGER / ACTION |
| currentFamily | Current AutomationNodeCatalog family |
| schemaKeys | Declared persisted configuration keys |
| runtimeKeys | Keys actually read at runtime |
| runtimeOwner | Handler/monitor/matcher that owns behavior |
| semantics | Event / State / Threshold / Transition / Invoke / SetState / SetValue / etc. |
| canonicalTarget | Stable target id |
| canonicalOperation | Stable operation/predicate id |
| selectionMode | SINGLE / MULTI |
| combinationMode | ANY_OF / ANY / ALL / BATCH / ORDERED / N/A |
| capabilityRequirements | Permission/API/Root/Shizuku/hardware requirements |
| sideEffect | NONE / REVERSIBLE / IRREVERSIBLE / EXTERNAL |
| idempotency | IDEMPOTENT / NON_IDEMPOTENT / CONDITIONAL |
| retrySafety | SAFE / UNSAFE / CONDITIONAL |
| migrationNotes | Compatibility aliases/defaults/edge cases |
| goldenTestId | Mandatory migration parity test id |
| reviewStatus | UNREVIEWED / REVIEWED / BLOCKED |

## T01 rules

1. Existing semantic operations count as evidence but do not automatically mark
   a legacy type REVIEWED.
2. No canonical mapping is accepted from a name-only heuristic when runtime
   behavior disagrees with the name.
3. Unknown/dynamic config is preserved until its producer and consumer are
   explicitly understood.
4. Legacy migration is one-to-one first. Consolidation is a later optimizer.
5. T01 closes only when all 233 rows are REVIEWED and no behavior owner is
   unknown.

## Status

- T00: **closed**
- T01: **in progress**
- Full mapping coverage: pending source-derived inventory generation/review.
