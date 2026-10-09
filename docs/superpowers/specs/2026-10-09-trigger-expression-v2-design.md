# Trigger Expression v2 Design

## Goal

Add an explicit, typed expression language for trigger logic without changing the meaning or execution path of existing ANY/ALL workflows. Version 2 adds nested boolean groups, state-only negation, ordered event sequences, and bounded event counts within monotonic-time windows.

## User intent and constraints

The issue requires `(A AND B) OR C`, NOT only for state predicates, ordered `A → B` within a window, and `count(N, window)`. Definitions from imports must fail closed when malformed or over budget. Event history must be bounded, minimal, and cleared on permission loss or workflow revision changes. The editor preview must call the same evaluator as runtime. Existing ANY/ALL workflows, backups, and restore behavior remain compatible.

## Design

### Activation and compatibility

`Automation.triggerExpressionV2` is optional and defaults to `null`. A null expression leaves the existing `TriggerMatchPolicy` and `TriggerExpressionEvaluator` path unchanged. A non-null expression is executable only when its envelope declares schema version 2 and passes `TriggerExpressionValidator`; unsupported or invalid expressions return a typed rejection and never fall through to legacy matching. The legacy `triggerMatch` value remains intact while v2 is active, so changing back to legacy mode restores its original behavior without reconstructing it.

The expression schema version is independent of the existing `Automation.workflowVersion` value, which already denotes occurrence-aware v1/v2 semantics. The new explicit envelope avoids overloading or silently reinterpreting that existing version.

### AST and validation limits

Use a sealed serializable AST with `State(triggerIndex)`, `Event(triggerIndex)`, `AllOf(children)`, `AnyOf(children)`, `NotState(triggerIndex)`, `Sequence(firstEventIndex, secondEventIndex, withinMs)`, and `Count(eventIndex, minimumCount, withinMs)`. Trigger references use indices from the immutable Automation snapshot; any edit that reorders or changes triggers produces a new `workflowRevision` and requires expression validation before save.

The validator enforces schema version 2, at most 64 nodes, depth at most 8, non-empty groups with at least two children, valid unique trigger references, event-only references for temporal operators, state-only references for `State` and `NotState`, positive windows no longer than 7 days, counts from 1 to 1000, and no unsupported node kinds. Runtime does not execute unvalidated ASTs. Unknown state is preserved as `Unknown`, never coerced to true or false.

### Evaluation and event history

One pure `TriggerExpressionEvaluatorV2` owns evaluation. Runtime and preview supply the same immutable expression, a trigger-state reader, the current occurrence, and a minimal history snapshot. Boolean groups use three-valued `ConditionResult` composition. Event leaves can match only the current occurrence; history operators can match only explicitly correlated, deduplicated events. An occurrence lacking a stable event identity cannot enter temporal history and yields a typed unavailable/unknown result for the temporal branch.

Elapsed windows use `SystemClock.elapsedRealtime()` and never wall time. A persisted elapsed-time regression (device reboot) expires the ledger. Calendar scheduling remains owned by the existing wall-clock schedule code and is not converted to elapsed time.

`TriggerExpressionHistoryStore` uses a dedicated Preferences DataStore ledger with one atomic bounded snapshot. It stores only workflow ID, monotonic workflow revision, trigger index, elapsed timestamp, and a keyed digest of a stable occurrence identity. It never stores event payloads, message/call/notification content, phone numbers, provider errors, or raw event identifiers. A Keystore-backed HMAC provider protects stable occurrence digests. Enforce at most 128 workflows, 4096 history records globally, and 256 KiB serialized storage; prune entries older than the maximum 7-day window and evict least-recently-used workflow state at capacity. Corruption, invalid records, a reboot/regressed elapsed clock, invalid expression, disabled workflow, or permission loss clears the affected history and returns no historical match.

Room adds `workflowRevision` and nullable `triggerExpressionJson` to `automations` in migration 27→28. A single DAO transaction increments `workflowRevision` for every definition save, compare-and-set save, and import; enable/disable status-only writes do not increment it. Temporal history is keyed by `(automationId, workflowRevision)`, so stale windows cannot cross a saved revision. Automation deletion clears that workflow's history. Old rows migrate with revision 1 and a null expression.

### Persistence and migration

The optional expression round-trips through the Automation serializer, Room converter, `triggerExpressionJson`, canonical workflow payload schema 5, and portable JSON backups. Canonical V3 schemas 3 and 4 decode with a null expression; schema 5 requires the v2 envelope to pass validation. Import preflight validates the AST before any write. Legacy-to-v2 authoring produces a structurally equivalent expression but does not alter `triggerMatch`; reverting to legacy mode clears only the active v2 field and restores the saved legacy behavior. Old backups omit the optional field and remain legacy.

### Builder and preview

Add a dedicated `TriggerExpressionEditorCard` with explicit opt-in, nested ANY/ALL groups, state-only NOT, ordered pair, and bounded-count controls. Trigger selections use the current snapshot indices. Show validation failures adjacent to the expression, with no save-time coercion. A preview simulator accepts a bounded list of sample events and state values and calls the same pure evaluator used by runtime; it does not call the execution engine, schedule work, or access providers. Preview is bound to the immutable `workflowRevision` and becomes stale after any draft edit until recalculated.

### Evidence and observability

Diagnostic results expose stable reason codes (`INVALID_SCHEMA`, `MISSING_EVENT_ID`, `HISTORY_EXPIRED`, `HISTORY_RESET`, `STATE_UNKNOWN`, `PERMISSION_UNAVAILABLE`) only. They never include AST config values, raw event identities, payloads, or exception text. Audit docs distinguish JVM/CI evidence from emulator, permission lifecycle, and OEM validation.

## Acceptance mapping

- Legacy ANY/ALL behavior is unchanged when `triggerExpressionV2 == null` and is covered by before/after parity tests.
- Only schema-2 expressions accepted by `TriggerExpressionValidator` reach temporal evaluation.
- Nested AST depth/nodes, time windows, count, ledger bytes, workflow count, and history event count are hard bounded.
- Unknown, unavailable, malformed, permission-revoked, duplicate, replayed, and reboot-reset inputs cannot create a positive historical match.
- Runtime and preview call the same pure evaluator; property tests compare both for generated bounded ASTs and event sequences.
- Room migration, portable backups, canonical schema 3/4 decode, schema 5 round-trip, and reversion to legacy preserve prior definitions.
- Process death preserves only valid bounded history; reboot, workflow revision change, deletion, or permission loss clears it.

## Risks and open implementation checks

- Some event sources do not currently supply stable occurrence IDs. Temporal authoring must be disabled for unsupported source contracts or those monitors must provide stable IDs before a definition may save.
- V2 evaluates only callbacks and previewed samples; it does not add polling. State leaves use the existing one-shot live state reader and capability/permission contract.
- The history ledger is not an audit trail. Its TTL and hard bounds are semantic safety controls and must be applied on every read/write path.
- A failed/corrupt store read is a closed gate for temporal expressions; it must never be interpreted as an empty successful history when an event replay could otherwise fire actions.
