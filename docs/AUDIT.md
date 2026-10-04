# NexaFlow source audit — 2026-10-04

This is a checkout-specific audit for the Master Execution Plan. It separates source/test evidence from device and service-side evidence. The checkout is based on `811f1430098cd496ed424bceebe6ff81409ffe27` (`v3.91.8`, `origin/main`) with an in-progress security-plan change to release signing; this is not a clean-main baseline.

## Baseline and repository facts

| Area | Current evidence | Status |
|---|---|---|
| README/catalog claims | `README.md` says Android 8/API 26 and points to a generated catalog. `docs/CAPABILITY_CATALOG.md` currently reports 57 trigger enum entries (55 general-picker entries) and 180 action enum entries. | `REAL` for documented source counts; runtime/device capability is not inferred |
| Catalog-to-builder parity | `python scripts/audit_catalog_and_releases.py catalog` reports `CATALOG_PARITY: OK — 57 triggers (55 exposed) and 180 actions all appear exactly once in the builder.` | `REAL` for enum/picker parity |
| Locale resource parity | `python scripts/check_strings_parity.py` reports `PARITY_PROBLEMS: 0`. | `REAL` for key parity; this does not establish translation quality |
| Resource hygiene | `python scripts/auto_fix.py --check` exited successfully with no output. | `REAL` for this gate only |
| Action dispatch | `core/execution/.../HandlerDispatchE2ETest.kt` asserts every `ActionType` is covered by `ActionRegistry.default()`; actual outcome and platform support still vary by action/device. | `REAL` for registry coverage; per-device outcome `UNTESTED` |
| Trigger runtime coverage | Builder/catalog parity is gated. Platform event adapters and monitors are distributed across `core/automation-engine`, `app`, and canonical source registries; no current single report maps all 57 enum values to live adapters and device tests. | `PARTIAL`; complete per-trigger source/test matrix remains open |
| Semantic execution | `docs/ARCHITECTURE.md`, `docs/architecture/capability-adaptive-execution.md`, `OperationRegistryParityTest`, and `ExecutionEngineSemanticPreflightTest` document and test the shared `CapabilityRouter` path. Coverage does not imply every backend works on hardware. | `REAL` for listed contracts/tests; privileged/OEM device behavior `UNTESTED` |
| AI provider/runtime | `core/ai-runtime` contains streaming adapters for OpenAI-compatible, OpenAI Responses, Anthropic Messages, and Gemini; the in-app coordinator and `NexaFlowAiToolExecutor` route typed tools through the existing agent API controller/registry. Provider/network/device end-to-end outcomes are not certified by source inspection. | `REAL` source paths; physical end-to-end `UNTESTED` |
| External agent gateway | `core/agent-api` exposes authenticated REST/MCP paths; pairing and operation scopes are described in `docs/AGENT_PAIRING.md` and `docs/AGENT_SECURITY.md`. LAN is fail-closed until a TLS transport exists. | `REAL` source/documentation; remote-client/device TLS `UNTESTED` |
| Agent safety | `AgentGrantMode` includes `READ_ONLY`, `STANDARD`, timed/permanent full access and fail-closed `UNKNOWN`; `AgentAccessManagerTest` covers defaults, migration, token rotation/replay. Approval records and content hashes are wired through the app and execution boundary. | `REAL` source/tests; manual UX/device proof `UNTESTED` |
| Risk calculation/CI guardrails | `config/detekt/detekt.yml` configures `EmptyCatchBlock`, `SuspendFunSwallowedCancellation`, `TooGenericExceptionCaught`, and `SwallowedException`. `scripts/check_suppression_budget.py` passed at 83 `Suppress` and 42 `SuppressLint`. | `REAL` for configured static gates |
| Database safety | `docs/SECURITY.md`, `core/database` explicit migrations and migration tests document data-preserving migration policy. Current plan run still needs the full Gradle test result and current schema check. | `PARTIAL` pending this run's full evidence |
| Signing and secret files | `keystore/` contains only `keystore.properties.example`; `.gitignore` excludes `.jks`, `.keystore`, and `keystore.properties`. No actual local signing secret was found in the directory. Full Git-history secret scan has not yet run. | `PARTIAL`; history scan pending |
| Current validation document | `docs/VALIDATION.md` starts with the v3.91.8 checkout's current local verification and preserves the historical v3.74 record. | `REAL` for documented baseline; this worktree still needs its own CI run |
| Performance/OEM behavior | No connected device evidence was available during this audit; the master plan's soak, macrobenchmark, Perfetto, `ApplicationExitInfo`, Shizuku/Root and OEM matrix cannot be inferred from JVM/Gradle output. | `UNTESTED` |

## Trigger/action audit boundary

The generated catalog is authoritative for enum inventory and builder labels. Action dispatch has an executable registry-coverage test. The catalog parity gate checks all 57 trigger entries (55 exposed) and all 180 actions appear exactly once in the builder, but this is not proof that every trigger has a live source or every action has verified behavior on every API/OEM. A per-item runtime/source/permission/resource/test/device matrix is required before making stronger claims; until it exists, individual behavior beyond those gates remains `UNTESTED`.

The per-enum matrix is included at the end of this file. The Gradle module/main-source LOC and project dependency inventory is at [`evidence/baseline/module-inventory.md`](evidence/baseline/module-inventory.md).

## AI and agents

There are both an in-app LLM/tool path and an external agent API. In-app model calls are mediated by the provider adapters in `core/ai-runtime`; tool calls go through `ManagedAgentRunCoordinator` and `NexaFlowAiToolExecutor` into the shared `AgentMcpToolRegistry`/`AgentApiController` path, rather than a second automation executor. External clients pair and obtain scoped credentials through `core/agent-api` and `core/agent-security`. Agent-originated high/critical task changes are tied to approval state/content hashes at the app composition and execution boundary. Source and tests establish implementation paths, not successful network calls or complete behavior on a physical device. No real-device LLM execution record was collected in this audit.

| Component | Production path | Current test evidence | Device/end-to-end status |
|---|---|---|---|
| Provider adapters | `core/ai-runtime` contains OpenAI-compatible, OpenAI Responses, Anthropic Messages, and Gemini streaming adapters. | `OpenAiCompatibleProviderTest`, `AnthropicMessagesProviderTest`, `NativeAiProviderAdaptersTest`, `AiProviderAdapterContractTest`, `AiStructuredToolParserTest` | Live vendor/network behavior `NOT TESTED` here |
| Credentials/settings | `core/ai-runtime` credential contracts plus app vault-backed storage and `feature/settings` provider setup. | `VaultBackedAiCredentialStoreTest`, `AiProviderSetupPolicyTest`, `AiProviderDiagnosticsTest`, datastore preference tests | Keystore restore and live provider probe on phone `NOT TESTED` |
| Chat UI | `feature/ai` chat screen and view model. | `AiChatViewModelTest` | Full chat using a real configured provider `NOT TESTED` |
| In-app agent/tools | `ManagedAgentRunCoordinator` → `NexaFlowAiToolExecutor` → shared `AgentMcpToolRegistry`/`AgentApiController`. | `ManagedAgentRunCoordinatorTest`, `AgentMcpToolRegistryTest`, `AgentMcpControllerTest`, `ExternalIngressIntegrationTest` | On-device plan/approval/action/history round trip `NOT TESTED` |
| External API/pairing | `core/agent-api` HTTP/MCP routes and `core/agent-security` scoped credential manager. | Parser/host-policy/controller tests; `AgentAccessManagerTest`; `AGENT_PAIRING.md` | External client/network pairing and TLS `NOT TESTED`; LAN remains disabled until TLS |
| Grant/approval/risk enforcement | `AgentGrantMode`, `AgentAccessManager`, app approval coordinator and execution validator wiring in `app/.../di/AppModule.kt`. | Mode/migration/token-replay tests, coordinator and execution content-hash tests | Manual approval UX and risky device action `NOT TESTED` |

Conclusion for P0-03: both an in-app LLM/tool workflow and an external agent gateway exist in source. Neither is device-certified until a real-device execution record and the required failure-path evidence are collected.

## Open baseline items

1. Baseline command logs and module inventory are recorded under `docs/evidence/baseline/`; the local release experiment was interrupted before an APK was produced, so it is not a successful release build.
2. The source inventory covers Gradle modules and every trigger/action enum. Runtime outcomes remain partial until behavior-level and device evidence is gathered.
3. Run a full-history `gitleaks` or `trufflehog` scan when available; the current release manifest was inspected, but no new production-signed APK was built in this worktree.
4. Record P0-04 as `NOT TESTED` until a named physical device and measured performance/soak evidence are available.
5. After changes are committed and pushed, append their own CI run and release evidence; do not substitute the successful baseline run for validation of this worktree.

## Commands already executed in this audit

| Command | Result |
|---|---|
| `python scripts/auto_fix.py --check` | Exit 0; no output |
| `python scripts/check_strings_parity.py` | Exit 0; `PARITY_PROBLEMS: 0` |
| `python scripts/audit_catalog_and_releases.py catalog` | Exit 0; 57 triggers (55 exposed), 180 actions, builder parity OK |
| `python scripts/check_suppression_budget.py` | Exit 0; 83 `Suppress`, 42 `SuppressLint` |
| `:app:tasks --all --offline --no-daemon` | `BUILD SUCCESSFUL`; proves Gradle configuration/task discovery only |
| `:app:assembleRelease --offline --no-daemon` | Failed at release artifact packaging with the intended missing-production-signing guard; no APK produced |
| `:app:assembleRelease -PallowDebugSigning=true --offline --no-daemon` | Interrupted during R8; no completed APK evidence, so not counted as a successful build |

## Per-enum source inventory (generated 2026-10-04)

These states describe the strongest evidence established by this audit. `UNTESTED` means source/picker presence alone did not prove an event reaches a working handler on a device. `PARTIAL` means registry or catalog coverage is tested while individual runtime outcomes remain unverified.

### Triggers (57)

| TriggerType | Status | Evidence / limitation |
|---|---|---|
| `TIME` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `BATTERY` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `APPLICATION` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `DEVICE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `CONNECTIVITY` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `WIFI_CONNECTED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `MOBILE_DATA_CONNECTED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `HOTSPOT` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `LOCATION` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `SMS` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `BLUETOOTH_DEVICE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `RINGER_MODE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `NETWORK_MODE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `NOTIFICATION` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `CALENDAR` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `SENSOR` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `WEBHOOK` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `ROM_SETTING` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `HEADPHONE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `CHARGER` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `AIRPLANE_MODE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `DARK_MODE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `CALL_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `INCOMING_CALL` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `APP_INSTALLED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `MEDIA_PLAYING` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `VOLUME_CHANGED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `POWER_SAVER` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `BLUETOOTH_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `BRIGHTNESS_LEVEL` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `STORAGE_LOW` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `AUTO_ROTATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `DATA_SAVER_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `DEVICE_LOCKED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `WIFI_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `NFC_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `LOCATION_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `SCREEN_ROTATION_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `WIFI_SIGNAL_STRENGTH` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `CELL_SIGNAL_STRENGTH` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `BATTERY_TEMPERATURE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `USB_CONNECTED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `HDMI_CONNECTED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `ETHERNET_CONNECTED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `VPN_CONNECTED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `CLIPBOARD_CHANGED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `DND_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `STAY_AWAKE_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `AUTO_BRIGHTNESS_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `SCREEN_TIMEOUT_CHANGED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `DATA_ROAMING_STATE` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `TIMEZONE_CHANGED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `BOOT_COMPLETED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `NFC_TAG_SCANNED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `ALARM_SET_CHANGED` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `WEAR_EVENT` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |
| `PLUGIN_EVENT` | `UNTESTED` | Builder/catalog inventory only; per-trigger live source and device path not certified. See `docs/CAPABILITY_CATALOG.md` and `core/automation-engine` tests. |

### Actions (180)

| ActionType | Status | Evidence / limitation |
|---|---|---|
| `SYSTEM_BRIGHTNESS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_VOLUME` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_STREAM_VOLUME` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_DND` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SCREEN_ROTATION` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_APP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SEND_NOTIFICATION` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_BLOCK_NOTIFICATION` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_CLEAR_APP_NOTIFICATIONS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_WIFI` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_BLUETOOTH` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_FLASHLIGHT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_AIRPLANE_MODE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_MEDIA_PLAY_PAUSE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_MEDIA_NEXT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_MEDIA_PREVIOUS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_URL` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_CLEAR_NOTIFICATIONS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_EXPAND_STATUS_BAR` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_COLLAPSE_STATUS_BAR` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SCREEN_TIMEOUT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_STAY_AWAKE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_AUTO_BRIGHTNESS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_RINGER_MODE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_MOBILE_DATA` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_NETWORK_MODE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_PRIVATE_DNS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_HOTSPOT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_NFC` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_POWER_SAVER` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_ANIMATIONS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_LOCK_SCREEN` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SET_ALARM` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SET_TIMER` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_DARK_MODE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_RECENTS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_GO_HOME` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `APPLICATION_OPEN_APP_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_RING_VOLUME` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SET_RINGTONE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_LOCATION` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_UPDATE_GOOGLE_PLAY_APPS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_PLAY_UPDATES` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_DEVICE_STORE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SEND_SMS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SMS_REPLY` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SMS_BLOCK_INCOMING` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SEND_REMINDER` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_WAIT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `BATTERY_ALERTS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `BATTERY_CHARGING_NOTIFICATIONS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `APPLICATION_LAUNCH_APP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `APPLICATION_CLOSE_APP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ADVANCED_SHIZUKU` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ADVANCED_ROOT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_HTTP_REQUEST` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `PLUGIN_FIRE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_VIBRATE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_WAKE_SCREEN` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_CLIPBOARD_SET` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_MEDIA_STOP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_NOTIFICATIONS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_QUICK_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SET_SETTING` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SCREENSHOT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_INPUT_TEXT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_KEY_EVENT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_INPUT_TAP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_INPUT_SWIPE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_COLOR_INVERSION` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_GRAYSCALE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_EXTRA_DIM` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_NIGHT_LIGHT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_HAPTIC_FEEDBACK` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SOUND_EFFECTS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_FORCE_STOP_APP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_CLEAR_APP_DATA` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_LOCATION_MODE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_DATA_SAVER` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_FONT_SCALE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_DISPLAY_DENSITY` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SCREENSAVER` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_BATTERY_SAVER_THRESHOLD` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_CHARGING_LIMIT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_CHARGING_FEEDBACK` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_ALWAYS_ON_DISPLAY` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SHOW_TAPS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_POINTER_LOCATION` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_ADAPTIVE_BATTERY` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_WIFI_SLEEP_POLICY` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_BLUETOOTH_DISCOVERABILITY` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_AUTO_TIME` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_AUTO_TIMEZONE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_HAPTIC_INTENSITY` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_CAMERA_SHUTTER_SOUND` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_WIFI_SCANNING` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_WIFI_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_BLUETOOTH_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_LOCATION_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_DATA_USAGE_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_BATTERY_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_DISPLAY_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_SOUND_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_STORAGE_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_SECURITY_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_ACCESSIBILITY_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_APP_SETTINGS_LIST` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_ABOUT_PHONE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_MEDIA_FAST_FORWARD` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_MEDIA_REWIND` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_DIAL_NUMBER` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_CAMERA` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_PLAY_STORE_APP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_MEDIA_PLAY_FROM_SEARCH` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_REBOOT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SHUTDOWN` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_RESTART_SYSTEM_UI` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_TOAST` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_ALERT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_VIBRATE_PATTERN` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_PASTE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_APP_DRAWER` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_TOGGLE_PIP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_WIFI_CONNECT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_WIFI_FORGET` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_DATA_ROAMING` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SCREENSAVER_TIMEOUT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_POINTER_SPEED` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_INSTALL_APK` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_UNINSTALL_APP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_DISABLE_APP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_ENABLE_APP` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SET_NOTIFICATION_TONE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_CALL_VIBRATION` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_NETWORK_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_NFC_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_DATA_SAVER_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_DEVELOPER_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_MAPS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SOFT_RESTART` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_STATUS_BAR_TOGGLE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_CONTACTS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SEND_EMAIL` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_NOTIFICATION_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_PRIVACY_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_CAST_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_INPUT_METHOD_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_DEFAULT_APPS_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_VPN_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_DATE_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_PRINT_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_USAGE_ACCESS_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_BLUETOOTH_SCAN` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_WIFI_SCAN_NOW` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `SYSTEM_SET_TIMEZONE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `CALL_BLOCK` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `CALL_BLOCK_SILENT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `CALL_REPLY_WITH_SMS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `CALL_SILENCE` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ROM_CUSTOM_SETTING` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ROM_QS_TILES` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ROM_STATUS_BAR` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ROM_LOCKSCREEN` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ROM_NAVIGATION` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ROM_THEME` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ROM_AMBIENT_AOD` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ROM_NOTIFICATIONS` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `ROM_BATCH` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `DATA_TEXT` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `DATA_ENCODING` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `DATA_HASH` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `DATA_RANDOM` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `DATA_MATH` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `DATA_DATE_TIME` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `DATA_JSON` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
| `DATA_ARRAY` | `PARTIAL` | Registered/picker parity is gated; behavior-specific coverage and device outcome require per-action review. See `HandlerDispatchE2ETest.kt` and `docs/CAPABILITY_CATALOG.md`. |
