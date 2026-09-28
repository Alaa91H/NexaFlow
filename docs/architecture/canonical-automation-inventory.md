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


## Semantic migration already present at baseline

The existing `SemanticActionMapper` already routes **17 legacy ActionType values**
through the typed semantic capability layer:

- SYSTEM_WIFI
- SYSTEM_BLUETOOTH
- SYSTEM_LOCATION
- SYSTEM_AIRPLANE_MODE
- SYSTEM_SCREEN_ROTATION
- SYSTEM_BRIGHTNESS
- SYSTEM_SCREEN_TIMEOUT
- SYSTEM_DND
- SYSTEM_NFC
- SYSTEM_HOTSPOT
- SYSTEM_MOBILE_DATA
- SYSTEM_DATA_SAVER
- APPLICATION_CLOSE_APP
- SYSTEM_FORCE_STOP_APP
- SYSTEM_CLEAR_APP_DATA
- SYSTEM_DISABLE_APP
- SYSTEM_ENABLE_APP

These mappings are not treated as final canonical-family design automatically;
T01 still verifies aliases, defaults, capability semantics, side effects and
migration parity for each legacy type. They do, however, establish that the
new architecture should evolve the existing semantic operation layer rather
than replace it.


## Current family distribution

This is the source-of-truth distribution from `AutomationNodeCatalog` at the
T00 baseline. Large families are the first duplication hot-spots for later
canonicalization.

### Triggers

| Family | Legacy types |
|---|---:|
| CONNECTIVITY | 17 |
| DEVICE | 15 |
| BATTERY | 4 |
| SCHEDULE | 4 |
| SOUND | 4 |
| COMMUNICATION | 3 |
| APPLICATIONS | 2 |
| LOCATION | 2 |
| DATA | 1 |
| MEDIA | 1 |
| NETWORK | 1 |
| NOTIFICATIONS | 1 |
| PLUGINS | 1 |
| ROM | 1 |

### Actions

| Family | Legacy types |
|---|---:|
| CONNECTIVITY | 25 |
| DISPLAY | 22 |
| APPLICATIONS | 21 |
| SOUND | 15 |
| SYSTEM | 15 |
| NOTIFICATIONS | 12 |
| DATA | 10 |
| ROM | 9 |
| BATTERY | 8 |
| DEVELOPER | 8 |
| MEDIA | 7 |
| SCHEDULE | 6 |
| COMMUNICATION | 5 |
| LOCATION | 4 |
| DEVICE | 3 |
| NETWORK | 3 |
| FILES | 1 |
| FLOW | 1 |
| PLUGINS | 1 |

These counts are descriptive only. They do not authorize merging entries until
their semantics and migration parity have been reviewed.
