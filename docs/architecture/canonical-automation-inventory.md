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
- T01: **closed**
- Source-derived inventory coverage: **233/233**
- Semantic review coverage: **233/233 REVIEWED**
- Trigger coverage: **57/57**
- Action coverage: **176/176**
- Existing semantic-router parity pinned: **17/17**
- CI evidence: workflow run **#735** (`36395436428`) — lint, semantic-review invariants, runtime-contract audit, coverage, and build all passed.
- T05: **implemented** — typed selection/execution semantics per ADR-003 in
  `domain/.../canonical/SelectionSemantics.kt`: `TargetSelectionMode`,
  `EventLogic` (ANY_OF only by design), `ConditionLogic`, `ExecutionMode`,
  `FailurePolicy`, per-operation cardinality, and a fail-closed validator
  (`validateSelectionSemantics` / `requireValidSelectionSemantics`) covering
  missing execution semantics, missing failure policy, cardinality violations,
  contradictory batch writes, and unverifiable expression batch writes.
  Covered by 13 unit tests and CI gate
  `scripts/check_canonical_selection_semantics.py`.
- T06: **implemented** — Semantic Rules Engine per plan §10 in
  `domain/.../canonical/NodeSemanticRules.kt`. Registry-backed rules over
  stable predicate/operation identities: contradictory state assertions
  (§10.1, §46.12), event-ALL exclusivity with fail-closed proof requirement
  (§8.2, Gate D), duplicate conflicting writes inside one atomic batch scope
  with adjacency mirroring plan §29 (§10.2/§10.9), and typed write-payload
  requirements (§10.4).  Unknown declarations fail closed. Covered by 21 unit
  tests and CI gate `scripts/check_canonical_semantic_rules.py`.
- T07: **implemented** — Capability Graph & Resolver per plan §7 / ADR-007 in
  `domain/.../canonical/CapabilityGraph.kt`: serializable provider catalog
  reusing the established capability vocabulary (`CapabilityBackendId`,
  `PrivilegeLevel`, `StrategyId`, `DeviceFeature`), explicit
  `CapabilitySelectionPolicy` (allow/prefer/pin/privileged opt-in), a pure
  deterministic `CanonicalCapabilityResolver` (pin > prefer > least privilege
  > stable id), and fail-closed handling of unknown operations, unobserved
  backends, missing hardware and ungranted privileges. Unsupported intents
  stay `UNSUPPORTED`/`PENDING_USER_ACTION` with per-provider exclusion
  reasons — never a silent intent substitution. Covered by 17 unit tests and
  CI gate `scripts/check_canonical_capability_resolver.py`.
- T08: **implemented** — Dynamic Schema Engine per plan §11 / ADR-005 in
  `domain/.../canonical/NodeSchema.kt`: typed `NodeSchemaField` descriptors
  (14 field kinds mapped 1:1 onto canonical value kinds), declared defaults
  through the only default-producing factory/`defaultsOf` pair (no hidden
  runtime defaults), conditional visibility/requirement, declared conflicts,
  capability linkage, security classes, bounded summary templates with
  optional groups and secret masking, and a uniqueness-enforcing
  `NodeSchemaRegistry` keyed by target+operation/predicate. Fail-closed
  validation covers unknown fields, invisible supplied fields, type
  mismatches, bounds, enum allowlists, and missing required fields. Covered
  by 17 unit tests and CI gate `scripts/check_canonical_schema_engine.py`.
- T09: **implemented** — Canonical Validation Pipeline in
  `domain/.../canonical/CanonicalValidationPipeline.kt`: six ordered,
  fail-closed stages (Syntax → Type → Schema → Semantic → Capability →
  Security); findings are pinned to a stage and a stable rule name, and the
  closure condition is structural: an invalid verdict cannot reach the
  planner through this API. Security stage enforces capability declarations
  for HIGH_RISK/DESTRUCTIVE classes and keeps secret references inside
  secret-typed fields only. Covered by 9 unit tests and CI gate
  `scripts/check_canonical_validation_pipeline.py`.
- T10: **implemented** — Execution Planner per plan §17 / ADR-008 in
  `domain/.../canonical/ExecutionPlanner.kt`: compiles validated canonical
  ASTs into deterministic atomic commands with declared semantics
  (idempotency/reversibility; undeclared operations abort planning), groups
  adjacent actions per the §29 adjacency, marks a group parallel only when
  the T06 rules prove it conflict-free, enforces policy gates
  (BEST_EFFORT⇒CONTINUE_ON_ERROR, TRANSACTIONAL⇒ROLLBACK_WHEN_SUPPORTED +
  all-side-effecting-commands-reversible), and produces reverse-rank
  compensations. `planValidated` refuses invalid T09 verdicts, materializing
  the closure rule. Covered by 12 unit tests and CI gate
  `scripts/check_canonical_execution_planner.py`.
- T11: **implemented** — Unified Error Model & Execution Journal per plan
  §21–§23 in `domain/.../canonical/ExecutionJournal.kt`: the §21 error-code
  vocabulary, the six-phase run lifecycle, `TriggerEvaluation` provenance and
  `SkipReason` for the Why-didn't-it-run diagnostics, plus
  `validationBlockedRun`/`capabilityBlockedRun` builders that materialize T09
  verdicts and T07 exclusions into a journal record. The journal is secret-
  safe by construction: secret-looking metadata keys/values or messages are
  rejected at construction (Gate I groundwork). Covered by 11 unit tests and
  CI gate `scripts/check_canonical_execution_journal.py`.
- T12: **implemented (infrastructure core)** — the schema-driven
  configurator state machine in `domain/.../canonical/NodeConfiguratorState.kt`:
  dynamic tabs derived from the NodeSchema, progressive disclosure
  (Basic/Advanced/Expert), declared-default seeding, live T08 validation,
  missing-required surfacing, bounded multi-select state with deterministic
  search/filter/sort/count for large lists, and shared summary rendering.
  Pure and UI-independent: any Compose/platform shell renders it; per-family
  configurator UIs become unnecessary. Covered by 15 unit tests and CI gate
  `scripts/check_canonical_configurator.py`. The Compose sheet host (render
  wiring) is a UI follow-up on top of this contract.
- T13: **implemented** — Central Summary Engine at workflow level in
  `domain/.../canonical/WorkflowSummaryEngine.kt`: one deterministic
  summary (trigger + conditions with declared logic + action lines +
  semantics line, plan §13 shape) reused identically by builder, templates,
  history, import preview and diagnostics; per-node lines come from the T08
  formatter and locale-neutral tokens keep user copy in the resource layer.
  Covered by 7 unit tests and CI gate
  `scripts/check_canonical_summary_engine.py`.
- T14: **implemented** — Legacy Adapter Framework per plan §26 / ADR-004 in
  `domain/.../canonical/LegacyCanonicalAdapter.kt`: explicit rule table
  (kind+legacyType keyed, duplicates rejected), strict typed value parsers
  (no silent coercion), lossless preservation of unconsumed config keys,
  fail-closed rejections (unknown type / missing config / unparsable value /
  invalid output), and idempotent deterministic canonicalization. Rules are
  supplied by T15 mapping data; the framework itself is legacy-agnostic.
  Covered by 11 unit tests and CI gate
  `scripts/check_canonical_legacy_adapter.py`.

T01 is closed. No T02 implementation may redefine these legacy meanings without
an explicit reviewed change to the semantic inventory contract.


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
