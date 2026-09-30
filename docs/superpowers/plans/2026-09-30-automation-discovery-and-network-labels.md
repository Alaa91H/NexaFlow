# Automation Discovery and Network Labels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore directly visible, separate trigger and execution discovery, remove Common/Popular tiers, and render understandable network mode labels without breaking stored automation semantics.

**Architecture:** Keep the existing guided builder and catalog/data model. Change only how discovery is presented and how framework network values are translated to UI labels; preserve the automation configuration vocabulary consumed by runtime matching.

**Tech Stack:** Kotlin, Jetpack Compose, Android resources, JUnit, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-30-automation-and-ai-provider-experience.md` (Goals, User-approved product decisions, Automation builder, Cellular-network labels, Builder acceptance criteria).

## Global Constraints

- Keep trigger and execution lists separate.
- Remove Common/Popular UI and duplicate lists in every shipped locale.
- Preserve search, categories, selection, availability gating, advanced discovery, configuration, and save behavior.
- Keep stable persisted values and framework values needed by runtime matching.
- Unknown platform values show a localized unknown label, never raw enum/debug strings.

## Review Focus

- RTL Arabic with tabs, search, and long category labels: verify direct discovery does not clip or reverse trigger/action grouping.
- Empty catalog search: show an intentional empty state and keep the Add/selection flow disabled.
- Unsupported capability: keep blocked rows and permission affordances intact after removing the Common tier.
- Advanced-only option: keep it discoverable and gated by its current disclosure control.
- Unknown network integer or display override: render localized Unknown and do not persist `LEGACY_*` text as the trigger state.

---

### Task 1: Pin builder discovery behavior

**Files:**
- Modify: `feature/automation-builder/src/test/java/com/nexaflow/feature/builder/AutomationOptionCatalogTest.kt` or the existing builder UI/source contract test selected after inspection.
- Test: same owning test source.

**Interfaces:**
- Consumes: existing `AutomationOptionCatalog`, trigger/action catalogs, and screen text resources.
- Produces: regression assertions that trigger and execution discovery remain independent and no Common/Popular tier is part of the active UI contract.

- [ ] **Step 1: Add failing assertions** that catalog discovery is categorized separately for triggers and actions and that the Common tier is not required to enumerate normal options.
- [ ] **Step 2: Run the owning test** with `.\gradlew.bat :feature:automation-builder:testDebugUnitTest --tests '*AutomationOptionCatalogTest'` and confirm the relevant assertion fails against current source behavior.
- [ ] **Step 3: Adjust the assertion to the concrete screen/source contract** after inspecting existing test infrastructure; avoid adding a new UI testing dependency.
- [ ] **Step 4: Re-run the focused test** and confirm the regression is pinned before changing the UI.
- [ ] **Step 5: Commit** the focused test.

### Task 2: Show trigger discovery inline and remove Common tier

**Files:**
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt` trigger section around the `NodeConfiguratorSheet` and `option_tier_common` rendering.
- Modify: `feature/automation-builder/src/main/res/values/strings.xml` and all locale `strings.xml` files containing `option_tier_common` or now-unused sheet copy.
- Test: builder contract test established in Task 1.

**Interfaces:**
- Consumes: `supportedTriggers`, category mapping, search state, availability map, selected trigger state, `TriggerOptionRow`, and existing trigger configurator callback.
- Produces: directly visible trigger option browser within the When tab; selection continues to flow through `selectedTriggerTypes` and existing configuration callback.

- [ ] **Step 1: Remove Common list rendering** and render category/search discovery directly in the trigger section without the separate picker sheet.
- [ ] **Step 2: Preserve selection and configure behavior** by using the same selected set, availability handlers, advanced disclosure, and confirmation behavior already used by the current picker.
- [ ] **Step 3: Remove the Common/Popular resource key** from base and translated resource files after all references are removed; do not remove the all/category/advanced labels.
- [ ] **Step 4: Run** `.\gradlew.bat :feature:automation-builder:testDebugUnitTest` and `.\gradlew.bat :feature:automation-builder:compileDebugKotlin`.
- [ ] **Step 5: Commit** the trigger UI and resource change.

### Task 3: Show execution discovery inline and remove Common tier

**Files:**
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt` execution section around action `NodeConfiguratorSheet` and `option_tier_common` rendering.
- Modify: locale resources only where Task 2 did not already remove shared Common/Popular strings.
- Test: builder contract test established in Task 1.

**Interfaces:**
- Consumes: action options/catalog/categories, action search, availability map, selected action types, current action defaults, `ActionOptionRow`.
- Produces: directly visible execution option browser within the Do tab; selected actions still become `ActionDraft`s through existing logic.

- [ ] **Step 1: Remove Common action list rendering** and render the action catalog with its own search/categories inline.
- [ ] **Step 2: Preserve action availability/permission handlers, multi-select behavior, defaults, and advanced disclosure.**
- [ ] **Step 3: Verify no `option_tier_common` UI reference remains** using `rg -n "option_tier_common|commonTriggerOrder|commonActionOrder" feature/automation-builder/src` and remove obsolete catalog helpers only if unused.
- [ ] **Step 4: Run** `.\gradlew.bat :feature:automation-builder:testDebugUnitTest` and `.\gradlew.bat :feature:automation-builder:compileDebugKotlin`.
- [ ] **Step 5: Commit** the execution UI change.

### Task 4: Correct network mode display mapping

**Files:**
- Modify: `core/common/src/main/java/com/nexaflow/core/common/CellularNetworkReader.kt` only if platform-to-generation mapping is missing values.
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt` at network mode option labels if raw config is displayed.
- Modify: localized string resources used by the network picker for Unknown if absent.
- Test: `core/common/src/test/java/com/nexaflow/core/common/CellularNetworkReaderTest.kt` and builder test for displayed labels.

**Interfaces:**
- Consumes: raw platform type/override and stable `AUTO`, `2G`, `3G`, `4G`, `5G` configuration constants.
- Produces: localized display string mapping; persisted/runtime config remains one of the stable trigger vocabulary values.

- [ ] **Step 1: Add tests** for legacy cellular integer values mapping to 2G/3G/4G/5G, known LTE/NR override behavior, and unknown integers mapping to null/Unknown without leaking raw values.
- [ ] **Step 2: Run** `.\gradlew.bat :core:common:testDebugUnitTest --tests '*CellularNetworkReaderTest'` and confirm any new mapping case fails before implementation.
- [ ] **Step 3: Implement the smallest explicit mapping** based on Android `TelephonyManager` constants; never derive labels from `Int.toString()` or enum/debug serialization.
- [ ] **Step 4: Ensure picker state values are stable generation tokens** and labels are localized separately. Add an Unknown resource only if unknown values can appear in the picker.
- [ ] **Step 5: Run** `.\gradlew.bat :core:common:testDebugUnitTest --tests '*CellularNetworkReaderTest'`, `.\gradlew.bat :feature:automation-builder:testDebugUnitTest`, and module compilation.
- [ ] **Step 6: Commit** the mapping and test changes.

### Task 5: Verify builder behavior and Arabic layout

**Files:**
- Review: changed builder and resource files from Tasks 2–4.
- Test: existing module tests; UI screenshot/manual check if the environment has an emulator.

**Interfaces:**
- Consumes: integrated builder discovery and localized network label changes.
- Produces: verified acceptance evidence for separate inline catalogs and stable network configuration.

- [ ] **Step 1: Run full module checks** with `.\gradlew.bat :feature:automation-builder:testDebugUnitTest :feature:automation-builder:compileDebugKotlin :core:common:testDebugUnitTest`.
- [ ] **Step 2: Inspect resource parity and references** with the repository’s resource validation command documented in `.github/workflows/android-ci.yml` and `rg` for removed Common-tier strings.
- [ ] **Step 3: If an emulator is available, inspect Arabic RTL and English screens** at phone width: separate tabs, visible categories, search, selected options, and legible generation names.
- [ ] **Step 4: Record actual command outcomes and remaining device checks**; do not infer screenshot validation from compilation.
- [ ] **Step 5: Commit** any final fix and report the exact verification evidence.
