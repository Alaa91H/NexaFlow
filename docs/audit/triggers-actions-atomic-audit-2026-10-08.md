# NexaFlow atomic triggers/actions baseline — 2026-10-08

**Scope:** Issue [#123 / T00](https://github.com/Alaa91H/NexaFlow/issues/123). This freezes a reproducible repository and environment baseline before the atomic trigger/action audit. It does not change production behavior or certify Android devices.

## Source identity and checkout

| Item | Observation |
|---|---|
| Plan baseline | `4199f4a1108b60cab05b3df017a10b2d24a0e2d8` (`main`, 2026-10-08) |
| Actual `HEAD` / `origin/main` | Both `4199f4a1108b60cab05b3df017a10b2d24a0e2d8` at capture time |
| Branch at baseline capture | `main`; T00 changes are on the isolated branch `audit/2026-10-08-triggers-executions`, created from the verified SHA. |
| Initial worktree | Not clean: pre-existing edits in `PermissionManagerScreen.kt` and untracked `ExactAlarmAccessChangeDetector.kt` plus `ExactAlarmAccessChangeDetectorTest.kt`; these are exact-alarm permission work and were not created or modified by T00. |
| Worktree policy | The primary checkout was not switched or modified by T00 because it contains unrelated uncommitted work. T00 evidence is isolated in its own worktree. |
| OS | Windows 10.0.26100 (Windows 11, build 26100), PowerShell |
| Git remote | `https://github.com/Alaa91H/NexaFlow.git` |

The baseline pin refers to committed source only. Local modifications must not be attributed to this SHA or treated as validated by its CI.

## Build and Android environment

| Component | Observation |
|---|---|
| Java | Microsoft OpenJDK `17.0.20.1` |
| Gradle wrapper | `9.6.1` (`gradle/wrapper/gradle-wrapper.properties`) |
| Android Gradle Plugin | `9.3.1` (version catalog alias `pv9_3_1`) |
| Kotlin Android/Compose plugins | `2.4.10` (version catalog alias `pv2_4_10`) |
| Android compile / target / minimum SDK | 37 / 37 / 26 (`app/build.gradle.kts`) |
| Local SDK platforms | 34, 35, 37.0; API 26 and 36 platforms are not installed locally. |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | Not set in the captured shell. SDK platform files are present at `C:\Users\Alaa\AppData\Local\Android\Sdk`. |
| Emulator / physical device / OEM / Root / Shizuku | `adb devices -l` returned no devices. Availability and provider state are therefore `NOT TESTED`. |
| Signing material | Only `keystore.properties.example` was present in the repository keystore directory; no production keystore was observed there. CI signing secrets were not inspected. |
| Host matrix | Windows observed. Ubuntu behavior is represented only by the hosted CI run below. macOS is `NOT TESTED`. |

The first wrapper probe began downloading Gradle 9.6.1 and did not finish in its initial command window. After the distribution was cached, the same probe succeeded. An initial `auto_fix.py --check` in the dirty primary checkout did not return within its command window; in this clean issue worktree it completed successfully. The final local results below supersede those preliminary attempts.

## Repository and GitHub controls

| Control | Observation at capture time |
|---|---|
| Repository | Public, default branch `main` |
| `main` branch protection at initial inspection | GitHub API returned HTTP 404, `Branch not protected`; protection and required status checks were absent. |
| Native secret scanning | Disabled in repository settings |
| Secret-scanning push protection | Disabled |
| Dependabot security updates | Disabled |
| Actions workflow | `.github/workflows/nexaflow-ci.yml`; push, pull request, manual, and scheduled triggers; permission scopes are declared per job. |
| Release tags | Latest listed release was `v3.91.10` (2026-10-05); historical releases remain present. |
| Open issue backlog | 104 issues were open at capture time, including separate masters #122 (T00–T25) and #149 (A00–A75). The full snapshot is `docs/evidence/<sha>/issues.json`; T00 starts the #122 sequence. |

The current CLI account has Admin permission. After identifying the exact CI contexts from completed runs, T00 configured and re-read branch protection for `main`: pull requests are required; strict status checks `secret-scan`, `lint`, `Android emulator integration`, `coverage`, and `build` are mandatory; stale approvals are dismissed; conversation resolution is required; admins are subject to the rule; force pushes and branch deletion are blocked. The required approval count is zero so a sole maintainer is not locked out pending a second maintainer, but the PR and CI gates still apply. The check contexts came from completed Actions runs; release publication and Dependabot auto-merge contexts were excluded because they are intentionally skipped on ordinary PRs. The settings response is captured in `branch-protection.json`.

Native secret scanning, secret-scanning push protection, and Dependabot security updates remain disabled. The workflow token default permission is `read`; workflow-level permissions are declared in the YAML. These controls were inventoried, not changed. Their disabled state is recorded as a gap, not inferred to be an account-policy limitation.

The latest release was inspected directly. `v3.91.10` points to commit `66f0dbd1327365b770901b2fa7d8d528a531ae9b` and contains phone APK SHA-256 `cce058a2f28ed58ccc184e2d45cb51561bb457afac7180028d7ea2d1ca90f43f` and Wear APK SHA-256 `93a783021748c3075ce42991743ddf16b94093390fd43265899b15ac71faa63d`. Tag CI run [37258291959](https://github.com/Alaa91H/NexaFlow/actions/runs/37258291959) succeeded on that exact commit, including production APK signing, certificate match, version/tag match, alignment, and publication. `git tag -v v3.91.10` reported `error: no signature found`: APK signatures were verified by CI, but the Git tag itself is not cryptographically signed. Private signing credentials were not inspected. Detailed snapshots are `release-v3.91.10.json` and `tag-ci-37258291959.json` under `docs/evidence/<sha>/`.

## Baseline verification evidence

Commands run against the unchanged committed source, except that the checkout contained the unrelated edits listed above:

| Command / evidence | Result |
|---|---|
| `python scripts/check_strings_parity.py` | Exit 0 — `PARITY_PROBLEMS: 0`. |
| `python scripts/audit_catalog_and_releases.py catalog` | Exit 0 — `CATALOG_PARITY: OK`; 57 trigger enum entries (55 exposed) and 180 action enum entries appear in the builder. Catalog parity is not runtime/device proof. |
| `python scripts/auto_fix.py --check` | Exit 0 — `AUTO_FIX: OK - no orphaned, duplicate, or unbalanced strings.` (clean issue worktree). |
| `python scripts/check_atomic_overhaul_baseline.py` | Exit 0 — `Atomic baseline OK: v3.91.3 9e5e2fa132f2`. |
| `python scripts/check_atomic_architecture_fitness.py --self-test` and regular gate | Both exit 0 — self-test and architecture fitness OK. |
| `python scripts/check_persistence_safety.py` | Exit 0 — 25 exported schemas, latest schema 27. |
| `python scripts/check_ai_secret_boundary.py`; `python scripts/check_ai_provider_registry.py` | Both exit 0. |
| `python scripts/check_suppression_budget.py`; `python scripts/check_readme_stale_counts.py` | Both exit 0 — counts 83 `Suppress`, 42 `SuppressLint`; README count guard OK. |
| `python scripts/check_canonical_baseline.py` | Exit 0 — 57 triggers + 180 actions = 237 catalog node kinds; original 57/176 surface preserved. |
| `./gradlew.bat --version` | Exit 0 — Gradle 9.6.1 on Microsoft OpenJDK 17.0.20.1, Windows 11 build 26100. |
| `./gradlew.bat projects --console=plain` | Exit 0 — `BUILD SUCCESSFUL in 11m 15s`; full Gradle project hierarchy enumerated. This is a configuration/environment probe, not a compile, test suite, or full build. |
| `adb devices -l` | Exit 0; device list empty. Device/OEM/Root/Shizuku validation `NOT TESTED`. |
| Hosted Actions [run 37625029211](https://github.com/Alaa91H/NexaFlow/actions/runs/37625029211) | Completed `success` on exact SHA `4199f4a1108b60cab05b3df017a10b2d24a0e2d8` (2026-10-07). Secret scan, lint/static gates, unit tests, and untagged debug APK build passed. Tag validation, production signing, tagged release packaging/publication were skipped as expected for a scheduled run. This validates the committed SHA in hosted Ubuntu CI, not the local worktree edits or device behavior. |

## Known issues and external evidence gaps

- Existing open questions remain in [`docs/OPEN_QUESTIONS.md`](../OPEN_QUESTIONS.md), including physical devices and the 72-hour multi-OEM soak (OQ-01), distribution-channel decision (OQ-02/OQ-05), GitHub native scanning controls (OQ-03), authorized live AI provider account (OQ-04), and locked-boot policy (OQ-06).
- Prior Windows evidence in [`docs/evidence/baseline/p0-01-current-main-2026-10-05.md`](../evidence/baseline/p0-01-current-main-2026-10-05.md) records DataStore temp-file replacement failures on Windows and a successful hosted Ubuntu run for a different commit. Reproduce at this exact SHA before assigning current status; no such local Gradle reproduction completed here.
- No performance, battery, emulator, instrumentation, OEM, permission, privileged-backend, migration fault-injection, or 72-hour soak result is claimed by T00.
- The last known open issue list is a point-in-time snapshot; issue state can change after capture.
- The point-in-time open issue and published release lists captured for this baseline are in this commit's `docs/evidence/<sha>/issues.json` and `releases.json`.

## Change impact

T00 changes repository settings to protect `main` as described above and adds documentation/evidence metadata. No production source, task behavior, database schema/migration, Android permission, CI workflow, or release credential changed. The GitHub issue state was not changed. The pre-existing exact-alarm edits remain outside this T00 worktree and evidence set.

## T09 temporal trigger filter implementation (2026-10-09)

T09 implements bounded, process-local temporal state in the existing trigger/execution architecture. Event and schedule configuration uses `minIntervalMs`, `cooldownMs`, and paired `rateLimitCount`/`rateLimitWindowMs`; `debounceMs` is exposed for volume-change triggers and uses a trailing-edge quiet window. State-readable thresholds support `stableForMs` and numeric `hysteresis` for battery, volume, brightness, Wi-Fi/cell signal, and battery temperature. Durations are bounded to 0–604,800,000 ms; rate count to 1–1,000; hysteresis uses the documented sensor unit and threshold scale. Missing values preserve prior behavior. Configuration remains in the existing `Trigger.config` string map; Room converter round-trip and legacy JSON compatibility are covered without a schema migration. `SystemClock.elapsedRealtime()` drives runtime windows; wall-clock identity remains unchanged. Unknown/unavailable/error observations break stability continuity without becoming false. Rejections are reason-coded before single-flight, wake lock, occurrence receipt, checkpoint, or action.

The canonical builder exposes typed duration/count/decimal controls alongside specialized editors, with exact duration bounds and legacy config preservation. Stable-for rechecks are delayed and bounded (one pending check per trigger key, queue cap 4,096) and re-read live state before delivery. Filter state is process-local and bounded (LRU cap 4,096); it resets on automation deletion, invalid/unknown observations, or monotonic rollback. The shared monitor sources currently integrated are battery, volume, brightness, Wi-Fi/cell signal, and battery temperature. No Room schema, Android permission, or capability boundary changed.

Verification on the local Windows host: `git diff --check` exit 0; `:domain:testDebugUnitTest :core:execution:testDebugUnitTest :core:automation-engine:testDebugUnitTest :feature:automation-builder:testDebugUnitTest` completed `BUILD SUCCESSFUL` (all four tasks, 2026-10-09); `:core:database:testDebugUnitTest` completed `BUILD SUCCESSFUL` (2026-10-09). The verification checkout was based on exact predecessor merge `95f90ecc407dd247a224d4a36a1fb436f160d3af`. These are local JVM/unit-test results only. Emulator, physical device, permission denial/regrant, reboot persistence, OEM lifecycle, and provider behavior are `NOT TESTED`; exact-head hosted CI is still required before merge.
