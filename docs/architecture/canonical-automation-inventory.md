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
- T15: **implemented** — 233/233 legacy mapping rules generated into
  `domain/.../canonical/LegacyMappingTable.kt` from the reviewed T01
  inventory via `scripts/generate_legacy_mapping_table.py` (single source of
  truth; CI fails on drift): 57/57 triggers → ObserveNode skeletons over
  registered predicates, 176/176 actions → InvokeNode skeletons over
  registered operations. Generated rules consume no config keys (payloads
  ride along losslessly until family phases add typed upgrades). Every rule
  is validated against the T03 identity registry and pinned idempotent.
  Gate B closed in CI: 57/57 + 176/176 = 233/233. Covered by 7 unit tests
  and CI gate `scripts/check_canonical_legacy_mappings.py`.
- T16: **implemented** — Golden Migration Suite in
  `domain/src/test/.../GoldenMigrationSuiteTest.kt`: a pinned golden
  contract (kind, target, identity) for each of the 233 mappings, payload
  parity (config re-emerges verbatim), whole-table idempotency, the 57/176
  baseline split, serialization round-trips, and a no-unregistered-identity
  sweep.  Gate E closed in CI. Covered by 6 unit tests and CI gate
  `scripts/check_canonical_golden_migration.py`.
- T17: **implemented (pilot family)** — Open Settings per plan §T17 in
  `domain/.../canonical/PilotOpenFamily.kt`: typed value upgrades for all
  41 SYSTEM_OPEN_* actions over the reviewed mappings (strict package/URL
  parsing; page identity stays the reviewed target), parity-pinned against
  the T15 skeletons, single-target cardinality and semantics declared, and
  the family schema (enum-token page allowlist) wired into the T08 engine.
  Drifted tables fail closed; unconsumed keys still ride along losslessly.
  Covered by 11 unit tests and CI gate
  `scripts/check_canonical_pilot_open_family.py`.
- T18: **implemented** — Media & Navigation family in
  `domain/.../canonical/FamilyPhase18MediaNavigation.kt`: typed upgrades for
  the 6 media transport actions + search (optional typed session-package
  filter, required query for PLAY_FROM_SEARCH) and 8 navigation actions
  (optional typed app-package filter), parity-pinned against the T15
  skeletons. Media declares MULTI/ORDERED with max-4 cardinality and
  CONTINUE_ON_ERROR; navigation is SINGLE. The framework gained optional
  consumed keys (`requiredKeys` ⊆ `consumedKeys`) while staying
  fail-closed on required ones. Covered by 11 unit tests and CI gate
  `scripts/check_canonical_family_media_navigation.py`.
- T19: **implemented** — Connectivity family in
  `domain/.../canonical/FamilyPhase19Connectivity.kt`: 17 actions upgraded
  (9 desired-state writes to typed SetState with required boolean, 4 value
  writes, 4 wifi/bluetooth sessions with typed SSID and a SECRET_REFERENCE
  password — raw passwords never enter the AST) and 16 triggers upgraded
  with conditional typed state conditions. MULTI/ORDERED enable semantics
  with CONTINUE_ON_ERROR; contradictory batches rejected by T06. Declared
  provider fallback (public API → Shizuku → Root) resolved through the T07
  resolver. Covered by 12 unit tests and CI gate
  `scripts/check_canonical_family_connectivity.py`.
- T20: **implemented** — Display/Sound/Haptics family in
  `domain/.../canonical/FamilyPhase20DisplaySound.kt`: 33 actions (13
  display booleans, 5 display scalars, 5 sound booleans, 9 sound
  scalars/enums; WAKE_SCREEN keeps its reviewed skeleton) and 11 triggers
  upgraded with typed values. Batch profiles are adjacent typed writes in
  one atomic scope — contradictions rejected by T06, parallel safety proven
  per scope by T10; ringer mode is an enum-token allowlist and brightness a
  bounded percentage. Covered by 14 unit tests and CI gate
  `scripts/check_canonical_family_display_sound.py`.
- T21: **implemented** — Applications family in
  `domain/.../canonical/FamilyPhase21Applications.kt`: 17 actions + 2
  triggers upgraded with typed package/package-list values. The §9.3
  cardinality table is executable per operation (open=SINGLE;
  force-stop/enable bounded MULTI ≤20 with CONTINUE_ON_ERROR; destructive
  capped MULTI ≤5 with FAIL_FAST). Destructive operations declare
  CONDITIONALLY_IDEMPOTENT command semantics (no blind retry, rule 46.14)
  and a DESTRUCTIVE-class schema requiring a capability  declaration (T09 security stage). Covered by 15 unit tests and CI gate
  `scripts/check_canonical_family_applications.py`.
- T22: **implemented** — Notifications/Calls/Communication family in
  `domain/.../canonical/FamilyPhase22Communication.kt`: 14 actions + 4
  triggers upgraded with typed message/number/app-filter values. SEND and
  DIAL declare NON_IDEMPOTENT command semantics (an SMS re-send is a
  duplicate message, never a safe retry — rule 46.14); the SMS schema is
  SENSITIVE-class so message bodies stay out of the journal (T11 sweep);
  sender filters are optional typed package lists (absent = any sender).
  Covered by 13 unit tests and CI gate
  `scripts/check_canonical_family_communication.py`.
- T23: **implemented** — Battery/Power/Sensors/Peripherals family in
  `domain/.../canonical/FamilyPhase23PowerSensors.kt`: 5 power actions + 8
  battery/sensor/peripheral triggers upgraded with typed threshold/state
  semantics (BATTERY carries threshold + charging filters; CHARGER has
  optional typed state; USB/HDMI/Wear stay state-or-event filterable).
  Threshold schema is bounded 0..100 at the schema layer. Covered by 12
  unit tests and CI gate `scripts/check_canonical_family_power_sensors.py`.
- T24: **implemented** — Time/Calendar/Location family in
  `domain/.../canonical/FamilyPhase24TimeLocation.kt`: 5 actions + 6
  triggers upgraded. DST safety is structural: schedules carry wall-clock
  [TimeOfDayValue] plus an explicit [TimezoneValue] (no frozen offsets);
  timers are monotonic [DurationValue]; geofence radii are typed and
  bounded 1..100km with optional enter/exit tokens; timezone-changed stays
  a pure change event. Covered by 14 unit tests and CI gate
  `scripts/check_canonical_family_time_location.py`.
- T25: **implemented** — Data/ROM/Advanced/External family in
  `domain/.../canonical/FamilyPhase25AdvancedExternal.kt`: 28 actions + 1
  plugin trigger upgraded. Privileged root/shizuku commands upgrade their
  raw command text to SECRET_REFERENCE values (never in the AST); HTTP
  auth tokens likewise; data transforms carry typed ExpressionValue
  payloads; wait requires a typed duration. Privileged/destructive
  operations declare CONDITIONALLY_IDEMPOTENT command semantics and the
  HTTP schema is SENSITIVE with a secret-typed token field. Covered by 13
  unit tests and CI gate
  `scripts/check_canonical_family_advanced_external.py`.
- T26: **implemented** — Canonical runtime cutover in
  `domain/.../canonical/CanonicalRuntimePipeline.kt`: the single runtime
  path legacy input → adapter (T14 + T15 + T17–T25 overrides) → canonical
  AST → validation (T09, schema-typed values + declared defaults) →
  execution plan (T10). `planLegacy` fails closed on rejected mappings and
  invalid verdicts (`cutover refused`); the validated surface is the
  schema-typed view of the consumed config while unconsumed legacy keys
  ride losslessly in `preservedConfig` but are never executed. The cutover
  planner merges family-declared command semantics over the default table
  (conflict-checked, fail closed). Page tokens now upgrade to typed
  enum tokens against the schema allowlist, and optional-key rules no
  longer inherit required keys (MapsRule, HttpRule, PowerRule,
  PluginTriggerRule, PrivilegedCommandRule fixed). Covered by 9 unit tests
  (283 total) and CI gate `scripts/check_canonical_runtime_cutover.py`.
- T27: **implemented** — Persistence policy (dual-read / V3-write) in
  `domain/.../workflow/WorkflowPersistencePolicy.kt`: every save persists
  the versioned `WorkflowDocumentV1` row plus the lossless legacy snapshot
  (rollback path); failed V3 preparation degrades to the legacy row with a
  typed warning so a persistence hiccup never loses a user edit; `V3_ONLY`
  is refused until the legacy migration is declared complete (fail closed).
  Reads serve the document as authoritative, merging the snapshot's
  unmodeled fields (`deepLinkToken` never rides the document), with typed
  fallback reasons for corrupt or future-version payloads — never
  fabricated defaults. Pure domain (no Room/clock coupling). Covered by 13
  unit tests and CI gate `scripts/check_canonical_persistence_policy.py`.
- T28: **implemented** — Controlled migration rollout in
  `domain/.../workflow/WorkflowMigrationOrchestrator.kt`: deterministic
  bounded batches (1..200) over the T27 V3-write policy, a durable
  idempotent journal with typed per-id outcomes (MIGRATED /
  DEGRADED_LEGACY_ONLY / FAILED), a per-run failure threshold that aborts
  systematic conversion bugs mid-batch, crash recovery by re-planning from
  the journal (settled ids never replanned, FAILED ids retried), and a
  fail-closed completion gate whose declaration unlocks T27 `V3_ONLY`
  writes. Pure domain; the runner supplies clocks and executes planned
  decisions. Covered by 11 unit tests and CI gate
  `scripts/check_canonical_migration_rollout.py`.

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
