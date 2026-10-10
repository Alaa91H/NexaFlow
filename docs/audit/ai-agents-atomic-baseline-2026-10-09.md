# NexaFlow AI agents — A00 current baseline (2026-10-09)

**Issue:** [#150 / A00](https://github.com/Alaa91H/NexaFlow/issues/150). **Shared platform predecessor:** [#123 / T00](https://github.com/Alaa91H/NexaFlow/issues/123), implementation [PR #226](https://github.com/Alaa91H/NexaFlow/pull/226), now merged in the current `main` history. **AI master:** [#149](https://github.com/Alaa91H/NexaFlow/issues/149); **platform master:** [#122](https://github.com/Alaa91H/NexaFlow/issues/122).

**Status: PARTIAL — current source baseline and local host checks recorded; GitHub exact-SHA CI and PR review remain unverified.** This report does not claim live-provider, device, emulator, OEM, endurance, or release acceptance. Do not close A00 until the refreshed report is reviewed on a PR and its required exact-commit checks pass.

## 1. Current source identity

| Key | Evidence |
|---|---|
| Repository | `Alaa91H/NexaFlow`, default branch `main` |
| Verified `main` / local checkout commit | `bc9c287d917dd017d4467a8735f35892ff05e766` |
| Source tree | `9d213368a999e776d980378d3f6bd07bc616db85` |
| Commit time and subject | 2026-10-09 20:38:38 UTC; merge of release PR #242 (`Merge pull request #242 from Alaa91H/release/v3.91.13`) |
| Latest release in `CHANGELOG.md` | `v3.91.13` (2026-10-09) |
| Shared T00 | PR #226 is in `main`; T00 is no longer a merge prerequisite. |
| Existing A00 PR | PR #227 points to `5a2348d955ce0ad575988a10cf2d3a79f244d4de`; local comparison is 41 commits behind and 2 ahead of current `main`. Its frozen 2026-10-05 source identity is stale. |

The upstream `HEAD` and `refs/heads/main` both resolved to the commit above during this capture. `git status --short` was clean before this report was added.

## 2. Change review since the previous frozen source

The earlier A00 report at baseline `4199f4a1108b60cab05b3df017a10b2d24a0e2d8` is historical evidence, not the current baseline. A direct diff from that commit to this `main` found no changes in the AI source owners audited below: `core/ai-runtime`, `core/agent-api`, `core/agent-runtime`, `core/agent-security`, `core/agent-relay`, managed-agent app orchestration, agent persistence, and managed-agent settings. Therefore the provider/agent code observations and source line references from the earlier report remain applicable to this source snapshot; CI, release, repository, and environment claims have been refreshed separately here.

The change review does not imply that the reported limitations have been fixed. In particular, a source definition does not prove provider availability, cost metadata, safe real-world effects, or interoperability.

## 3. AI source inventory and findings

Current production/test Kotlin file counts, checked in this checkout:

| Module | Production files | JVM test files |
|---|---:|---:|
| `core/ai-runtime` | 19 | 15 |
| `core/agent-api` | 14 | 8 |
| `core/agent-runtime` | 2 | 1 |
| `core/agent-security` | 5 | 1 |
| `core/agent-relay` | 5 | 3 |

The existing source audit records 13 provider definitions and 4 explicit presets. These are declarations only; no live provider call was made. Runtime ownership remains split among the AI provider/runtime modules, managed-agent coordinator and tool executor, agent API/Binder/relay entry points, existing authorization gates, and the shared automation execution architecture. Do not introduce a second execution engine.

The concrete open findings from the earlier report remain tied to unchanged source files and are still assigned to their existing follow-up issues:

- Configured `maxCostMicros` runs fail closed before model invocation when cost is unknown; A06 / #156 owns cost enforcement.
- Basic chat context remains ViewModel-scoped; A28–A31 / #178 own opt-in conversation persistence and continuation.
- A2A streaming/cancel methods remain unsupported; A47–A51 / #197 own lifecycle and advertised capability parity.
- Process recovery marks active runs interrupted rather than replaying them; A10 / #160 must preserve this safety invariant.
- Relay remains device-side; A50 / #200 requires an approved server or an explicit external blocker.
- Live providers, device acceptance, and end-to-end effects are not demonstrated by source inventory or local static checks.

For precise source paths and historical evidence provenance, see [the 2026-10-08 report](ai-agents-atomic-baseline-2026-10-08.md). The current report supersedes its source identity and environment status only.

## 4. Build and environment observations

| Item | Current evidence |
|---|---|
| Gradle | Wrapper 9.6.1. It runs from this checkout when `GRADLE_USER_HOME` points to writable `/tmp/nexaflow-gradle` and JVM proxy properties are supplied from the configured proxy. |
| Java | Java 21 runtime is present. The installed JDK lacks `javac` and Gradle reports it cannot provide `JAVA_COMPILER`. |
| Android SDK | No SDK platform 37 or `sdkmanager` was found; `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset. |
| Android Gradle tasks | Not runnable in this environment until a full JDK and Android SDK 37 are available. No application build, Robolectric suite, lint, or Detekt pass is claimed. |
| GitHub write/API access | The current proxy policy rejects the HTTPS `CONNECT` to `api.github.com`; `github.com` and Git fetch work. `gh auth status` also reports its selected token invalid. A draft egress rule for `api.github.com` was saved for user review and publication; it has not been applied to this running environment. No PR update, merge, issue edit, or close was performed. |

## 5. Checks run on current `main`

| Command | Result |
|---|---|
| `python3 scripts/auto_fix.py --check` | PASS — no orphaned, duplicate, or unbalanced strings. |
| `python3 scripts/check_strings_parity.py` | PASS — zero parity problems. |
| `python3 scripts/audit_catalog_and_releases.py catalog` | PASS — 57 triggers (55 exposed) and 180 actions appear exactly once in the builder. |
| `python3 -m unittest discover -s scripts/tests -p test_exported_components.py` | PASS — 9 tests. |
| `./gradlew tasks --all --no-daemon` | BLOCKED before task configuration by missing JDK compiler (`JAVA_COMPILER`). |
| Required GitHub checks on current `main` / PR #227 | NOT VERIFIED; API authentication is unavailable in this environment. Do not infer a pass from local checks. |
| Live-provider, physical-device, emulator/OEM, 72-hour soak, signed beta | NOT RUN. These require external credentials, hardware, time, and release authorization. |

The machine-readable manifest is [ai-agent-a00-baseline.json](../evidence/bc9c287d917dd017d4467a8735f35892ff05e766/ai-agent-a00-baseline.json).

## 6. Handoff and closure criteria

1. Refresh PR #227 from current `main`; do not merge its outdated baseline unchanged.
2. Obtain exact-SHA required CI results on the refreshed PR and review the updated evidence.
3. Keep provider/device/endurance claims explicitly unverified until their own authorized acceptance work is complete. Those tasks cannot be closed by this baseline report.
4. Close A00 only after its refreshed PR passes review and required checks. Continue A01 onward only in the issue dependency order and preserve each task's safety gates.

This report adds local documentation only. It does not state that PR #227 was updated, CI passed, or issue #150 was completed or closed.
