# Validation record

## AGENT_RUN action (v3.91.16) — 2026-10-30 (local, no device)

All results below are the actual exit statuses of this checkout; nothing was
re-run from an older tree. The change adds `ActionType.AGENT_RUN` and its
builder editor, canonical identity and tests.

| Command | Result |
|---|---|
| `./gradlew :app:compileDebugKotlin` | PASS — 306 tasks, Hilt/KSP component generation included |
| `./gradlew testDebugUnitTest --console=plain` | PASS — 3,120 tests, 0 failures, 0 errors, 0 skipped across 471 test classes |
| `./gradlew detekt` | PASS |
| `./gradlew lintDebug` | PASS — 1,323 tasks |
| `./gradlew :feature:automation-builder:coverageGate :core:execution:coverageGate :domain:coverageGate :data:coverageGate :core:agent-runtime:coverageGate` | PASS — `:core:execution` 69.2% (8,679/12,547), `:domain` 89.9% (11,036/12,278), `:data` 84.2% (1,202/1,428); builder and agent-runtime are report-only |
| `python3 scripts/check_diff_coverage.py --diff-file <HEAD→worktree> --threshold 0.80` | PASS — 93.4% (241/258) of changed executable lines; `AgentRunEditor.kt` 94.5%, `ManagedAgentRunGateway.kt` 89.5%, `AgentActionsHandler.kt` 87.3%, every other changed production file 100% |
| Source gates (`auto_fix --check`, strings parity, translation completeness, hardcoded text, vendor neutrality, catalog, atomic inventory, findings audit, node contracts, module boundaries, Compose stability, suppression budget, README counts, docs freshness) | PASS — 0 problems each |
| Canonical gates (T01–T44, families, baseline, inventory, legacy mappings, golden migration, runtime cutover, docs closure) | PASS — all exit 0 against the updated 57/181/238 inventory |

The diff-coverage gate uses `--diff-file` because the change set was still
uncommitted when it was measured; the CI form (`--base <sha>`) covers the same
lines once the commit exists. CI on the pushed commit, the tag workflow,
physical-device execution and publication are **NOT TESTED** here.

## Remediation execution — 2026-10-29 (local, no device)

Scope: phases 0–4 of `audit/FULL_AUDIT_REMEDIATION_PLAN_2026-10-28_AR.md`.
All commands below were run in this checkout on JDK 21; results are the real
exit statuses, not projections.

| Command | Result |
|---|---|
| `./gradlew :core:agent-api:testDebugUnitTest --tests '*AgentApiControllerTest*'` | PASS — 40 tests |
| `./gradlew testDebugUnitTest --continue` بعد حذف كل تقارير XML/التغطية القديمة | PASS — 3,004 اختبارًا، 0 فشل/خطأ/تخطٍّ، 33 وحدة (13m26s للتشغيل النظيف الكامل + 2m16s لإعادة تشغيل الوحدتين المعدّلتين) |
| `./gradlew coverageGate --continue ...` | PASS — 1m17s; `:data` 84.2%, `:core:agent-api` 61.3%, `:core:automation-engine` 44.5%, `:core:execution` 68.8%, `:core:compatibility` 90.9%, `:core:logging` 89.3%, `:core:security` 82.9%, `:core:wear-protocol` 94.8%; `:core:rom-integration` later re-measured at 24.7% |
| `./gradlew detekt --no-build-cache` | PASS — 1m28s actual analysis; `LongMethod`/`NestedBlockDepth` enabled with generated baselines |
| `./gradlew lintDebug --no-build-cache` | PASS — 14m59s, 1,326 tasks after all source/resource/test changes |
| `./gradlew detekt :feature:history:testDebugUnitTest :feature:dashboard:testDebugUnitTest :core:rom-integration:testDebugUnitTest :core:agent-api:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleRelease -PallowDebugSigning=true` | PASS — 28m07s; R8 mapping produced (106.5 MB) |
| `./gradlew :feature:history:testDebugUnitTest :core:rom-integration:coverageGate` | PASS — rom-integration 24.7% (1,000/4,048) |
| Python gate unit tests (supply chain, diff coverage, module boundaries, Compose stability, benchmark budget) | PASS — 34 tests |
| `python3 scripts/check_module_boundaries.py` | PASS — 0 violations, 3 documented allowlisted edges |
| `python3 scripts/check_compose_stability.py` | PASS — app unstable 21/21, non-skippable 20/20 |
| `python3 scripts/check_diff_coverage.py --base 5464d21fc` | PASS — 100% (209/209) of changed executable lines in modules with JVM coverage; the `--diff-file` form is not used because it cannot see untracked new files |
| Source gates (`auto_fix`, translation completeness, hardcoded text, vendor neutrality, catalog, suppression budget, persistence safety, node contracts, findings audit, README counts, strings parity, resources) | PASS — all 0 problems |
| `python3 scripts/check_docs_freshness.py --max-commits 25` | PASS |
| YAML parse of all `.github/workflows/*.yml` | PASS — 3 workflows |

Aggregate unit results from the XML reports after these runs: **3,004 tests,
0 failures, 0 errors** across 33 modules.

Architecture decomposition (Phase 2.3) is **partially done**: `SystemController`
dropped from 91,244 to 87,694 bytes (−3.9%) via `SystemInputController`; the
thirteen frozen hotspots total 960,857 bytes versus 964,407 at HEAD (−0.37%),
so the plan's ≥20% target is **not met**.

Not run / not provable in this environment: instrumentation on a real device or
emulator, OEM/Doze/boot/72-hour soak, live AI provider, Sentry, Shizuku/Root,
TalkBack review, a signed production release, and the SLSA attestation (it is
configured but only a tag run can produce it). Those remain `BLOCKED` with the
reasons recorded in the plan (`18.1`) and `OPEN_QUESTIONS.md`.

## Trigger Expression v2 local validation — 2026-10-09

Implementation candidate for issue [#133](https://github.com/Alaa91H/NexaFlow/issues/133), branch `audit/2026-10-09-t10-trigger-expressions`, based on `335c4953fa34c980a90d2e3ee0582c6e04577fa5` (merged #132). Focused local JVM/compile gates passed: `:domain:testDebugUnitTest --tests '*TriggerExpressionV2Test' --tests '*WorkflowValidatorTest'`, `:data:testDebugUnitTest --tests '*RepositoryImplTest'`, `:core:datastore:testDebugUnitTest --tests '*TriggerExpressionHistoryStoreTest'`, `:core:execution:testDebugUnitTest --tests '*TriggerExpressionRuntimeEvaluatorTest'`, `:core:database:testDebugUnitTest --tests '*MigrationTest'`, `:data:testDebugUnitTest --tests '*RoomAutomationMutationPersistenceTest'`, and `:feature:automation-builder:testDebugUnitTest --tests '*TriggerExpressionEditorTest'`; `:feature:automation-builder:compileDebugKotlin` also passed. The integrated changed-module test/build command also passed. `detekt lintDebug coverageGate --continue` passed (1,756 actionable tasks; 74 executed, 1,682 up-to-date); enforced module gates passed for data 84.2%, domain 89.8%, core:security 82.9%, and core:logging 89.3%. Other modules with lower coverage are report-only under current repository policy.

Coverage includes bounded AST/schema validation, legacy null-expression behavior, monotonic sequence order and expiry, replay admission, corrupt/oversized history fail-closed behavior, v27→v28 legacy defaults, Room definition revision increments versus status-only saves, runtime sequence parity, and builder preview parity. The HMAC digest behavior is covered with an injected key; Android Keystore generation/retrieval on an actual device is **NOT TESTED**. Emulator integration, device reboot/permission revoke-and-regrant, OEM behavior, and live source identity are **NOT TESTED**. GitHub PR CI/merge and issue closure are pending.

## Current checkout — v3.91.10 baseline revalidation, 2026-10-05

The P0 evidence refresh for `66f0dbd1327365b770901b2fa7d8d528a531ae9b` is recorded in [`evidence/baseline/p0-01-current-main-2026-10-05.md`](evidence/baseline/p0-01-current-main-2026-10-05.md). The local Android SDK was available and `detekt`, all debug lint tasks, and `assembleDebug assembleRelease -PallowDebugSigning=true` completed. The combined Windows Gradle invocation still exited nonzero: five DataStore tests failed when `FileStorageConnection.writeScope` could not replace a target file with its temporary file. The exact commit's Ubuntu tagged CI run [37258291959](https://github.com/Alaa91H/NexaFlow/actions/runs/37258291959) passed, including unit tests and production release checks. All four local app/Wear debug/release APKs passed v2 signature and version metadata inspection, but were signed with the Android Debug certificate and are disposable validation outputs. No Android device was connected. These results do not certify device behavior or make the local full Gradle command green.

## Security scan follow-up — 2026-10-05

At `7b600dff98e510255f93aa028409fd262f31d1ee`, official Gitleaks v8.30.1 was downloaded with its published SHA-256 verified and run against all local Git refs. It scanned 2,221 commits and initially reported five generic-key matches; review found two synthetic test fixtures and three non-secret comment/release-text matches. Their exact fingerprints are recorded in `.gitleaksignore`; the repeat scan exited 0 with no remaining findings. Details and scan limitations are in [`evidence/baseline/gitleaks-2026-10-05.md`](evidence/baseline/gitleaks-2026-10-05.md).

A checksum-pinned Gitleaks job in the single GitHub Actions workflow passed with lint, coverage, unit tests, and APK assembly in [run 37254899900](https://github.com/Alaa91H/NexaFlow/actions/runs/37254899900). Release publication was skipped on the ordinary `main` push. GitHub's repository security settings report native secret scanning disabled. No physical device was connected, so OEM/device validation remains `NOT TESTED`.

## P1-01 and backup-policy follow-up — 2026-10-05

PR #120 passed secret scan, lint, coverage, and build checks and was squash-merged to `main` as `a1982a058bca0af78f03fc65f89109b6849dcec1`. `:core:automation-engine:testDebugUnitTest --tests com.nexaflow.core.engine.SubscriptionChangeGateTest --rerun-tasks` passed on the merged source (234 tasks executed; 3 tests, no failures). `:app:testDebugUnitTest --tests com.nexaflow.app.BackupPolicyManifestTest` passed (2 tests, no failures); a combined targeted invocation also completed successfully. The post-merge main workflow [37268650702](https://github.com/Alaa91H/NexaFlow/actions/runs/37268650702) was still shown as `In progress` at 2026-10-05 05:54 UTC, so no green-main claim is made from that run. Source inspection found a Direct Boot design gap recorded in [`OPEN_QUESTIONS.md`](OPEN_QUESTIONS.md#master-plan-open-questions). No Android device was connected; boot behavior remains unverified.

## Current checkout — v3.91.8 baseline audit, 2026-10-05

The source audit and current local validation status are recorded in [`AUDIT.md`](AUDIT.md) and [`MASTER_PLAN_STATUS_2026-10-05.md`](MASTER_PLAN_STATUS_2026-10-05.md). Repository catalog/locale/suppression/README checks and the release-signing guard unittest passed. `detekt` completed. The combined `lintDebug` run did not finish because its Gradle daemon stopped advancing at `automation-builder` after reaching approximately 4.3 GB resident memory. A broad `testDebugUnitTest --continue` run executed the datastore module and reported 5/46 failures: DataStore could not rename a temporary file over an existing file. A Java probe confirmed this host's `File.renameTo(existingTarget)` returns `false`. The same baseline passed the `testDebugUnitTest` workflow gate on Ubuntu in [GitHub Actions run 37227693119](https://github.com/Alaa91H/NexaFlow/actions/runs/37227693119), confirming the Windows-local failures are host-specific. The broad local run continued into execution test compilation, then was interrupted after no visible progress for several minutes; the local test task is **not passed**.

`app-debug.apk` and `wear-debug.apk` were built locally and their V2 signatures verified; the app signer is `Android Debug`. The merged release manifest audit passed. A release packaging task without production credentials failed closed as expected. GitHub Actions run [37245345750](https://github.com/Alaa91H/NexaFlow/actions/runs/37245345750) on commit `d0e39a38` passed lint, coverage, unit tests, untagged APK assembly, and packaged-APK checks. Untagged CI APKs used explicit debug signing and were not published as a GitHub Release. Tag run [37247996319](https://github.com/Alaa91H/NexaFlow/actions/runs/37247996319) passed production signing, APK integrity checks, and publication; [release v3.91.9](https://github.com/Alaa91H/NexaFlow/releases/tag/v3.91.9) contains only the phone and Wear APKs. At the time of the original baseline entry no full-history scan tool was installed; see the scan follow-up above. Device and OEM checks remain `NOT TESTED`.

## Historical validation record — v3.74 candidate

Status: local integration checks passed. CI and publication are still pending until the commit, push, tag and release workflow complete.

## Canonical platform gates (local, 2026-09-29)

The canonical automation platform (T05–T41) is verified by deterministic gates, run locally on the closing commits of each phase and re-runnable at any time:

- `python3 scripts/check_canonical_final_audit.py` — the closing audit: every canonical gate ships a unittest, stays wired into CI and passes on the current tree; the inventory closes every phase T05–T39 with the retirement ledger. Passed with all 40 gates green.
- `python3 scripts/check_canonical_release_readiness.py` — every canonical gate passes, the T26+ statuses are recorded, the changelog carries an Unreleased section and the working tree is clean. Passed on the closing commits.
- `./gradlew :domain:testDebugUnitTest --tests "com.nexaflow.domain.canonical.*" --console=plain` and `./gradlew :core:plugin-sdk:testDebugUnitTest --tests "com.nexaflow.core.pluginsdk.*" --console=plain` — the canonical model and plugin SDK suites. Passed (plugin-sdk: 33 tests including the 13 T41 condition-contract tests).

These are JVM-local results. Device/OEM behavior remains governed by the standing rule: do not infer device results from JVM tests; physical-device evidence is recorded in the dated sections below.

The candidate combines published v3.73 main with preserved local work, then applies the supplied external-ingress audit and data/sensor/HTTP improvements. Original local work is retained in the `codex/security-audit-snapshot` branch; the release integration is in `codex/security-capability-release`.

Local checks run on 2026-09-17:

- `./gradlew :core:datastore:testDebugUnitTest --no-daemon --max-workers=1` — passed.
- `./gradlew :core:execution:testDebugUnitTest --no-daemon --max-workers=1` — passed after making the queued-deadline test use an injected clock.
- `./gradlew detekt lintDebug --continue --max-workers=2` — passed.
- `./gradlew testDebugUnitTest :app:assembleDebug :app:processReleaseManifest --continue --max-workers=2` — passed.

The final broad unit/build run reported `BUILD SUCCESSFUL in 3m 12s` with 844 actionable tasks, 15 executed and 829 up-to-date. The quality run reported `BUILD SUCCESSFUL in 1m 24s` with 1009 actionable tasks, 44 executed and 965 up-to-date.

CI commit/run links, release artifact URLs and tag status must be recorded after the push and release workflow complete. Device/OEM testing and the framework-permission instrumentation suite have not been run in this session. Do not infer those results from JVM tests.


## USB diagnosis, 2026-09-18

A connected 23049PCD8G running Android 17/API 37 and NexaFlow v3.74.3 reported Root available and Shizuku not granted. Process-scoped logcat contained repeated TelephonyCallback RejectedExecutionException messages against shutting-down or terminated executors, with thousands of completed callback tasks. Source review found unconditional listener re-registration on active-data-subscription delivery, including repeated initial values. The correction suppresses unchanged subscription delivery and tolerates callback submission after shutdown. This is device evidence of the original failure; installation and physical-device retesting of the correction are still pending. Raw device logs are not included in the repository.

Recovery-health domain tests and execution-history DAO regression tests passed locally. Automation-details and history Kotlin compilation passed. Recovery warnings now remain visible independently of failed-run counts, and legacy deferrals are consistently filtered and localized in history.

## USB diagnosis follow-up — v3.74.5 candidate

The v3.74.4 installation was verified on the USB device. Android 17 accepted exact, idle-safe time alarms for the configured 22:00 start and 06:00 end window, so the scheduler permission and Doze delivery path are operational. The historical execution record showed a removed hidden hotspot API (`WifiManager#setWifiApEnabled`) under Shizuku; v3.74.5 replaces it with the typed WifiShell Soft AP operation. A previous process instance was also killed by Android after excessive Binder traffic; the telephony re-registration correction is present in v3.74.4, and ongoing device observation remains required to establish long-running stability.

The v3.74.5 CI build exposed a module-boundary error before packaging: the automation-details UI attempted to inject the DataStore-backed execution ledger directly. v3.74.6 routes the explicit recovery-backlog reset through `ExecutionEngine`, the established owner of that ledger. The fix restores the compile path without changing reset semantics: records are discarded only after user confirmation, never replayed or reported as successful.

The recovery ledger remains fail-safe: unresolved side effects are never replayed implicitly. v3.74.5 supplies an explicit, confirmed, per-routine reset so a legacy recovery backlog can be acknowledged without blocking future scheduled runs for that routine. The device was unavailable for final on-device hotspot command validation after the source change; CI and the focused unit tests remain the release gates.
