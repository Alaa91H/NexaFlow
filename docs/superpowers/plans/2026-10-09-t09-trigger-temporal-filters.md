# T09 Typed Trigger Debounce, Hysteresis, Stable Duration and Cooldown Implementation Plan

> **For agentic workers:** Execute this plan inline, one task at a time. Use strict test-first red/green cycles and preserve the existing single execution and trigger-routing architecture.

**Goal:** Add backward-compatible, typed per-trigger temporal filters with matching authoring and runtime behavior, tri-state outcomes, bounded state, and reliable time semantics. This issue is not complete until every requested filter has live runtime wiring and exact-head CI.

**Architecture:** Keep saved trigger configuration in the existing `Trigger.config` map so no Room schema migration is needed. Add pure domain policy/state reducers for event quiet windows, cooldown/rate limits, numeric hysteresis and stable-for observations; connect them to the existing trigger-evaluation/admission path and canonical trigger schemas. Use monotonic elapsed time for durations, wall time only for calendar schedules, and expose only filters meaningful for a trigger family.

**Tech Stack:** Kotlin, kotlinx.coroutines, Android/Kotlin/JVM unit tests, existing canonical trigger schema/editor and `ExecutionEngine` / `TriggerExpressionEvaluator`.

**Spec:** GitHub issue #132 (T09), especially its Implementation, Tests & evidence, and Acceptance criteria sections.

## Global Constraints

- Preserve missing filter keys as legacy behavior: no configured filter means immediate existing semantics.
- Never convert `Unknown`, unavailable permission/provider state, clock reset, or malformed filter input into a confirmed `Unsatisfied` result.
- Keep temporal reducer state bounded and keyed by automation identity plus trigger identity; never use payload contents as an idempotency key.
- Preserve the single `ExecutionEngine`, `TriggerIndex`/event path, and `TriggerExpressionEvaluator`; do not add monitor/scheduler queues.
- Keep event occurrence wall timestamps for audit/schedule identity; use injected monotonic elapsed time for debounce, cooldown, stable duration and rate-limit windows.
- Persist filter settings through existing serialized trigger config; no database migration unless actual model/storage evidence proves it is required.
- Do not claim Android device/OEM behavior without a connected named device and direct evidence.

## Review Focus

- Missing, malformed, negative, or overflow duration settings must preserve legacy behavior or fail closed with an explicit code; they must never wrap to an immediately eligible interval.
- A monotonic clock reset or process restart must not create a long negative/positive duration or unbounded retained filter history.
- Initial threshold observation must initialize hysteresis without manufacturing a threshold crossing.
- Unknown/missing permission or sensor observations must not clear a satisfied stable condition or become false.
- Legacy trigger config with no new keys must behave exactly as before after save/reopen.

### Task 1: Typed temporal policy and bounded domain reducers

**Files:**
- Create `domain/src/main/java/com/nexaflow/domain/schedule/TriggerTemporalFilters.kt`.
- Test `domain/src/test/java/com/nexaflow/domain/schedule/TriggerTemporalFiltersTest.kt`.
- Reuse `domain/src/main/java/com/nexaflow/domain/models/Automation.kt` `Trigger` config contract without changing its persistence shape.

**Interfaces:**
- Produce an immutable parser result with explicit supported/invalid/unconfigured state, a monotonic `TriggerTemporalClock` seam, bounded per-trigger state, and reducer results `ALLOW`, `BLOCKED(reasonCode)`, or `UNKNOWN(reasonCode)`.
- Event filters own quiet-window debounce, minimum interval/cooldown and bounded event-rate accounting.
- State filters own hysteresis latching and stable-for duration from consecutive known observations; unknown observations retain unresolved status and do not reset into false.

- [x] Write literal-boundary unit tests first for first observation, exact cooldown boundary, quiet-window reset/expiry, duplicate occurrence, rate-limit expiry, threshold jitter/oscillation, stable-for, UNKNOWN, clock rollback, malformed/overflow values, and bounded eviction.
- [x] Run only the new domain test class and confirm expected failures.
- [x] Implement the smallest pure reducers that satisfy those tests; derive outputs from hand-calculated fixtures.
- [x] Re-run the domain suite (including legacy cooldown coverage); retain legacy helpers unchanged unless a regression test demonstrates a shared defect.

### Task 2: Schema-driven persisted filter authoring

**Files:**
- Modify `domain/src/main/java/com/nexaflow/domain/catalog/TriggerNodeSchemas.kt`.
- Modify `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt` only if canonical schemas do not cover the supported advanced trigger families.
- Test schema config contracts and editor draft round-trip in the existing domain/catalog and builder tests.

**Interfaces:**
- Add shared typed duration/count fields only to trigger families where event/state/threshold/schedule semantics support them; add a threshold hysteresis field only to threshold schemas.
- Persist values as versioned, documented string keys in the existing trigger config map. Defaults are omitted or equal the no-filter legacy behavior.
- The canonical schema editor remains the ordinary authoring surface; no second independent editor contract.

- [x] Write schema tests for supported fields, numeric bounds, unsupported families, and absent legacy defaults.
- [x] Run targeted tests; confirm schema and editor expectations match the final typed controls.
- [x] Add bounded schema fields for event rate limits and cooldown/min interval; expose shared filter fields alongside specialized and advanced trigger controls.
- [x] Add millisecond unit and 0–7 day bounds in the canonical UI binding; preserve the specialized TIME schema and unrelated config.
- [x] Verify Room converter encode/decode preserves each persisted filter key and unit exactly, alongside legacy Gson payload parsing. UI draft recreation is NOT TESTED.

### Task 3: Runtime integration through existing trigger evaluation and admission

**Files:**
- Modify `core/execution/src/main/java/com/nexaflow/core/execution/TriggerEvaluationSession.kt` and/or add a focused evaluator adjacent to `TriggerExpressionEvaluator`.
- Modify `core/execution/src/main/java/com/nexaflow/core/execution/ExecutionEngine.kt` at the existing automatic trigger admission gate.
- Test existing `core/execution/src/test/java/com/nexaflow/core/execution` trigger evaluation and execution admission suites.

**Interfaces:**
- `ExecutionEngine` passes automation identity, current trigger occurrence, typed current state, and monotonic time to one filter evaluator before any checkpoint or action side effect.
- `TriggerExpressionEvaluator` retains ANY/ALL and CURRENT_EVENT/LIVE_STATE semantics; filters may only narrow or delay a confirmed candidate, never manufacture satisfaction from UNKNOWN.
- Rejections use stable reason codes and do not write a durable execution checkpoint. Delayed state candidates are revalidated against live state before action execution.
- State is bounded across automations and stale config revisions; disable/remove/reset paths clear owned reducer state.

- [x] Write regression tests proving configured cooldown/debounce applies only to matching trigger occurrences, trailing-edge replacement runs only the latest action, stable-for waits for its duration, threshold jitter respects hysteresis, and UNKNOWN observations remain UNKNOWN. Device permission denial/regrant remains NOT TESTED.
- [x] Run targeted tests against real domain reducers and the existing execution gate; no assertions solely on test doubles.
- [x] Connect event filters to the shared admission path with injectable monotonic clock and typed outcomes.
- [ ] Test event/state/schedule semantics separately and ensure wall-clock edits do not alter elapsed filters.
- [x] Re-run the complete execution-engine unit suite and database converter persistence suite.

### Task 4: Evidence and acceptance

**Files:**
- Update `docs/audit/trigger-source-lifecycle.csv` only where filter semantics alter source lifecycle evidence.
- Update the T09 section in `docs/audit/triggers-actions-atomic-audit-2026-10-08.md` with supported families, configuration units/defaults, tests and unsupported cases.

- [x] Run `git diff --check`.
- [x] Run targeted domain/editor/execution tests and database converter tests. Local JVM unit suites passed on 2026-10-09.
- [ ] Run full configured exact-SHA CI; it remains NOT TESTED.
- [ ] Inspect current-head checks, test counts and job conclusions; skipped emulator/device work remains NOT TESTED.
- [x] Confirm no Room migration, Android permission or external capability change.
- [ ] Commit the single reviewable T09 package and report the exact PR, merge SHA, commands, exit codes, CI run and remaining device gaps.

## Decisions

- Ruling: keep per-trigger filter configuration in the serialized config map and keep filter history process-local/bounded — the current persistence model already round-trips arbitrary trigger config and no durable lifecycle contract exists for filter timers — cost if wrong: a restart clears in-flight debounce/stable-for history and admits the first fresh occurrence under a new process; this must be explicit in tests and docs.
- Ruling: do not close #132 with only event admission policies. Trailing-edge debounce, stable-for live revalidation, threshold hysteresis wiring, permission/provider reset behavior, current editor compilation, and exact-head CI remain open.
