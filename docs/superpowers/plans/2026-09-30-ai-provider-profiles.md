# AI Provider Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add extensible provider profiles for OpenAI, Claude, Gemini, OpenCode Zen, and manual endpoints with secure per-provider keys and a clear Verify flow.

**Architecture:** Extend the existing `core:ai-runtime` provider abstraction and registry with catalog-backed profiles and protocol adapters. Persist non-secret provider metadata in DataStore, keep API keys in `SecureStorage`, migrate the current single provider, and expose profile operations through settings ViewModel/UI.

**Tech Stack:** Kotlin, Android DataStore, SecureStorage, Hilt, Jetpack Compose, OkHttp/current transport, kotlinx serialization, JUnit, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-30-automation-and-ai-provider-experience.md` (AI provider profiles, Security and compatibility, Provider management acceptance criteria).

## Global Constraints

- Presets include OpenAI, Anthropic Claude, Gemini, and OpenCode Zen.
- Manual providers require a name, protocol, endpoint, model, and key; unsupported protocols cannot be silently treated as compatible.
- Store secrets only in `SecureStorage`; DataStore and UI state contain no API key material.
- Existing single-provider configuration migrates without losing its endpoint/model/key.
- Adding a provider does not silently select it or enable cloud fallback.
- Verify errors and telemetry never contain credentials.
- Preserve endpoint validation restrictions and bound profile counts and field lengths.

## Review Focus

- Existing user with legacy OpenAI configuration and key: migrate exactly once and do not duplicate profiles on repeated reads.
- Blank replacement key during edit: retain existing secret; explicit clear removes only that profile’s secret.
- Wrong protocol/endpoint combination: fail validation before sending credentials.
- Verify timeout, 401/403, unsupported model, malformed response: show safe actionable result without response headers/body containing secrets.
- Removed or changed active profile: keep routing deterministic and cloud fallback disabled unless explicitly enabled.

---

### Task 1: Model presets, protocols, and adapter boundary

**Files:**
- Create or modify: `core/ai-runtime/src/main/java/com/nexaflow/core/airuntime/AiProviderProfile.kt` for profile metadata and protocol identifiers.
- Create or modify: `core/ai-runtime/src/main/java/com/nexaflow/core/airuntime/AiProviderCatalog.kt` for preset metadata.
- Modify: `core/ai-runtime/src/main/java/com/nexaflow/core/airuntime/AiProviderRegistry.kt` for adapter registration/lookup.
- Test: `core/ai-runtime/src/test/java/com/nexaflow/core/airuntime/AiProviderCatalogTest.kt` and registry tests.

**Interfaces:**
- Consumes: existing `AiProviderDescriptor`, `AiProviderRegistry`, and `OpenAiCompatibleProvider` contracts.
- Produces: `enum class AiProtocol { OPENAI_CHAT_COMPLETIONS, ANTHROPIC_MESSAGES, GEMINI_OPENAI_COMPATIBLE, OPENCODE_ZEN }`; `data class AiProviderPreset(id: String, displayName: String, protocol: AiProtocol, baseUrl: String, defaultModelId: String, local: Boolean = false)`; an adapter interface with configure, probe, and model discovery operations.

- [ ] **Step 1: Add failing catalog tests** asserting stable IDs, display names, protocol, secure HTTPS preset endpoints and defaults for OpenAI, Claude, Gemini, and OpenCode Zen. Verify presets contain no API keys.
- [ ] **Step 2: Run** `.\gradlew.bat :core:ai-runtime:testDebugUnitTest --tests '*AiProviderCatalogTest'` and confirm the new types/tests fail to compile or assertions fail.
- [ ] **Step 3: Implement** protocol and preset metadata in the core runtime module; keep transport-specific logic out of the settings screen.
- [ ] **Step 4: Add registry tests** proving adapters resolve by protocol and unknown protocols fail closed.
- [ ] **Step 5: Run** `.\gradlew.bat :core:ai-runtime:testDebugUnitTest` and `.\gradlew.bat :core:ai-runtime:compileDebugKotlin`.
- [ ] **Step 6: Commit** catalog and adapter contracts.

### Task 2: Multi-profile persistence and one-time migration

**Files:**
- Modify: `core/datastore/src/main/java/com/nexaflow/core/datastore/AiProviderPreferences.kt`.
- Modify: `core/datastore/src/test/java/com/nexaflow/core/datastore/AiProviderPreferencesTest.kt`.
- Modify: `app/src/main/java/com/nexaflow/app/di/AiRuntimeModule.kt` for legacy migration dependencies only if ownership requires it.

**Interfaces:**
- Consumes: `AiProviderProfile` metadata serialized in a bounded versioned representation.
- Produces: `data class StoredAiProviderProfile(id: String, catalogId: String?, displayName: String, protocol: String, baseUrl: String, modelId: String, local: Boolean, enabled: Boolean)` and suspend operations `profiles(): Flow<List<StoredAiProviderProfile>>`, `upsert(profile)`, `remove(id)`, `selectedProfileId(): Flow<String?>`, `select(id: String?)`.

- [ ] **Step 1: Add failing DataStore tests** for profile round-trip, maximum count and lengths, invalid/corrupt JSON fallback, explicit selection, remove-selected behavior, and legacy single-profile migration exactly once.
- [ ] **Step 2: Run** `.\gradlew.bat :core:datastore:testDebugUnitTest --tests '*AiProviderPreferencesTest'` and verify the new operations fail as expected.
- [ ] **Step 3: Implement versioned, bounded metadata persistence**; migration reads the old setting keys and records a migration marker so repeated loads do not duplicate the profile.
- [ ] **Step 4: Ensure all persisted types exclude API-key values** and test serialized content against a known test secret.
- [ ] **Step 5: Run** the DataStore focused suite and module compile.
- [ ] **Step 6: Commit** profile persistence and migration.

### Task 3: Per-profile secure secrets and endpoint policy

**Files:**
- Modify: `core/ai-runtime/src/main/java/com/nexaflow/core/airuntime/OpenAiEndpointPolicy.kt` or add `AiEndpointPolicy.kt` for protocol-aware checks.
- Modify: `feature/settings/src/main/java/com/nexaflow/feature/settings/AgentSettingsViewModel.kt` for profile secret operations.
- Test: existing endpoint-policy tests and focused secret/policy tests in owning modules.

**Interfaces:**
- Consumes: stable profile ID, protocol, endpoint, secure storage, and existing endpoint policy.
- Produces: secret key function `providerApiKeyStorageKey(profileId: String): String`, plus validation of HTTPS remote endpoints, explicitly allowed local endpoints per current policy, protocol/URL compatibility, and supported lengths.

- [ ] **Step 1: Add failing tests** for per-profile key isolation, no secret in profile serialization/UI state, HTTPS requirement for remote endpoints, current local endpoint allowances, invalid URL rejection, and key-preserving blank edits.
- [ ] **Step 2: Run focused policy and settings tests** and verify failures are attributable to missing profile-key/policy behavior.
- [ ] **Step 3: Implement** stable per-profile key naming and protocol-aware URL validation by composing with existing restrictions; do not broaden private/local-network access.
- [ ] **Step 4: Add explicit remove-key behavior** that removes only the requested profile key.
- [ ] **Step 5: Run** `.\gradlew.bat :core:ai-runtime:testDebugUnitTest` and `.\gradlew.bat :feature:settings:testDebugUnitTest`.
- [ ] **Step 6: Commit** secure profile key and validation behavior.

### Task 4: Implement native provider adapters and probes

**Files:**
- Modify: `app/src/main/java/com/nexaflow/app/ai/AndroidOpenAiCompatibleTransport.kt` only for shared compatible protocols.
- Create: `app/src/main/java/com/nexaflow/app/ai/AndroidAnthropicMessagesTransport.kt` or the smallest protocol-specific source established by current transport patterns.
- Modify: `app/src/main/java/com/nexaflow/app/di/AiRuntimeModule.kt` to register built-in adapters.
- Modify/create: adapter tests in `core/ai-runtime` and app transport tests.

**Interfaces:**
- Consumes: protocol-specific config and API key from Task 3.
- Produces: working probes and model discovery for official OpenAI, Anthropic, Gemini-compatible, and OpenCode Zen APIs through adapters, using current official endpoint requirements verified at implementation time.

- [ ] **Step 1: Review current provider transport, error mapping, and official API docs** for authentication headers, model listing, and minimal non-billable verification request; identify a verification request that does not require generation where provider APIs support it.
- [ ] **Step 2: Add fake-transport tests** for each protocol’s URL, headers, request shape, successful verification, 401/403, timeout, malformed response, and redaction of key-bearing values.
- [ ] **Step 3: Implement or adapt transport** using provider-supported low-cost verification/model-listing endpoint; never send a key to a different host than the validated provider endpoint.
- [ ] **Step 4: Register adapters in Hilt** behind the core registry contract; ensure a new adapter can be added without settings UI changes.
- [ ] **Step 5: Run** `.\gradlew.bat :core:ai-runtime:testDebugUnitTest` and `.\gradlew.bat :app:testDebugUnitTest` plus owning app compile.
- [ ] **Step 6: Commit** adapters and protocol verification.

### Task 5: Provider settings ViewModel operations

**Files:**
- Modify: `feature/settings/src/main/java/com/nexaflow/feature/settings/AgentSettingsViewModel.kt`.
- Modify: settings ViewModel test file discovered in module test tree; create a focused test only if the project has no existing one.

**Interfaces:**
- Consumes: catalog, profiles DataStore, provider registry, secure storage, and adapter probe results.
- Produces: UI-safe list of profiles, selected profile ID, add/edit/remove/select/verify methods, per-profile verification state, and key-configured booleans; API key strings are input-only and never emitted in state.

- [ ] **Step 1: Add failing ViewModel tests** for add preset, manual add, explicit selection, key isolation, verify states, failure messaging, removal, and cloud fallback remaining disabled.
- [ ] **Step 2: Run the focused settings tests** and record expected failures.
- [ ] **Step 3: Implement** operations as coroutine-safe state transitions; validate before save, store key securely, save profile metadata, and refresh runtime descriptors only after successful operations.
- [ ] **Step 4: Ensure Add does not imply Verify, selection, or cloud fallback**; the Verify action can be used before or after saving without changing selection.
- [ ] **Step 5: Run** `.\gradlew.bat :feature:settings:testDebugUnitTest` and `.\gradlew.bat :feature:settings:compileDebugKotlin`.
- [ ] **Step 6: Commit** settings state and operations.

### Task 6: Provider management UI

**Files:**
- Modify: `feature/settings/src/main/java/com/nexaflow/feature/settings/AgentSettingsScreen.kt`.
- Modify: `feature/settings/src/main/res/values/strings.xml` and localized `strings.xml` files for provider names, labels, protocol selection, Add, Verify, testing/success/failure, and validation copy.
- Test: UI/composable tests in the existing settings module test setup.

**Interfaces:**
- Consumes: Task 5 UI state and operations.
- Produces: preset cards for OpenAI, Claude, Gemini, OpenCode Zen; manual provider form; list of configured providers with explicit select/edit/remove actions; API key input with visibility control and no state echo; Verify button/status.

- [ ] **Step 1: Add UI assertions** for all four preset cards, one manual option, key-only ordinary preset add flow, Verify state, failure state, and explicit selected profile.
- [ ] **Step 2: Run focused UI tests** and confirm missing sections/states fail.
- [ ] **Step 3: Implement preset selection** to prefill catalog metadata and request only API key before Add; expose Verify button with test-in-progress and safe result details.
- [ ] **Step 4: Implement manual fields** for display name, supported protocol, endpoint, model, and API key; ensure unsupported protocols cannot be selected.
- [ ] **Step 5: Add configured profile list** with explicit selection and edit/remove affordances; cloud fallback remains a separate existing setting.
- [ ] **Step 6: Add localized resources** and run resource parity/hardcoded-text validation.
- [ ] **Step 7: Run settings UI tests and compile**; inspect Arabic RTL layout when emulator is available.
- [ ] **Step 8: Commit** provider settings UI and localized resources.

### Task 7: End-to-end migration, registry selection, and verification

**Files:**
- Review and adjust: `app/src/main/java/com/nexaflow/app/di/AiRuntimeModule.kt`, `feature/settings` provider operations/UI, datastore migration tests, and existing routing policy tests.
- Add tests: app/runtime integration or settings integration test according to existing project infrastructure.

**Interfaces:**
- Consumes: all completed provider profile contracts and adapters.
- Produces: selected profile configures the runtime provider; old installations preserve behavior and secrets.

- [ ] **Step 1: Add integration test** that loads legacy settings, migrates metadata and key, selects the migrated profile, and configures the matching registry adapter.
- [ ] **Step 2: Add integration test** for adding and verifying each preset with a fake transport and for adding/verifying a manual compatible endpoint.
- [ ] **Step 3: Run targeted tests** for DataStore, AI runtime, settings, and app modules.
- [ ] **Step 4: Run full requested verification** with Android SDK configured: `.\gradlew.bat lintDebug testDebugUnitTest assembleDebug` and the repository CI workflow.
- [ ] **Step 5: Inspect test output and Actions job logs**; fix actual failures before reporting a pass.
- [ ] **Step 6: Commit** any integration fixes and document migration/verification evidence.

## Coverage check

- Preset catalogs/protocol expansion: Tasks 1 and 4.
- Manual provider: Tasks 1, 5, 6, and 7.
- Secure per-provider key and migration: Tasks 2, 3, and 7.
- Verify button, safe state/errors: Tasks 4, 5, 6, and 7.
- Explicit selection and no implicit cloud fallback: Tasks 5, 6, and 7.
- UI locale and extensibility: Tasks 1 and 6.
