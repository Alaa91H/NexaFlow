# Dashboard, AI Settings, and Activity History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Deliver the approved dashboard actions, collapsible advanced automation options, tabbed and extensible AI provider settings, and a dedicated activity history that includes NexaFlow-only SMS events.

**Architecture:** Preserve existing navigation, provider profile persistence, HistoryRepository, and provider catalog. Separate the work into dashboard/builder UI, AI settings/provider activity, and general activity/SMS persistence; add SMS events through Room and the existing SMS handling paths without adding inbox access. Keep each feature owned by its current module and compose existing screens/repositories where possible.

**Tech Stack:** Kotlin, Android, Jetpack Compose, Navigation Compose, Hilt, Room, Kotlin coroutines/Flow, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-01-dashboard-ai-settings-activity-design.md`

## Global Constraints

- SMS history is limited to messages/events handled by NexaFlow automations; it must not read the device's full SMS inbox or add `READ_SMS` permission.
- Credentials remain user supplied and stored using the existing secure credential handling.
- Do not silently replace a user's model or selected profile.
- Keep AI activity and SMS event logs bounded and clearable; avoid API keys, message bodies, or other secrets in logs.
- Provider definitions must remain catalog/adapter driven so adding providers does not require redesigning the screen.

## Review Focus

- Existing provider profiles with non-default/custom model IDs remain selected and usable after moving controls into tabs.
- A provider that does not support model discovery, or returns malformed/empty model data, still permits manual model IDs and reports connectivity accurately.
- RTL and narrow dashboard widths keep both floating actions visible, distinguishable, and reachable above system navigation insets.
- SMS broadcast/send paths that fail or are duplicated do not create misleading duplicate-success events or persist message content.
- Existing settings/history deep links and persisted execution records remain accessible after navigation consolidation.

---

### Task 1: Dashboard floating actions and collapsible advanced choices

**Files:**
- Modify: `feature/dashboard/src/main/java/com/nexaflow/feature/dashboard/DashboardScreen.kt`
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt`
- Test: `feature/dashboard/src/test/java/com/nexaflow/feature/dashboard/DashboardScreenTest.kt` (add if no existing screen test)
- Test: `feature/automation-builder/src/test/java/com/nexaflow/feature/builder/AutomationBuilderScreenTest.kt` (add if no existing screen test)

**Interfaces:**
- Consume the existing `NexaFlowFloatingActionButton`, `navController`, `showAdvancedTriggerOptions`, and `showAdvancedActionOptions` state.
- Keep the New Task destination `automation_builder` and Ask NexaFlow destination `ai_chat` unchanged.

- [x] **Step 1: Add UI coverage** for both home actions and independent advanced section toggling. Assert the old Ask NexaFlow card is absent, both labeled floating controls navigate correctly, and each advanced header can be tapped to show then hide its choices without changing the other section.
- [x] **Step 2: Run focused dashboard and builder tests**. Initial compile/test attempts exposed unsupported matcher imports and Robolectric animation setup; corrected the test harness and then verified each unit independently.
- [x] **Step 3: Implement the two FAB layout** by placing Ask NexaFlow and New Task actions side by side in the scaffold FAB slot, preserving accessibility labels and RTL-aware arrangement; remove the old top card.
- [x] **Step 4: Make advanced headings toggle controls** in both collapsed and expanded states, exposing expanded/collapsed semantics and keeping trigger/action state independent.
- [x] **Step 5: Re-run focused tests**. `:feature:dashboard:testDebugUnitTest --tests com.nexaflow.feature.dashboard.DashboardFabActionsTest` and `:feature:automation-builder:testDebugUnitTest --tests com.nexaflow.feature.builder.AdvancedOptionsToggleTest` pass.
- [x] **Step 6: Commit** with `git add feature/dashboard feature/automation-builder && git commit -m "feat: restore dashboard actions and collapsible options"`.

### Task 2: AI Settings tabs, provider presets, model picker, and activity

**Files:**
- Modify: `feature/settings/src/main/java/com/nexaflow/feature/settings/AgentSettingsScreen.kt`
- Modify: `feature/settings/src/main/java/com/nexaflow/feature/settings/AgentSettingsViewModel.kt`
- Modify: `core/ai-runtime/src/main/java/com/nexaflow/core/airuntime/AiProviderCatalog.kt` and provider adapters only where current discovery/verification interfaces need extension.
- Modify: localized strings/resources owned by `feature/settings` and `core/ai-runtime` as needed.
- Test: `feature/settings/src/test/java/com/nexaflow/feature/settings/AgentSettingsViewModelTest.kt` (add or extend existing tests).
- Test: `core/ai-runtime/src/test/java/com/nexaflow/core/airuntime/AiProviderCatalogTest.kt` and provider transport tests (add or extend as applicable).

**Interfaces:**
- Consume existing `AiProviderCatalog`, `AiProviderRegistry`, secure storage, provider preferences, transports, `discoveredModels`, and `activity` state.
- Expose UI state for the selected top tab, per-provider model discovery status, available/manual model choices, verification result, and bounded AI provider/agent activity.

- [x] **Step 1: Add focused UI/catalog/provider/persistence tests** for tab selection, preset defaults, provider effort mapping, model discovery request contracts, and persisted selected provider/model settings. ViewModel branch-level state tests remain a coverage limitation.
- [x] **Step 2: Run focused AI settings tests** with `./gradlew :feature:settings:testDebugUnitTest :core:ai-runtime:testDebugUnitTest`. Expected: failures identify missing model fallback/activity/UI state behavior.
- [x] **Step 3: Extend catalog/adapter model discovery and verification contracts** so supported protocols enumerate models and unsupported discovery is explicit; retain a manually entered model ID when listing is unavailable. Ensure secret values are never included in logs/errors.
- [x] **Step 4: Refactor screen into Agents, Logs, and Add agent tabs**. Agents retains current profile selection/edit/verify/remove controls; Logs displays provider/agent activity events; Add agent offers provider preset/custom selection, API key input, discovered model picker, manual model fallback, simple latency/quality levels, and Verify/Add actions.
- [x] **Step 5: Re-run focused AI tests** using the command in Step 2. Expected: the checked-in provider catalog, adapter, persistence, and tab tests pass; no claim is made for unimplemented ViewModel branch-level tests.
- [x] **Step 6: Commit** with `git add feature/settings core/ai-runtime && git commit -m "feat: simplify AI provider settings and activity"`.

### Task 3: General activity history and NexaFlow SMS event persistence

**Files:**
- Create: `core/database/src/main/java/com/nexaflow/core/database/SmsActivityEntity.kt` and corresponding DAO.
- Modify: `core/database/src/main/java/com/nexaflow/core/database/AppDatabase.kt` and `Migrations.kt`; add Room schema snapshot and migration test.
- Create: SMS activity repository contract/implementation following current `domain` and `data` repository patterns.
- Modify: `core/automation-engine/src/main/java/com/nexaflow/core/engine/SmsReceiver.kt` and consent/send result path only at NexaFlow-owned receive/send/processing points.
- Create/modify: `feature/history` SMS activity screen/state and general activity destination composing execution, blocked-call, and SMS histories.
- Modify: `feature/settings/src/main/java/com/nexaflow/feature/settings/SettingsScreen.kt`, `feature/settings/src/main/java/com/nexaflow/feature/settings/SettingsDestination.kt`, and `app/src/main/java/com/nexaflow/app/NexaFlowApp.kt` for dedicated navigation.
- Test: `core/database/src/test/java/com/nexaflow/core/database/MigrationTest.kt`, SMS activity repository/engine tests, and `feature/history` screen/ViewModel tests.

**Interfaces:**
- Consume `HistoryRepository`, current `BlockedCallsViewModel` history flow, Room migration registration, and existing SMS receiver/consent APIs.
- Add an `SmsActivityRepository` flow and bounded event record containing event id, event kind/direction, automation/execution reference if available, timestamp, outcome/error code, and redacted metadata only; no message body or API secret.

- [x] **Step 1: Write migration, repository, and SMS handling tests** for event insertion, newest-first bounded retrieval, clear/retention, metadata redaction, success/failure outcomes, and duplicate delivery suppression; assert manifest has no `READ_SMS` permission.
- [x] **Step 2: Run focused database/data/engine tests** with `./gradlew :core:database:testDebugUnitTest :data:testDebugUnitTest :core:automation-engine:testDebugUnitTest`. Expected: migration and SMS event tests fail because the new record/store is not implemented.
- [x] **Step 3: Add Room schema migration** from version 22 to 23 (or the next actual database version after rechecking HEAD), entity, indexed DAO, repository binding, bounded retention, and schema snapshot.
- [x] **Step 4: Record SMS events only at NexaFlow-owned processing points**, recording outcome and references without message body; preserve idempotency and ensure receive/send event boundaries are semantically accurate.
- [x] **Step 5: Re-run focused database/data/engine tests** using the command in Step 2. Expected: migration, redaction, outcomes, and deduplication assertions pass.
- [x] **Step 6: Commit** with `git add core/database data domain core/automation-engine && git commit -m "feat: record NexaFlow SMS activity safely"`.

### Task 4: Dedicated general activity screen and navigation integration

**Files:**
- Modify: `feature/history/src/main/java/com/nexaflow/feature/history/HistoryScreen.kt` or create a dedicated `ActivityHistoryScreen.kt` and ViewModel.
- Modify: `feature/settings/src/main/java/com/nexaflow/feature/settings/SettingsScreen.kt` and `SettingsDestination.kt`.
- Modify: `app/src/main/java/com/nexaflow/app/NexaFlowApp.kt`.
- Test: `feature/history/src/test/java/com/nexaflow/feature/history/HistoryScreenTest.kt` and new activity navigation/screen tests.

**Interfaces:**
- Consume existing execution history route, blocked-call flow, and the `SmsActivityRepository` created in Task 3.
- Produce one Settings activity destination with clear sections/tabs for executions, blocked calls, and NexaFlow SMS; old direct routes remain valid or redirect safely.

- [x] **Step 1: Add history screen/navigation tests** for all activity sections, empty/loading/error states, existing execution details navigation, and compatibility with `history` and `blocked_calls` deep links.
- [x] **Step 2: Run focused history tests** with `./gradlew :feature:history:testDebugUnitTest`. Expected: failures for the new SMS and unified activity navigation.
- [x] **Step 3: Add dedicated Settings activity destination** that composes existing execution and blocked-call content with the SMS event list, preserving filters and existing details behavior.
- [x] **Step 4: Update Settings navigation entries** so execution and blocked-call records move under the activity destination while old routes keep working; add SMS activity navigation and translations.
- [x] **Step 5: Re-run focused history tests** using the command in Step 2. Expected: all sections, state handling, and routes pass.
- [x] **Step 6: Commit** with `git add feature/history feature/settings app && git commit -m "feat: add unified automation activity history"`.

### Task 5: Whole-project verification and review

**Files:** All files changed in Tasks 1–4.

- [x] **Step 1: Run focused regression suites** for dashboard, builder, settings, AI runtime, database, data, automation engine, and history. Expected: all tests pass.
- [x] **Step 2: Run Android lint and debug build** with `./gradlew lintDebug assembleDebug`. Expected: both tasks complete successfully with the configured Android SDK.
- [x] **Step 3: Inspect merged manifest and permission regression** to confirm `READ_SMS` is absent and existing SMS permissions/receivers remain valid.
- [x] **Step 4: Review schema snapshots and migration tests** to confirm the new database version is migratable from the prior supported schema.
- [x] **Step 5: Inspect complete diff** for strings/localization completeness, secrets/message-body leakage, RTL floating action layout, accessibility semantics, and old route compatibility; resolve all critical/important findings.
- [x] **Step 6: Commit final fixes** with a scoped conventional commit and report exact commands/results; do not claim CI/release success unless remote CI is actually run and green.

## Self-review

- **Spec coverage:** Acceptance 1–2 -> Task 1; 3–4 -> Task 2; 5–7 -> Tasks 3–5. Data compatibility/security -> Tasks 2–5.
- **Placeholder scan:** No TODO/TBD placeholders. Every task states files, tests, commands, expected results, and interfaces; implementation-specific SMS schema values are deliberately bounded to event metadata and migration version is rechecked against the actual checkout before writing.
- **Type consistency:** `SmsActivityRepository` is introduced in Task 3 and consumed by Task 4; all existing route names remain available as compatibility aliases.
- **Review Focus coverage:** Provider preservation/discovery/manual fallbacks -> Task 2; RTL/insets -> Task 1; SMS failure/deduplication/redaction and permission -> Task 3 and Task 5; route and execution-history preservation -> Task 4.
- **Scope decomposition:** The dashboard/builder, provider settings, and persisted activity history are independently reviewable deliverables. Their sequencing is explicit; Task 4 consumes the repository contract from Task 3. If implementation discovers a provider or storage subsystem requires new user-facing scope, stop and update the spec before widening it.
