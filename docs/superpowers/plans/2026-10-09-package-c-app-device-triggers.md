# Package C App and Device Triggers Implementation Plan

> **For agentic workers:** Use the `executing-plans` skill to implement this plan task by task. Steps use checkbox syntax for tracking.

**Goal:** Harden and verify the existing app foreground, package install/update, screen/power, and USB trigger paths in issue #134 Package C.

**Architecture:** Keep the existing `APPLICATION`, `APP_INSTALLED`, `DEVICE`, and `USB_CONNECTED` trigger types and their monitor ownership. Add a pure package-broadcast classifier, route the app-install package filter through the existing app picker, and make `DEVICE.event` an enum matching actual editor/runtime events.

**Tech Stack:** Kotlin, Android broadcast APIs, Compose builder, domain trigger schemas, JUnit/Robolectric, Gradle, repository audit scripts.

**Spec:** `docs/superpowers/specs/2026-10-09-package-c-app-device-triggers.md`

## Global Constraints

- Do not add a new `TriggerType`, broad package-visibility permission, or polling loop.
- Keep existing saved `APPLICATION`, `APP_INSTALLED`, `DEVICE`, and `USB_CONNECTED` workflows compatible.
- Route lifecycle exits through the existing runtime store and `ExitCoordinator`.
- Treat unknown package broadcasts and unknown device event values as non-matches.
- Report device, emulator, and OEM validation as `NOT TESTED` unless observed.

## Review Focus

- A package-replacement remove broadcast must not run the update automation before the completed add broadcast.
- A normal uninstall must still match `REMOVED`, and an ordinary first install must still match `INSTALLED`.
- Selecting an `APP_INSTALLED` target must not overwrite the `APPLICATION.packages` list or change multi-select behavior.
- Existing persisted Bluetooth device event strings must remain in the `DEVICE.event` enum.
- USB `ON`/`OFF`, app foreground duplicate suppression, and restart-safe exit recovery must remain intact.

---

### Task 1: Define and test package broadcast classification

**Files:**
- Create: `core/automation-engine/src/main/java/com/nexaflow/core/engine/PackageEventClassifier.kt`
- Create: `core/automation-engine/src/test/java/com/nexaflow/core/engine/PackageEventClassifierTest.kt`
- Modify: `core/automation-engine/src/main/java/com/nexaflow/core/engine/PackageMonitor.kt`

**Interfaces:**
- Produces `internal enum class PackageTriggerEvent { INSTALLED, REMOVED, UPDATED }`.
- Produces `internal object PackageEventClassifier` with `fun classify(action: String?, replacing: Boolean): PackageTriggerEvent?`.

- [x] Write a table-driven test asserting `ACTION_PACKAGE_ADDED,false -> INSTALLED`, `ACTION_PACKAGE_ADDED,true -> UPDATED`, `ACTION_PACKAGE_REMOVED,false -> REMOVED`, `ACTION_PACKAGE_REMOVED,true -> null`, and an unrelated action -> null.
- [x] Run `.\gradlew.bat :core:automation-engine:testDebugUnitTest --tests com.nexaflow.core.engine.PackageEventClassifierTest --no-configuration-cache --no-daemon --console=plain`; confirm the test fails because the classifier does not exist.
- [x] Implement the classifier as a pure `when` over Android action strings and the replacement flag; keep event enum constants as the only returned values.
- [x] Update `PackageMonitor.receiver.onReceive` to classify once, return for null, then pass `event.name` and the package URI value into the existing handler.
- [x] Run the focused Gradle test and `git diff --check`; confirm classifier tests pass and the replacement remove half is ignored.

### Task 2: Type and test device trigger event configuration

**Files:**
- Modify: `domain/src/main/java/com/nexaflow/domain/catalog/TriggerNodeSchemas.kt`
- Modify: `domain/src/test/java/com/nexaflow/domain/catalog/TriggerNodeSchemasTest.kt`
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerSummary.kt` only if the test exposes an invalid-event fallback that needs alignment.

**Interfaces:**
- `TriggerType.DEVICE` schema exposes `event` as an enum with the eight exact strings in the spec, default `SCREEN_ON`, plus its existing `deviceName` and `deviceAddress` fields.
- `TriggerType.USB_CONNECTED` continues to use the existing shared `state` enum with `ON` and `OFF`.

- [x] Add assertions for the `DEVICE.event` field type, default, and complete allowed-value list, and for the unchanged USB `state` field.
- [x] Run `.\gradlew.bat :domain:testDebugUnitTest --tests com.nexaflow.domain.catalog.TriggerNodeSchemasTest --no-configuration-cache --no-daemon --console=plain`; confirm the new schema expectation fails first.
- [x] Replace only the `DEVICE.event` string field with the exact enum field; retain Bluetooth values and other fields.
- [x] Run the focused schema test and `.\gradlew.bat :domain:testDebugUnitTest --tests com.nexaflow.domain.catalog.TriggerNodeSchemasTest --tests com.nexaflow.domain.catalog.AutomationNodeCatalogTest --no-configuration-cache --no-daemon --console=plain`.

### Task 3: Route app-install target selection through the app picker

**Files:**
- Create: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerAppSelection.kt`
- Create: `feature/automation-builder/src/test/java/com/nexaflow/feature/builder/TriggerAppSelectionTest.kt`
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt`
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt`
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerSummary.kt` only if selected-app summary needs a picker-backed rendering update.

**Interfaces:**
- Produces `internal fun TriggerDraft.withPickedPackage(packageName: String): TriggerDraft`.
- For `APPLICATION`, the helper adds one exact package to the existing comma-delimited `packages` list without duplicates.
- For `APP_INSTALLED`, the helper stores exactly one package under `package` and preserves the selected event.
- Other trigger types return an unchanged draft.

- [x] Add tests for adding/deduplicating an `APPLICATION` package, replacing an `APP_INSTALLED` single package while retaining `event`, and leaving unrelated trigger drafts unchanged.
- [x] Run `.\gradlew.bat :feature:automation-builder:testDebugUnitTest --tests com.nexaflow.feature.builder.TriggerAppSelectionTest --no-configuration-cache --no-daemon --console=plain`; confirm expected missing-symbol failures.
- [x] Implement the helper and adapt the existing trigger picker target to use multi-select only for `APPLICATION` and single-select for `APP_INSTALLED`.
- [x] Replace the raw `APP_INSTALLED.package` text field with the existing app picker button and show the selected package in the editor; do not add package enumeration permissions.
- [x] Run the focused test and `.\gradlew.bat :feature:automation-builder:compileDebugKotlin --no-configuration-cache --no-daemon --console=plain`.

### Task 4: Verify and document Package C evidence

**Files:**
- Create: `docs/evidence/p1-11-connectivity-package-c-2026-10-09.md`
- Modify: `docs/superpowers/plans/2026-10-09-trigger-customization-packages.md`
- Modify: `CHANGELOG.md` under `[Unreleased]`.

**Interfaces:**
- Evidence records commands and exit codes, exact commit/CI URL when available, package-transition semantics, and every untested environment layer.
- The eight-package plan marks C locally implemented only after its focused tests pass; #134 stays open until A–H and the issue acceptance criteria are complete.

- [x] Run focused automation-engine, domain, and builder tests; then run `python scripts/audit_atomic_inventory.py --check`, `python scripts/check_strings_parity.py`, `python scripts/check_resources.py`, and `git diff --check`, recording their exit codes and concise outputs.
- [x] Record that dynamic package broadcasts are process-lifetime inputs and that emulator/device/OEM results are `NOT TESTED` unless the CI run supplies them.
- [x] Add an English `[Unreleased]` entry for app/device trigger hardening without altering the published `v3.91.12` section.
- [ ] Run a final `git diff --check`, inspect `git status --short`, commit only the Package C change, push the branch, open one PR for Package C, and wait for exact-SHA CI before merge.
