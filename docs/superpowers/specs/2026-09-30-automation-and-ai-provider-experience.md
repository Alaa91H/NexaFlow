# Automation Discovery and AI Provider Experience

## Status

Design approved by the user on 2026-09-30. Implementation has not started.

## Goals

1. Show trigger and execution discovery directly in their separate automation-builder tabs.
2. Remove the separate “Common/Popular” tier while keeping the complete trigger and execution catalogs separately categorized.
3. Display human-readable mobile network generations and options instead of unknown framework enum names.
4. Support ready-to-add OpenAI, Claude, Gemini, and OpenCode Zen profiles; the user supplies an API key and adds the profile.
5. Support manually configured providers and an adapter registry that can grow to additional protocols/providers.
6. Let users verify connectivity and credentials and see a clear result without exposing secrets.

## User-approved product decisions

- The automation builder uses distinct Trigger and Execution tabs.
- Trigger and execution lists appear directly in the corresponding tab; no combined list.
- Remove “Common/Popular” entirely. Keep category browsing/search and the existing advanced options where applicable.
- Provider presets: OpenAI, Claude (Anthropic API), Gemini, and OpenCode Zen.
- Include a manual provider form for future endpoints/providers.
- For OpenCode, include both the OpenCode Zen preset and manual custom-provider support.
- The common preset flow requires the user API key and Add action; verification is available from a Verify button.

## Proposed implementation architecture

### Automation builder

- Retain the separate builder sections/tabs for triggers and executions.
- Render catalog browsing inline in each corresponding section so options are immediately visible.
- Keep trigger and execution catalogs, categories, search, availability, selection, and configuration separate.
- Remove the Common/Popular tier and duplicate common lists, including all now-unused localized resources.
- Preserve the existing advanced discovery mechanism and catalog behavior outside this scope.

### Cellular-network labels

- Keep stable persisted values and framework values needed by runtime matching.
- Translate legacy/mobile network enum integers to the supported generation vocabulary (2G/3G/4G/5G) before rendering; unknown values get a localized “Unknown” label, never a raw enum/debug string.
- Ensure picker values map back to the existing trigger configuration contract.
- The exact bad value path must be confirmed during implementation; screenshots show legacy enum labels such as LEGACY_3347 and LEGACY_3247.

### AI provider profiles

- Replace the single-profile persistence assumption with a bounded collection of provider profiles and a selected/active profile reference, while preserving migration from the current single OpenAI-compatible settings.
- Give each profile a stable ID, catalog ID, display name, protocol/adapter ID, endpoint, model ID, locality, and enabled/configuration state.
- Store each provider API key in `SecureStorage` under a per-profile key. Never persist API-key material in DataStore or UI state; only expose whether a key exists.
- Use provider catalog metadata for presets (display name, endpoint, protocol, default/model discovery behavior), not provider-specific conditionals in the settings screen.
- Use adapter implementations for native Anthropic Messages and any non-compatible protocols. Use compatible transport only where the official endpoint supports it. Register adapters behind the existing runtime registry boundary.
- Include presets for OpenAI, Anthropic Claude, Gemini, and OpenCode Zen. OpenCode Zen is distinct from a manually configured OpenAI-compatible endpoint.
- A manual profile includes display name, protocol, endpoint, model, and API key. Initial manual protocols should include OpenAI Chat Completions-compatible and Anthropic Messages if feasible with the adapter contract; unsupported protocols must not be silently treated as compatible.
- Add the profile on Add; keep Verify available as a separate operation, and show testing/success/failure state with actionable, localized error text that does not include credentials.
- Keep routing selection and cloud fallback explicit; adding a profile must not silently enable cloud fallback or select it for execution.

### Security and compatibility

- Reuse existing endpoint validation and secure storage mechanisms; extend protocol-aware endpoint validation without weakening restrictions.
- Never log, render, or include API keys in errors/telemetry.
- Migrate existing settings and key without loss; preserve current behavior until a profile is explicitly selected/enabled.
- Bound collection sizes and text field lengths. Validate endpoint schemes and model IDs before saving or making requests.

## Acceptance criteria

### Builder

- Trigger and execution tabs remain distinct and their available options are immediately visible.
- No “Common/Popular” header/list is rendered in either tab in any locale.
- Search, category filters, selected state, capability gating, advanced options, configuration, and save behavior continue to work.
- Network-mode picker displays localized 2G/3G/4G/5G/AUTO labels; unknown platform values display a localized unknown label, never enum/debug codes.
- Saved network trigger values continue to match runtime generation values.

### Provider management

- Presets for OpenAI, Claude, Gemini, and OpenCode Zen populate their defaults and require only the user's API key plus Add for the ordinary add flow.
- A successful or failed Verify operation updates per-profile status and reports useful detail without key disclosure.
- Manual endpoint/model/protocol configuration can be added, verified, selected, edited, and removed.
- Provider registry supports adding an adapter without rewriting the settings screen.
- Secrets remain in secure storage and existing single-provider settings migrate without losing configuration.
- Provider selection is explicit and cloud fallback remains off unless explicitly enabled.

## Verification plan

- Unit tests for provider profile serialization/migration, stable IDs, secret key separation, protocol/preset metadata, adapter routing, endpoint validation, verification outcomes, and safe error formatting.
- UI tests or composable tests for inline builder catalogs, category separation, removal of the common tier, provider add/verify/error/selection flows.
- Run owning module tests/compilation and full Android build/lint/CI. Review actual GitHub Actions results before reporting success.
- Where available, manually inspect screenshots/emulator behavior for compact and RTL Arabic layouts; confirm the three screenshot-driven issues are visibly corrected.

## Out of scope

- No server-side proxy or hosted credential storage.
- No automatic provider fallback or silent selection.
- No claim that a provider is operational until the Verify request succeeds for the supplied credentials and chosen model/protocol.
- No release publishing as part of the feature unless separately requested after implementation and validation.

## Open implementation checks

- Inspect how `LEGACY_*` labels are constructed and keep the underlying trigger config contract compatible.
- Confirm OpenCode Zen endpoint/model discovery against current official docs at implementation time.
- Confirm current Anthropic model-discovery and verification APIs at implementation time.
- Confirm migration/versioning strategy for provider DataStore without resetting existing user configuration.
