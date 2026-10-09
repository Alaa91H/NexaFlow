# NexaFlow AI agents — A00 frozen baseline and evidence (2026-10-08)

**Issue:** [#150 / A00](https://github.com/Alaa91H/NexaFlow/issues/150). **Shared platform predecessor:** [#123 / T00](https://github.com/Alaa91H/NexaFlow/issues/123), implementation PR [#226](https://github.com/Alaa91H/NexaFlow/pull/226). **AI master:** [#149](https://github.com/Alaa91H/NexaFlow/issues/149); **platform master:** [#122](https://github.com/Alaa91H/NexaFlow/issues/122).

**Status:** `PARTIAL — DOCUMENTED / CODE REVIEW PENDING / CI PENDING` until this PR and T00's #226 pass their exact-commit required checks and the shared baseline is merged. This document describes **read-only source and GitHub API observations**, not a fresh local Gradle run or any real-model/device acceptance. A00 must **not** close independently ahead of T00's shared frozen baseline.

## 1. Frozen source identity

| Key | Evidence at read |
|---|---|
| Repository | `Alaa91H/NexaFlow`, default branch `main` |
| Verified `main` commit | `4199f4a1108b60cab05b3df017a10b2d24a0e2d8` |
| Main source tree | `9aec81ba0cc9d659d280c0e8f2e4130fefcc0a7d` |
| Main commit timestamp | 2026-10-05 06:36:40 UTC |
| Main commit subject | `test(app): guard backup policy and record P1 follow-up (#121)` |
| A00 work branch | `audit/2026-10-08-ai-agents-baseline-a00` from the exact SHA above; **documentation only** |
| T00 implementation branch | `audit/2026-10-08-triggers-executions` → PR #226. **Do not overwrite or duplicate its commits.** |
| Last published release | `v3.91.10` (2026-10-05, APK phone + Wear) |
| Last completed successful `main` Actions run observed | [37625029211](https://github.com/Alaa91H/NexaFlow/actions/runs/37625029211) on this exact baseline SHA (2026-10-07) |
| T00 PR CI | [37723239512](https://github.com/Alaa91H/NexaFlow/actions/runs/37723239512) on `6fc7e86c6a3a2d9be88d4d5328ded54a10a2d15a`; **IN_PROGRESS when sampled**. Requery before marking success. |

The underlying shared T00 report [on the T00 branch](https://github.com/Alaa91H/NexaFlow/blob/audit/2026-10-08-triggers-executions/docs/audit/triggers-actions-atomic-audit-2026-10-08.md) records a **dirty primary Windows checkout** containing unrelated exact-alarm edits and a clean isolated T00 worktree. Its local `gradlew.bat projects`, static scripts and `adb devices` evidence are **T00's recorded results**, **not commands independently executed by A00**. This A00 workspace has no checkout of that Windows machine; the authorized Desktop Commander device was **offline** during this review.

## 2. Build, SDK, persistence and repository controls

| Item | Frozen repository configuration / provenance |
|---|---|
| Gradle | `9.6.1` at `gradle/wrapper/gradle-wrapper.properties:3` |
| Android Gradle plugin | `9.3.1` at `gradle/libs.versions.toml:36` |
| Kotlin plugins | `2.4.10` at `gradle/libs.versions.toml:34,122–124` |
| Compile / target / minimum Android SDK | `37 / 37 / 26` at `app/build.gradle.kts:65–70` |
| Bytecode / JVM language target | Java 17, Kotlin JVM 17 (`app/build.gradle.kts:186–202`); Robolectric unit test JVM 21 toolchain (`app/build.gradle.kts:104–112`) |
| Room | Schema version **27**, `exportSchema = true`, at `core/database/.../AppDatabase.kt:22–23`. Historical exported schemas exist; passing migrations is **not** inferred from presence. |
| Dependency verification | `gradle/verification-metadata.xml` referenced by `settings.gradle.kts:22–26` |
| Main protection | Enabled at audit: **strict** required checks `secret-scan`, `lint`, `Android emulator integration`, `coverage`, `build`. Protection is a settings observation, not evidence that these checks passed on A00's future PR. |
| Signing | The published APKs and matching tag build were validated in T00's release evidence; T00 records the **Git tag itself is not cryptographically signed**. No signing secrets were read. |
| Local machine | T00 recorded Windows build 26100, Microsoft OpenJDK 17.0.20.1, SDK platforms 34/35/37 (26/36 missing), and an empty `adb devices -l` listing at its capture time. **A00 did not independently execute these probes.** |

The `main` checkout is a **baseline reference**, not a statement that this PR or T00 has landed on `main`.

## 3. Audited AI subsystem inventory (source snapshot; not live capability support)

| Area | Real source owners / observations |
|---|---|
| Conversation/provider registry | `core/ai-runtime`: `AiConversationEngine`, `AiProviderRegistry`, `AiProviderCatalog`, `AiProviderDomain`. |
| Native and compatibility adapters | `OpenAiResponsesProvider`, `OpenAiCompatibleProvider`, `AnthropicMessagesProvider`, `GeminiNativeProvider`; Android transports under `app/src/main/java/com/nexaflow/app/ai/`. |
| Agent definition/budget and policy | `core/agent-runtime/AgentRuntimeModels.kt`, `PolicyFilteredAgentToolExecutor.kt`; management in `feature/settings/ManagedAgent*.` |
| Run orchestration | `app/.../agent/ManagedAgentRunCoordinator.kt`, `NexaFlowAiToolExecutor.kt`; durable state repositories in `data/src/main/java/com/nexaflow/data/agents/`. |
| Local and external control surfaces | `core/agent-api` REST + MCP + A2A; `app/.../agent/NexaFlowAgentService.kt` Binder; `core/agent-relay` device-side outbound relay. |
| Shared authorization and effect owner | `core/agent-security` and `core/automation-control/AutomationCommandService.kt`; device effects route through the existing `core/execution` runtime. |
| Storage | `core/database` Room agent entities/DAOs, `core/datastore/AiProviderPreferences.kt`, credential references via `VaultBackedAiCredentialStore` (do not serialize raw API keys into reports). |

**Source-scoped inventory counts:** 13 provider **definitions** (not all built-in presets) and 4 explicit cloud/gateway **presets** are declared in `AiProviderCatalog.kt:41–166`, including OpenAI Responses, Anthropic Messages, Gemini Native and OpenCode Zen. OpenRouter/Groq/Mistral/DeepSeek/xAI, Ollama/LM Studio and custom are **provider definitions**; no live connectivity or account access was performed. The current source defaults list `gpt-5.6`, `claude-sonnet-5-5`, `gemini-3.8-flash`, `kimi-k2.7-code`; a literal default is **not** evidence of availability or entitlement.

**Static code inventory from the 1,913-entry `main` Git tree:** `core/ai-runtime` 19 production Kotlin files / 15 JVM test files; `core/agent-api` 14 / 8; `core/agent-runtime` 2 / 1; `core/agent-security` 5 / 1; `core/agent-relay` 5 / 3. These are file-count facts only; they do not establish test execution or coverage.

## 4. Concrete open findings / precise follow-ups

| Finding | Evidence on frozen commit | Assigned issue / expectation |
|---|---|---|
| Every managed run **with non-null `maxCostMicros` is rejected** before model invocation. | [`ManagedAgentRunCoordinator.kt:119–123`](https://github.com/Alaa91H/NexaFlow/blob/4199f4a1108b60cab05b3df017a10b2d24a0e2d8/app/src/main/java/com/nexaflow/app/agent/ManagedAgentRunCoordinator.kt#L119-L123) | [A06 #156](https://github.com/Alaa91H/NexaFlow/issues/156); fail closed only when actual cost unknown; do not fake model price or bypass budget. |
| Basic chat context is **ViewModel-local**, initialized with a new UUID, and cleared on lifecycle. | [`AiChatViewModel.kt:34–35`](https://github.com/Alaa91H/NexaFlow/blob/4199f4a1108b60cab05b3df017a10b2d24a0e2d8/feature/ai/src/main/java/com/nexaflow/feature/ai/AiChatViewModel.kt#L34-L35) | [A28–A31](https://github.com/Alaa91H/NexaFlow/issues/178) opt-in persistence and scoped continuation, no secrets. |
| A2A controller rejects `message/stream` and `tasks/cancel`. | [`AgentA2AController.kt:90–104`](https://github.com/Alaa91H/NexaFlow/blob/4199f4a1108b60cab05b3df017a10b2d24a0e2d8/core/agent-api/src/main/java/com/nexaflow/core/agentapi/AgentA2AController.kt#L90-L104) | [A47–A51](https://github.com/Alaa91H/NexaFlow/issues/197); advertise only real capabilities. |
| Agent run process recovery **marks active runs interrupted and does not auto-replay**. | [`AgentRunRepository.kt:260–267`](https://github.com/Alaa91H/NexaFlow/blob/4199f4a1108b60cab05b3df017a10b2d24a0e2d8/data/src/main/java/com/nexaflow/data/agents/AgentRunRepository.kt#L260-L267) | Preserve this safety invariant in [A10 #160](https://github.com/Alaa91H/NexaFlow/issues/160); report unknown effects for review. |
| Remote relay is device-side only; server provision/deployment is **out of scope** in current implementation. | [`docs/AGENT_RELAY.md`](https://github.com/Alaa91H/NexaFlow/blob/4199f4a1108b60cab05b3df017a10b2d24a0e2d8/docs/AGENT_RELAY.md) | [A50 #200](https://github.com/Alaa91H/NexaFlow/issues/200) must use an explicitly approved server or mark `BLOCKED_EXTERNAL`. |
| Tool-approval grants require independent authorization; successful source-level checks do not prove a live agent. | `PolicyFilteredAgentToolExecutor` and `AgentAccessManager` | [A36–A43](https://github.com/Alaa91H/NexaFlow/issues/186) and [A27 #177](https://github.com/Alaa91H/NexaFlow/issues/177). |

These findings are **audit observations**, not fixes delivered by A00.

## 5. Evidence matrix: avoid misreporting tests

| Gate / command | Evidence classification | What is actually established |
|---|---|---|
| `python scripts/audit_catalog_and_releases.py catalog` | **T00 PR #226 recorded local exit 0** | 57 triggers (55 exposed) / 180 actions **catalog parity only**; not executed independently in this A00 workspace. |
| `python scripts/check_strings_parity.py` | **T00 PR recorded local exit 0** | Source locale parity result, not device RTL UI. |
| `./gradlew.bat --version`, `./gradlew.bat projects --console=plain` | **T00 PR recorded local exit 0** | Gradle 9.6.1 and configuration success on T00's isolated Windows worktree; **not a full compile/unit run**. |
| Other static safety/canonical scripts | **T00 PR recorded local exit 0** | See [T00 PR description](https://github.com/Alaa91H/NexaFlow/pull/226) and its evidence manifest. No new A00 executions claimed. |
| Scheduled `main` Actions [37625029211](https://github.com/Alaa91H/NexaFlow/actions/runs/37625029211) | **VERIFIED PASS** on exact `4199f4a1108b60cab05b3df017a10b2d24a0e2d8` | Hosted CI for the **baseline code**, not this branch or uncommitted Windows changes. |
| T00 PR [37723239512](https://github.com/Alaa91H/NexaFlow/actions/runs/37723239512) | **IN_PROGRESS on inspection** | Do not approve/merge #226 until all required checks finish at the PR's actual head. |
| Windows DataStore full regression | **NOT TESTED at A00** | Prior 2026-10-05 report records five local Windows failures. Need exact-SHA repro and CI comparison, not a fresh pass claim. |
| Android device/OEM/permission/Root/Shizuku | **NOT TESTED at A00** | T00 machine's `adb devices -l` empty, desktop agent currently offline; no physical-device proof. |
| Live OpenAI/Anthropic/Gemini/OpenCode/Ollama | **NOT TESTED** | No account/credential/connection or authorized usage/budget confirmed. |
| 72-hour soak, battery, network fault injection | **NOT TESTED** | No device, 72-hour evidence, Perfetto or long-running run was observed. |
| **A00 PR exact-SHA checks** | **PENDING — CI triggers after PR creation** | Check the five required contexts and commit hash on A00 PR. |

## 6. Baseline synchronization and hard dependencies

1. T00 [#123](https://github.com/Alaa91H/NexaFlow/issues/123) and A00 [#150](https://github.com/Alaa91H/NexaFlow/issues/150) **share exactly one pinned `main` SHA**, release reference, SDK/build/Room configuration and CI identity; do not create another competing engine, schema or baseline.
2. PR [#226](https://github.com/Alaa91H/NexaFlow/pull/226) contains the **platform environment, repo settings and reproducibility evidence**; A00's PR adds **AI agent-specific inventories/findings** without modifying or duplicating T00 documents. Merge the T00 prerequisite first, then the A00 documentation PR once its own checks pass.
3. Record ownership/dependency edges at [A05 #155](https://github.com/Alaa91H/NexaFlow/issues/155) and review the platform master [#122](https://github.com/Alaa91H/NexaFlow/issues/122) before changing Room/migrations, capability router, approvals, CI or security.
4. Outstanding external decisions are in [`docs/OPEN_QUESTIONS.md`](https://github.com/Alaa91H/NexaFlow/blob/4199f4a1108b60cab05b3df017a10b2d24a0e2d8/docs/OPEN_QUESTIONS.md): device/OEM, distribution, native secret scanning, authorized provider budget and locked-boot policy. The open questions are **blockers to particular acceptance claims**, not permission to fabricate test results.
5. For release readiness, preserve a clear distinction between **source audited**, **hosted CI green**, **real provider verified**, **physical Android verified** and **beta release verified**. None implies the next.

## 7. Change log / handoff

- Changes in this PR: **AI audit documents and machine-readable baseline evidence only**. No application code, Android manifest permissions, Room schema, Gradle, CI workflow, credentials, provider keys, model calls or runtime behavior changed.
- Tests directly executed by the author of this A00 report: **none**; source/API observations inspected, and T00's independently documented command logs linked.
- A00 remains **PARTIAL** until T00 merges, A00 PR passes exact-SHA CI, and acceptance is reviewed. Only then may the specific documentation/bootstrap portions be checked and the Issue closed with evidence. Device/live-provider tasks remain separately unverified.
