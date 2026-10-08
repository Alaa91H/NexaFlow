# T02 atomic finding register

Audit evidence SHA(s): `a9cf4e634cf435caf425e709b152a3ee72892779`. This is a point-in-time source and evidence review; it does not certify device/OEM behavior.

## Status counts

| Classification | Count |
|---|---:|
| BLOCKED_EXTERNAL | 2 |
| CONFIRMED | 1 |
| DOCUMENTED_GAP | 5 |
| IMPLEMENTED_UNVERIFIED | 3 |
| INVESTIGATE | 3 |

## Findings

### T02-001 — Windows DataStore replacement fails across production and test factories

- Classification / severity: **CONFIRMED / P1**; area: `persistence`; risk: `CROSS_PLATFORM_PERSISTENCE`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:core/datastore/src/test/java/com/nexaflow/core/datastore/AgentNetworkPreferencesTest.kt:33;a9cf4e634cf435caf425e709b152a3ee72892779:core/datastore/src/test/java/com/nexaflow/core/datastore/AiProviderPreferencesTest.kt:125;a9cf4e634cf435caf425e709b152a3ee72892779:core/datastore/src/test/java/com/nexaflow/core/datastore/SmsDeliveryStoreTest.kt:24;a9cf4e634cf435caf425e709b152a3ee72892779:core/datastore/src/main/java/com/nexaflow/core/datastore/SmsDeliveryStore.kt:13;a9cf4e634cf435caf425e709b152a3ee72892779:core/datastore/src/test/java/com/nexaflow/core/datastore/DataStoreCorruptionHandlerTest.kt:45`.
- Observation: The exact current Windows checkout reproduces five write failures across PreferenceDataStoreFactory files and the Robolectric-backed SmsDeliveryStore. Each throws IOException while androidx.datastore tries to rename a .tmp file over an existing target. The production corruption handler does not participate in these ordinary edit writes.
- Impact: Windows-hosted unit tests fail and persistent preference updates can fail on affected host/filesystem combinations; CI on Linux does not expose the same replacement semantics.
- Prerequisite: Run one exact-HEAD Windows test task with Android SDK configured and no competing Gradle process.
- Minimal reproducer / verification: `.\gradlew.bat :core:datastore:testDebugUnitTest --console=plain --no-configuration-cache`
- Expected: Selected DataStore tests pass on Windows and retain persistence
- Actual: Current HEAD a9cf4e634cf435caf425e709b152a3ee72892779: 46 tests completed, 5 failed. Exact failing cases: FAIL explicitLanChoicePersists:; FAIL v1MigrationPreservesValidProfilesWhenOneEntryIsCorrupt:; FAIL cooldownIsDurableAcrossDifferentMessages:; FAIL samePhysicalMessageCanBeClaimedByDifferentAutomations:; FAIL duplicateFingerprintIsRejectedAcrossClaims: Each report records IOException renaming a temporary DataStore file over an existing target.
- Regression target: `core/datastore/src/test/java/com/nexaflow/core/datastore/AgentNetworkPreferencesTest.kt; core/datastore/src/test/java/com/nexaflow/core/datastore/AiProviderPreferencesTest.kt; core/datastore/src/test/java/com/nexaflow/core/datastore/SmsDeliveryStoreTest.kt`; proposed task: #126.
- Test results: `Windows exact HEAD: 46 tests / 5 failed; see hash-pinned per-class reports. CI Ubuntu exact older commit 66f0dbd: 46 tests / 0 failed per historical baseline (not current proof).`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `Historical clue only: docs/evidence/baseline/p0-01-current-main-2026-10-05.md:20; the current exact-HEAD reproduction is separate.`.

### T02-002 — Locked-boot delivery policy remains undecided

- Classification / severity: **DOCUMENTED_GAP / P0**; area: `boot`; risk: `BOOT_AND_RECOVERY_POLICY`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:app/src/main/AndroidManifest.xml:196;a9cf4e634cf435caf425e709b152a3ee72892779:core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationAlarmReceiver.kt:48`.
- Observation: The manifest filter lists LOCKED_BOOT_COMPLETED
- Impact: while the receiver is not directBootAware and its recovery dependencies use credential-protected application state; OQ-06 records that the desired behavior is undecided.
- Prerequisite: A misleading source declaration or an unsafe direct-boot change can break scheduled automation or access unavailable state.
- Minimal reproducer / verification: `Maintainer must choose wait-until-first-unlock or a restricted device-protected subset.`
- Expected: Inspect the merged manifest and simulate locked boot before first unlock on supported Android hardware.
- Actual: No owner policy decision or physical locked-boot observation is recorded.
- Regression target: `AutomationAlarmReceiver boot-policy test; API 26+ physical locked-boot test`; proposed task: #128.
- Test results: `NOT RUN; see external blocker and documented required verification.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `#128`.

### T02-003 — Trigger occurrence identity coverage across event sources

- Classification / severity: **INVESTIGATE / P0**; area: `event_identity`; risk: `DUPLICATE_ADMISSION`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:core/execution/src/main/java/com/nexaflow/core/execution/TriggerOccurrenceDeduplicator.kt:39;a9cf4e634cf435caf425e709b152a3ee72892779:core/execution/src/test/java/com/nexaflow/core/execution/TriggerOccurrenceDeduplicatorTest.kt:58`.
- Observation: The shared deduplicator intentionally processes occurrences without a stable eventId instead of guessing an identity; tests pin that contract.
- Impact: Sources that cannot supply a stable identity may admit repeated non-idempotent work; whether this occurs for a production source needs source-specific evidence.
- Prerequisite: Map every ingress to a stable event identity or document why it cannot provide one.
- Minimal reproducer / verification: `Run deterministic duplicate-delivery tests per trigger source with identical and missing IDs`
- Expected: The same stable source event is admitted at most once within its contract window; missing identity behavior is explicit for each source.
- Actual: The generic missing-ID behavior is tested; production-source identity mapping and duplicate-delivery coverage remain unverified.
- Regression target: `TriggerOccurrenceDeduplicatorTest; source-specific admission tests`; proposed task: #127.
- Test results: `NOT RUN for this finding.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `None`.

### T02-004 — Crash recovery across checkpoint side-effect and history boundaries

- Classification / severity: **DOCUMENTED_GAP / P0**; area: `recovery`; risk: `CROSS_STORE_RECOVERY`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:core/datastore/src/main/java/com/nexaflow/core/datastore/ActiveExecutionStore.kt:279;a9cf4e634cf435caf425e709b152a3ee72892779:core/execution/src/main/java/com/nexaflow/core/execution/recovery/ExecutionRecoveryCoordinator.kt:29`.
- Observation: The runtime persists UNKNOWN outcomes and requires review before replay; repository evidence does not yet cover every cross-store crash boundary between side effect checkpoint and terminal/history writes.
- Impact: A crash between durable stores can leave work pending review or incomplete history; blind replay is deliberately prohibited.
- Prerequisite: Enumerate side-effect and persistence commit boundaries for each execution path before adding fault injection.
- Minimal reproducer / verification: `Inject process/store failures before and after side effect checkpoint completion and verify no unsafe replay or lost owner.`
- Expected: Every boundary yields a recoverable truthful result or explicit UNKNOWN review item with stable ownership.
- Actual: Selected checkpoint paths are covered; exhaustive cross-store crash-boundary recovery is not established.
- Regression target: `ExecutionEngineRecoveryCheckpointTest; ActiveExecutionStoreCheckpointTest`; proposed task: #129; #130.
- Test results: `NOT RUN; see external blocker and documented required verification.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `No external blocker to deterministic JVM fault-injection; device process-death proof remains separate.`.

### T02-005 — Declared action fields without an exact handler read

- Classification / severity: **INVESTIGATE / P1**; area: `action_schema`; risk: `ACTION_SCHEMA_RUNTIME_PARITY`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:domain/src/main/java/com/nexaflow/domain/catalog/ActionNodeSchemas.kt:35;a9cf4e634cf435caf425e709b152a3ee72892779:domain/src/main/java/com/nexaflow/domain/catalog/ActionNodeSchemas.kt:61;a9cf4e634cf435caf425e709b152a3ee72892779:domain/src/main/java/com/nexaflow/domain/catalog/ActionNodeSchemas.kt:139;a9cf4e634cf435caf425e709b152a3ee72892779:domain/src/main/java/com/nexaflow/domain/catalog/ActionNodeSchemas.kt:259;a9cf4e634cf435caf425e709b152a3ee72892779:core/execution/src/main/java/com/nexaflow/core/execution/handler/AppActionsHandler.kt:45;a9cf4e634cf435caf425e709b152a3ee72892779:core/execution/src/main/java/com/nexaflow/core/execution/handler/SystemActionsHandler.kt:374;a9cf4e634cf435caf425e709b152a3ee72892779:core/execution/src/main/java/com/nexaflow/core/execution/handler/SystemActionsHandler.kt:383;a9cf4e634cf435caf425e709b152a3ee72892779:core/execution/src/main/java/com/nexaflow/core/execution/handler/NotificationActionsHandler.kt:93`.
- Observation: T01 found four declared field/handler mismatches: BATTERY_ALERTS.below, APPLICATION_LAUNCH_APP.packages, SYSTEM_OPEN_PLAY_STORE_APP.package, and SYSTEM_REBOOT.mode.
- Impact: Users may configure values that do not affect the selected action; intended legacy aliases or compatibility behavior have not been resolved.
- Prerequisite: Confirm user-visible contract and whether each field is intentional compatibility data before changing schema/runtime behavior.
- Minimal reproducer / verification: `Configure a non-default value for each field and assert the handler receives or intentionally ignores it in a focused unit test.`
- Expected: Each field is consumed by the handler or explicitly documented as unused/compatibility-only with a regression test.
- Actual: Static same-arm analysis reports no handler read; UI intent and runtime behavior remain unverified.
- Regression target: `ActionRegistry and family-handler tests for listed actions`; proposed task: #135; #136.
- Test results: `Source-level finding; UI, compatibility intent, and dynamic handler behavior remain unverified.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `Source-level finding from merged T01; not classified as a confirmed user-visible defect.`.

### T02-006 — Fixed-IANA timezone configuration visibility

- Classification / severity: **INVESTIGATE / P1**; area: `trigger_editor`; risk: `TIME_CONFIGURATION_PARITY`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:domain/src/main/java/com/nexaflow/domain/catalog/TriggerNodeSchemas.kt:26;a9cf4e634cf435caf425e709b152a3ee72892779:feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerTimeEditor.kt:231`.
- Observation: TIME schema and schedule calculator define zonePolicy and zoneId
- Impact: but the legacy trigger editor's TIME section has no direct control found by the T01 static UI candidate scan.
- Prerequisite: Users may be unable to select the fixed timezone behavior exposed by the schema/runtime contract.
- Minimal reproducer / verification: `Trace both legacy and canonical editor routes and clarify which one owns TIME configuration.`
- Expected: Create a TIME trigger through each supported editor and verify zonePolicy/zoneId can be set and survive save/reopen.
- Actual: Legacy-editor binding was not found; whether the canonical editor binds and round-trips these fields remains unknown.
- Regression target: `TriggerTimeEditor UI and builder save/reopen tests`; proposed task: #131; #134.
- Test results: `NOT RUN for this finding.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `None`.

### T02-007 — Plugin event input validation and deduplication boundary

- Classification / severity: **IMPLEMENTED_UNVERIFIED / P1**; area: `plugin_ingress`; risk: `PLUGIN_TRUST_BOUNDARY`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:core/automation-engine/src/main/java/com/nexaflow/core/engine/PluginEventIngress.kt:43;a9cf4e634cf435caf425e709b152a3ee72892779:core/automation-engine/src/test/java/com/nexaflow/core/engine/PluginEventIngressTest.kt:30`.
- Observation: The ingress validates token shape and length matches approved configured subscriptions and rate/dedup limits before publishing; tests exercise these static paths.
- Impact: Hostile package identity and Android permission/signature enforcement still require full component/installed-plugin boundary review.
- Prerequisite: Verify sender identity originates from trusted Android caller metadata and that no caller can spoof senderPackage.
- Minimal reproducer / verification: `Attempt forged sender/component payloads through the exported ingress component and verify rejection before publish.`
- Expected: Only authorized installed plugin identity can publish matching approved events; all rejected attempts produce no event.
- Actual: JVM ingress cases exist; installed-package caller identity and Android permission behavior remain unverified.
- Regression target: `PluginEventIngressTest; exported-component manifest/security integration tests`; proposed task: #143.
- Test results: `JVM/source evidence exists as described; required integration/device verification NOT RUN.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `No vulnerability confirmed; end-to-end Android trust boundary remains unverified.`.

### T02-008 — External workflow import bounds and review-before-activation

- Classification / severity: **IMPLEMENTED_UNVERIFIED / P1**; area: `imported_data`; risk: `IMPORTED_DATA_VALIDATION`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:data/src/main/java/com/nexaflow/data/backup/BackupManager.kt:67;a9cf4e634cf435caf425e709b152a3ee72892779:data/src/main/java/com/nexaflow/data/backup/BackupManager.kt:137`.
- Observation: Import code bounds stream size
- Impact: preflights workflows
- Prerequisite: rekeys ID collisions and disables imported automations before persistence; model-based fuzz and hostile-provider coverage are not comprehensive in T02 evidence.
- Minimal reproducer / verification: `Malformed or adversarial import data could still expose untested parser/validation edge cases even though the current boundary has explicit safeguards.`
- Expected: Run bounded fuzz/property tests over malformed JSON quotas identities and dependency graphs.
- Actual: Source preflight and disable-before-persist safeguards exist; adversarial fuzz and hostile-provider behavior remain unverified.
- Regression target: `BackupManagerTest; workflow validation property/fuzz target`; proposed task: #143.
- Test results: `JVM/source evidence exists as described; required integration/device verification NOT RUN.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `None`.

### T02-009 — Accessibility behavior is not certified by source parity gates

- Classification / severity: **DOCUMENTED_GAP / P1**; area: `accessibility`; risk: `ACCESSIBILITY_VALIDATION`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:docs/AUDIT.md:14;a9cf4e634cf435caf425e709b152a3ee72892779:docs/MASTER_PLAN_STATUS_2026-10-05.md:16`.
- Observation: Locale key parity and static UI source checks do not constitute TalkBack keyboard focus contrast or RTL device verification.
- Impact: Users relying on assistive technology or RTL layouts may encounter unobserved interaction barriers.
- Prerequisite: Run accessibility instrumentation and manual TalkBack/RTL checks on supported API levels.
- Minimal reproducer / verification: `Execute screen-reader focus order and action labeling checks across builder settings and key flows.`
- Expected: Automated and manual accessibility outcomes are recorded with device/API evidence and regressions for confirmed failures.
- Actual: No Android device or TalkBack session was available for this audit.
- Regression target: `Compose accessibility tests; Android API 26+ physical UI validation`; proposed task: #145.
- Test results: `NOT RUN; see external blocker and documented required verification.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `Physical UI validation requires a connected Android device.`.

### T02-010 — Locale parity does not establish translation quality

- Classification / severity: **DOCUMENTED_GAP / P1**; area: `localization`; risk: `LOCALIZATION_QUALITY`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:docs/AUDIT.md:14;a9cf4e634cf435caf425e709b152a3ee72892779:docs/MASTER_PLAN_STATUS_2026-10-05.md:32`.
- Observation: Automated checks establish locale key-set parity only and do not assess semantic accuracy truncation or context-sensitive plural handling.
- Impact: Incorrect or clipped translations can make settings and safety prompts confusing despite a green parity gate.
- Prerequisite: Review high-risk safety permission and automation strings in context with qualified Arabic and English reviewers.
- Minimal reproducer / verification: `Run locale screenshots and plural/RTL review for the critical flows.`
- Expected: Critical strings are reviewed in context and regressions for truncation or mistranslation are recorded.
- Actual: No contextual translation, truncation, plural, or RTL review was run for current HEAD.
- Regression target: `Locale screenshot review; resource parity tests`; proposed task: #147.
- Test results: `NOT RUN; see external blocker and documented required verification.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `Requires qualified contextual review`.

### T02-011 — Measured dispatch editor and battery baselines

- Classification / severity: **BLOCKED_EXTERNAL / P2**; area: `performance`; risk: `PERFORMANCE_AND_BATTERY`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:docs/MASTER_PLAN_STATUS_2026-10-05.md:16;a9cf4e634cf435caf425e709b152a3ee72892779:docs/OPEN_QUESTIONS.md:7`.
- Observation: No current macrobenchmark Perfetto battery or soak result is attached to the current baseline.
- Impact: Performance regressions and background battery impact cannot be quantified from static tests.
- Prerequisite: Provide supported Android hardware and an agreed benchmark/soak window.
- Minimal reproducer / verification: `Run macrobenchmark and Perfetto traces on named devices under controlled workloads.`
- Expected: A repeatable baseline and budgets are attached for trigger dispatch editor rendering and idle battery.
- Actual: No physical device, benchmark trace, battery measurement, or soak artifact is available.
- Regression target: `Macrobenchmark and Perfetto suites; named OEM/API device matrix`; proposed task: #144; #146.
- Test results: `NOT RUN; blocked on named external owner/device dependency.`
- External blocker: Maintainer must supply the agreed device(s) and benchmark window.
- Historical clue (not treated as current proof): `No current physical benchmark artifact.`.

### T02-012 — Android API OEM permission and privileged-backend acceptance matrix

- Classification / severity: **BLOCKED_EXTERNAL / P0**; area: `device_acceptance`; risk: `DEVICE_OEM_PERMISSION`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:docs/OPEN_QUESTIONS.md:7;a9cf4e634cf435caf425e709b152a3ee72892779:docs/audit/triggers-actions-atomic-audit-2026-10-08.md:78`.
- Observation: No named physical devices are available; no Root Shizuku OEM exact-alarm or API 36/37 behavior is claimed.
- Impact: The final runtime safety and 72-hour acceptance gates cannot be completed without target hardware and test access.
- Prerequisite: Maintainer must identify devices Android builds backend states and the 72-hour test window.
- Minimal reproducer / verification: `Run the documented device matrix and attach redacted logs keyed by build and device.`
- Expected: Every required device/API/backend scenario has evidence or an explicit owner-approved exclusion.
- Actual: No target device or owner-approved device/API/backend exclusion is recorded.
- Regression target: `T23 device matrix and soak procedures`; proposed task: #146.
- Test results: `NOT RUN; blocked on named external owner/device dependency.`
- External blocker: Maintainer must provide physical test devices and test window.
- Historical clue (not treated as current proof): `docs/audit/triggers-actions-atomic-audit-2026-10-08.md:78`.

### T02-013 — Timezone resolution and DST wall-clock scheduling

- Classification / severity: **IMPLEMENTED_UNVERIFIED / P1**; area: `clock_dst`; risk: `CLOCK_AND_DST`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:domain/src/main/java/com/nexaflow/domain/schedule/TimeTriggerCalculator.kt:59;a9cf4e634cf435caf425e709b152a3ee72892779:domain/src/test/java/com/nexaflow/domain/schedule/TimeTriggerCalculatorTest.kt:74;a9cf4e634cf435caf425e709b152a3ee72892779:core/automation-engine/src/test/java/com/nexaflow/core/engine/AutomationAlarmReceiverTimeChangeTest.kt:15`.
- Observation: Current code contains deterministic DST gap overlap and wall-clock tests plus clock/timezone rescheduling hooks.
- Impact: RTC delivery after real timezone changes reboot Doze and OEM alarm restrictions has not been physically observed.
- Prerequisite: Run exact schedule tests then device travel clock-change and DST cases on named Android versions.
- Minimal reproducer / verification: `Unit behavior matches wall-clock expectations and each platform alarm is recomputed after a real clock/timezone change.`
- Expected: Source and local test inventory evidence exist; physical alarm delivery remains NOT TESTED.
- Actual: JVM schedule tests and source hooks are present; physical delivery after clock changes, reboot, and Doze is NOT TESTED.
- Regression target: `TimeTriggerCalculatorTest; AutomationAlarmReceiverTimeChangeTest; physical schedule delivery matrix`; proposed task: #128.
- Test results: `JVM/source evidence exists as described; required integration/device verification NOT RUN.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `None`.

### T02-014 — Durable cancellation outcome at side-effect boundaries

- Classification / severity: **DOCUMENTED_GAP / P0**; area: `cancellation`; risk: `CANCELLATION_AND_SIDE_EFFECTS`.
- Evidence: `a9cf4e634cf435caf425e709b152a3ee72892779:core/execution/src/main/java/com/nexaflow/core/execution/ExecutionEngine.kt:991;a9cf4e634cf435caf425e709b152a3ee72892779:core/execution/src/test/java/com/nexaflow/core/execution/ExecutionEngineRecoveryCheckpointTest.kt:384`.
- Observation: The engine has explicit cancellation checkpoints and selected tests that preserve UNKNOWN after interrupted side effects; broad action/backend cancellation boundary coverage is not present in the T02 evidence.
- Impact: Incorrect cancellation classification can hide an applied side effect or incorrectly retry it.
- Prerequisite: Enumerate each action/backend side-effect boundary and define truthful cancellation outcome.
- Minimal reproducer / verification: `Inject cancellation before dispatch during dispatch after effect and before history persistence for representative idempotent and irreversible actions.`
- Expected: Cancellation never converts an uncertain irreversible effect to success or automatic replay.
- Actual: Selected JVM cases exist; exhaustive capability cancellation and process-death behavior remains unverified.
- Regression target: `ExecutionEngineRecoveryCheckpointTest; capability-specific cancellation fault injection`; proposed task: #129; #130.
- Test results: `NOT RUN; see external blocker and documented required verification.`
- External blocker: None recorded.
- Historical clue (not treated as current proof): `No specific current cancellation regression reproduced in this audit.`.

## Audit coverage and limits

The source review covers event identity/concurrency, cancellation and crash boundaries, clock and DST, permissions and locked boot, persistence, restore/import ownership, plugin trust, accessibility, performance, and localization. Related trigger/action family mappings remain traceable through the T01 inventories. Confidence labels distinguish current reproductions from code-level observations and missing external evidence.

The register separates confirmed behavior, explicit gaps, investigations, unverified implementations, and external blockers. Absence of a physical device, provider account, or owner decision is recorded as a limitation; it is not converted into a software defect. A static source reference proves only the cited source shape, not dynamic behavior.

All P0 candidates without a deterministic current reproducer remain labeled `INVESTIGATE`, `DOCUMENTED_GAP`, or `BLOCKED_EXTERNAL`; none is promoted to `CONFIRMED` by historical prose alone.
