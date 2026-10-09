# Advanced Trigger Expression v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a schema-validated, bounded trigger-expression v2 with temporal event matching, durable minimal history, exact runtime/preview parity, and unchanged legacy ANY/ALL behavior.

**Architecture:** Keep absent v2 expression on the existing runtime path. Add a sealed AST and pure three-valued evaluator, persist only bounded event metadata keyed by a monotonic workflow revision, and make the builder preview call the exact runtime evaluator. Room and canonical payload versions advance compatibly; v1/v2 legacy workflows and backups read with no expression.

**Tech Stack:** Kotlin, kotlinx.serialization, Room 2.7, Preferences DataStore, Android Keystore HMAC, Jetpack Compose, JUnit/Robolectric, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-09-trigger-expression-v2-design.md`

## Global Constraints

- Expression envelope schema is exactly 2; invalid or unknown versions never fall back to ANY/ALL.
- AST bounds are 64 nodes, depth 8, window 1..604800000 ms, and count 1..1000.
- History bounds are 128 workflows, 4096 records, and 262144 serialized bytes, with seven-day TTL.
- Temporal duration comparisons use monotonic elapsed realtime; wall time remains reserved for schedules.
- No raw event payload, source identifier, phone number, notification content, or event ID may be persisted or logged.
- Existing definitions with no expression retain the current runtime evaluator and ANY/ALL semantics.
- NOT is supported only through `NotState(triggerIndex)` and must reject event-only trigger types.
- Import and builder save reject invalid expressions before any persistent mutation.

## Review Focus

- An event is repeated or replayed after process death: a stable keyed occurrence digest admits it at most once within its retention window.
- Android reboots while history is persisted: elapsed-clock regression clears history before any temporal match.
- A trigger permission is revoked while a sequence is pending: the affected history is cleared and evaluation remains unavailable/unknown.
- Trigger order/config changes while an editor preview is open: the workflow revision changes and the stale preview cannot be saved or reused.
- A backup contains an unsupported expression schema or deeply nested expression: preflight rejects it within bounded parsing cost and preserves all installed automations.

---

### Task 1: Domain AST, validator, evaluator, and workflow revision

**Files:**
- Create: `domain/src/main/java/com/nexaflow/domain/workflow/TriggerExpressionV2.kt`
- Modify: `domain/src/main/java/com/nexaflow/domain/models/Automation.kt`
- Modify: `domain/src/main/java/com/nexaflow/domain/workflow/WorkflowValidator.kt`
- Modify: `domain/src/test/java/com/nexaflow/domain/workflow/WorkflowValidatorTest.kt`
- Create: `domain/src/test/java/com/nexaflow/domain/workflow/TriggerExpressionV2Test.kt`

**Interfaces:**
- `Automation.triggerExpressionV2: TriggerExpressionDefinitionV2? = null` and `Automation.workflowRevision: Long = 1L`.
- `TriggerExpressionNodeV2` sealed serializable node types: `State(index)`, `Event(index)`, `AllOf(children)`, `AnyOf(children)`, `NotState(index)`, `Sequence(firstIndex, secondIndex, withinMs)`, `Count(index, minimumCount, withinMs)`.
- `TriggerExpressionValidator.validate(definition, triggers): List<TriggerExpressionIssue>` returns stable codes and bounded paths; an empty list means valid.
- `TriggerExpressionEvaluatorV2.evaluate(input): TriggerExpressionEvaluation` accepts an immutable definition, trigger list, current event indices, tri-state live-read map, monotonic elapsed time, and sanitized history records; it has no Android or execution-module dependency.

- [ ] **Step 1: Add failing domain tests** for valid `(A AND B) OR C`, invalid state/event reference kinds, NOT over event, duplicate/out-of-range references, empty groups, unsupported versions, depth 9, 65 nodes, seven-day-plus-one window, and count 0/1001.
- [ ] **Step 2: Run `./gradlew :domain:testDebugUnitTest --tests '*TriggerExpressionV2Test' --tests '*WorkflowValidatorTest'` and confirm the new tests fail for absent types/validation.**
- [ ] **Step 3: Implement the serializable AST and pure validator** with the exact bounds in the spec; preserve `null` as the existing legacy mode.
- [ ] **Step 4: Add pure evaluator truth-table tests** for nested groups, current-occurrence-only Event leaves, Unknown/Unavailable/Error propagation, ordered sequence boundaries, count expiry, duplicate event input, and wall-clock independence.
- [ ] **Step 5: Run `./gradlew :domain:testDebugUnitTest --tests '*TriggerExpressionV2Test' --tests '*WorkflowValidatorTest'` and verify PASS.**
- [ ] **Step 6: Commit** as `feat(triggers): add validated expression v2 domain model`.

### Task 2: Versioned persistence, workflow revisions, and bounded temporal history

**Files:**
- Modify: `core/database/src/main/java/com/nexaflow/core/database/AutomationEntity.kt`
- Modify: `core/database/src/main/java/com/nexaflow/core/database/AutomationDao.kt`
- Modify: `core/database/src/main/java/com/nexaflow/core/database/AppDatabase.kt`
- Modify: `core/database/src/main/java/com/nexaflow/core/database/Migrations.kt`
- Modify: `core/database/src/main/java/com/nexaflow/core/database/Converters.kt`
- Modify: `core/database/schemas/com.nexaflow.core.database.AppDatabase/28.json` (generate with Room schema export)
- Create: `core/datastore/src/main/java/com/nexaflow/core/datastore/TriggerExpressionHistoryStore.kt`
- Create: `core/datastore/src/test/java/com/nexaflow/core/datastore/TriggerExpressionHistoryStoreTest.kt`
- Modify: `core/datastore/build.gradle.kts`
- Create: `core/security/src/main/java/com/nexaflow/core/security/OccurrenceIdentityHmac.kt`
- Modify: `app/src/main/java/com/nexaflow/app/di/AppModule.kt`
- Modify: `data/src/main/java/com/nexaflow/data/mapper/AutomationMapper.kt`
- Modify: `data/src/main/java/com/nexaflow/data/repository/AutomationRepositoryImpl.kt`
- Modify: `data/src/main/java/com/nexaflow/data/repository/RoomAutomationMutationPersistence.kt`
- Modify: `domain/src/main/java/com/nexaflow/domain/canonical/CanonicalWorkflowDocumentV3.kt`
- Modify: `data/src/main/java/com/nexaflow/data/mapper/CanonicalWorkflowV3ReadMapper.kt`
- Modify: `domain/src/main/java/com/nexaflow/domain/workflow/WorkflowPersistencePolicy.kt`
- Test: `core/database/src/test/java/com/nexaflow/core/database/MigrationTest.kt`
- Test: `data/src/test/java/com/nexaflow/data/mapper/AutomationMapperTest.kt`
- Test: `data/src/test/java/com/nexaflow/data/repository/RepositoryImplTest.kt`
- Test: `data/src/test/java/com/nexaflow/data/repository/RoomAutomationMutationPersistenceTest.kt`
- Test: `domain/src/test/java/com/nexaflow/domain/workflow/WorkflowPersistencePolicyTest.kt`

**Interfaces:**
- `AutomationEntity.workflowRevision: Long = 1L` and `triggerExpressionJson: String? = null`.
- Room 27→28 adds those columns with safe defaults; DAO save/CAS/import increments definition revision transactionally, while enable/disable-only updates retain it.
- `TriggerExpressionHistoryStore.recordAndRead(automationId, workflowRevision, eventIndices, elapsedMs, sourceId, stableEventId): HistoryResult` uses `sourceId` only as HMAC input, performs one atomic bounded update, and returns trigger-index/elapsed-time records or a typed reset/unavailable result. The execution adapter maps those records to the domain evaluator input.
- `TriggerExpressionHistoryStore.clearAutomation(id)` and `clearSourcePermissionScope(...)` remove affected records.
- `OccurrenceIdentityHmac.digest(stableEventId): String` returns a Keystore-keyed digest; tests inject a fake implementation.
- `CanonicalWorkflowDocumentV3.schemaVersion=5` stores the optional expression; schemas 3 and 4 read with null expression.

- [ ] **Step 1: Write failing migration tests** for v27 rows acquiring revision 1 and null expression and for idempotent DAO revision increments/CAS conflicts.
- [ ] **Step 2: Write failing store tests** for same-process reload, duplicate digest rejection, workflow revision isolation, TTL pruning, 128/4096/256-KiB caps, corrupt data, missing identity, and elapsed-clock regression.
- [ ] **Step 3: Run focused database, datastore, and data mapper tests** and confirm the new assertions fail before implementation.
- [ ] **Step 4: Implement Room 28 columns, converter, migration, and transactional revision advancement** in `AutomationRepositoryImpl` and `RoomAutomationMutationPersistence`; status-only ENABLE/DISABLE writes keep the definition revision, while CREATE starts at 1 and UPDATE advances it. Add `MIGRATION_27_28` to `Migrations.ALL` and export `AppDatabase/28.json`.
- [ ] **Step 5: Implement Keystore HMAC identity hashing and DataStore history**; persist only trigger indices, monotonic times, revision identifiers, and HMAC digests; clear on corrupt/regressed time and reject unbounded writes.
- [ ] **Step 6: Extend canonical schema 5 and `AutomationEntity` mapping** so expression and revision survive row read/write, old canonical schemas, and portable backup import/export. Extend `WorkflowPersistencePolicy` reads to rehydrate the optional expression and stored revision from its full legacy snapshot.
- [ ] **Step 7: Run `./gradlew :core:database:testDebugUnitTest :core:datastore:testDebugUnitTest :data:testDebugUnitTest` and verify PASS, including migration and backup compatibility.**
- [ ] **Step 8: Commit** as `feat(triggers): persist bounded expression history`.

### Task 3: Runtime admission and source lifecycle integration

**Files:**
- Modify: `core/execution/src/main/java/com/nexaflow/core/execution/TriggerEvaluationSession.kt`
- Create: `core/execution/src/main/java/com/nexaflow/core/execution/TriggerExpressionRuntimeEvaluator.kt`
- Modify: `core/execution/src/main/java/com/nexaflow/core/execution/ExecutionEngine.kt`
- Modify: `app/src/main/java/com/nexaflow/app/di/AppModule.kt`
- Test: `core/execution/src/test/java/com/nexaflow/core/execution/TriggerExpressionRuntimeEvaluatorTest.kt`
- Modify: `core/execution/src/test/java/com/nexaflow/core/execution/ExecutionEngineTriggerMatchTest.kt`
- Source monitor files are unchanged unless they are added to the validated stable-identity allowlist; unsupported sources remain unavailable for temporal operators.

**Interfaces:**
- Runtime selects v2 only when `triggerExpressionV2 != null`; absent expression delegates byte-for-byte to the existing legacy evaluator.
- V2 evaluates `State`/`Event` leaves with current evidence, then invokes `TriggerExpressionHistoryStore` only for validated `Sequence` or `Count` nodes.
- Unsupported source identity yields `MISSING_EVENT_ID`; no event is admitted to temporal state and no actions run.
- Disable, deletion, revision change, permission loss, data corruption, and reboot/regressed elapsed time clear affected temporal history.

- [ ] **Step 1: Add failing engine tests** proving no legacy ANY/ALL result changes, invalid v2 cannot fall back, simultaneous events satisfy current-event groups, ordered events require A before B, reverse order fails, duplicate/replay does not advance, process reload preserves only valid history, and missing/Unknown/permission-lost evidence never fires.
- [ ] **Step 2: Run `./gradlew :core:execution:testDebugUnitTest --tests '*TriggerExpressionRuntimeEvaluatorTest' --tests '*ExecutionEngineTriggerMatchTest'` and confirm failure for missing integration.**
- [ ] **Step 3: Add the v2 runtime branch after schema validation and before action admission**; keep current `TriggerMatchPolicy` untouched for null-expression workflows.
- [ ] **Step 4: Limit `Sequence` and `Count` to event sources whose current `TriggerOccurrence.eventId` contract is stable and non-sensitive**; reject temporal authoring for other trigger types. Do not persist or log `sourceId` or raw `eventId`.
- [ ] **Step 5: Connect permission-loss, deletion, disable, and revision transitions to history clearing** without polling new providers or replaying UNKNOWN.
- [ ] **Step 6: Run `./gradlew :core:execution:testDebugUnitTest :core:automation-engine:testDebugUnitTest` and verify all legacy and v2 tests pass.**
- [ ] **Step 7: Commit** as `feat(triggers): evaluate explicit temporal expressions`.

### Task 4: Builder editor, migration controls, and evaluator-backed preview

**Files:**
- Create: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerExpressionEditorCard.kt`
- Create: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerExpressionPreview.kt`
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt`
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderViewModel.kt`
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/BuilderSaveCoordinator.kt`
- Modify: `feature/automation-builder/src/main/res/values/strings.xml`
- Modify: `feature/automation-builder/src/main/res/values-ar/strings.xml`
- Modify: `feature/automation-builder/src/main/res/values-de/strings.xml`
- Modify: `feature/automation-builder/src/main/res/values-es/strings.xml`
- Modify: `feature/automation-builder/src/main/res/values-fr/strings.xml`
- Modify: `feature/automation-builder/src/main/res/values-hi/strings.xml`
- Modify: `feature/automation-builder/src/main/res/values-ja/strings.xml`
- Modify: `feature/automation-builder/src/main/res/values-pt/strings.xml`
- Modify: `feature/automation-builder/src/main/res/values-ru/strings.xml`
- Modify: `feature/automation-builder/src/main/res/values-tr/strings.xml`
- Modify: `feature/automation-builder/src/main/res/values-zh-rCN/strings.xml`
- Test: `feature/automation-builder/src/test/java/com/nexaflow/feature/builder/TriggerExpressionEditorTest.kt`

**Interfaces:**
- Builder state holds the immutable draft expression and current base `workflowRevision`.
- Explicit enable builds a v2 expression from current ANY/ALL selections while preserving the legacy `triggerMatch`; disable drops only the v2 expression and restores legacy matching.
- Preview input is at most 64 sample events and reuses `TriggerExpressionEvaluatorV2`; a changed draft invalidates preview until recomputed.
- UI shows unsupported event source and invalid expression errors before save; it does not save guessed defaults.

- [ ] **Step 1: Add failing editor-model tests** for legacy upgrade/revert, reordering references, unsupported source selection, stale revision invalidation, max AST/window/count values, and preview/runtime output parity.
- [ ] **Step 2: Run focused builder tests and confirm failures.**
- [ ] **Step 3: Implement typed controls** for nested ANY/ALL groups, state-only NOT, ordered A→B within, and count/window; expose schema validation codes as localized messages.
- [ ] **Step 4: Implement bounded sample-event preview** by passing samples through the same runtime evaluator with no action execution or provider calls.
- [ ] **Step 5: Add matching strings and verify all locale keys** with `python scripts/check_strings_parity.py` and `python scripts/audit_translation_completeness.py`.
- [ ] **Step 6: Run `./gradlew :feature:automation-builder:testDebugUnitTest` and verify PASS.**
- [ ] **Step 7: Commit** as `feat(builder): edit and preview trigger expressions`.

### Task 5: Documentation, migration evidence, and integration gates

**Files:**
- Create/update: `docs/architecture/trigger-expression-v2.md`
- Modify: `docs/audit/triggers-actions-atomic-audit-2026-10-08.md`
- Modify: `docs/VALIDATION.md`
- Modify: canonical/atomic inventory artifacts regenerated by their checked-in scripts.
- Modify: `CHANGELOG.md` only if the repository’s release policy requires an entry for this unreleased feature branch.

- [ ] **Step 1: Document schema, operators, caps, source identity support, Unknown behavior, storage TTL, revision invalidation, permission-loss reset, and backup rollback.**
- [ ] **Step 2: Record tests and CI evidence by exact command/commit; explicitly label emulator, physical-device, permission denial/regrant, reboot, and OEM observations NOT TESTED unless performed.**
- [ ] **Step 3: Run focused tests for `:domain`, `:core:database`, `:core:datastore`, `:data`, `:core:execution`, `:core:automation-engine`, and `:feature:automation-builder`; then run the complete PR CI and inventory/resource gates.**
- [ ] **Step 4: Self-review the full diff for old ANY/ALL changes, raw event data logging/storage, unbounded work, migration downgrade loss, and preview/runtime divergence; fix every finding.**
- [ ] **Step 5: Commit** as `docs(triggers): document expression v2 safety and migration`.

## Self-review

- Spec coverage: AST semantics and bounds are covered by Task 1; Room/schema/revision/history security by Task 2; runtime/current-event/permission behavior by Task 3; editor and exact-evaluator preview/reversion by Task 4; audit and evidence by Task 5.
- Placeholder scan: no TODO/TBD implementation steps; each task lists concrete files, APIs, limits, and command expectations.
- Type consistency: editor and runtime share `TriggerExpressionDefinitionV2`, `TriggerExpressionValidator`, and `TriggerExpressionEvaluatorV2`; DataStore accepts `workflowRevision` as a `Long` and only receives sanitized occurrence inputs.
- Review focus tests: replay/process death, reboot, permission loss, stale revision, and hostile imports are assigned above.

## Execution handoff

Implement natively in this session. These tasks share the same AST, history state, and runtime interfaces, so keeping one implementer across their boundaries reduces contract drift. Perform an end-to-end self-review and use exact-head CI before opening or merging the issue PR.
