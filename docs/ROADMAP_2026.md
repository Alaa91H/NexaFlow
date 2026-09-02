# NexaFlow Master Plan 2026 — Fix · Develop · Optimize

> **Goal:** make NexaFlow the most reliable, most complete, and most device-adaptive
> automation engine on Android — honest by construction: every capability is proven on
> the device that runs it, and nothing is ever reported as applied without evidence.
>
> **Status legend:** ✅ shipped · 🚧 in progress · 📋 planned · 🎯 target gate
>
> Companion documents: [`docs/IMPROVEMENT_PLAN.md`](IMPROVEMENT_PLAN.md) (completed
> tranches P0–P2), [`docs/audit/GAP_ANALYSIS_2026.md`](audit/GAP_ANALYSIS_2026.md)
> (forensic baseline), [`docs/STRICT_TASK_LIFECYCLE.md`](STRICT_TASK_LIFECYCLE.md).

---

## 0. Where NexaFlow stands (evidence baseline, 2026-09-02)

| Area | Evidence | Status |
|---|---|---|
| Trigger catalog | 53 `TriggerType` values, 52 user-exposed (`PLUGIN_EVENT` restricted to verified plugin config) | ✅ |
| Action catalog | 157 `ActionType` values, all 157 exposed in the builder picker | ✅ |
| Durable execution | Occurrence admission, atomic idle barrier, workflow-revision checkpointing, `RECOVERY_REQUIRED` classification, bounded re-delivery | ✅ |
| Exit coordination | `ExitCoordinator` atomic claims, revert-or-custom exit behavior, `EXIT_FAILED` observability | ✅ |
| Compatibility | `DeviceProfile` + `CommandCompatibilityEngine` filter the picker per device; unsupported commands vanish instead of failing at runtime | ✅ |
| CI quality gates | Resource/orphan/banned-key gate, string parity across 11 locales, Detekt, Android Lint zero-tolerance, dependency verification, 16 KB alignment, APK signing verification | ✅ |
| Observability | Sentry (opt-in, no NDK), execution history with outcome filters, routine health card | ✅ |
| Performance | Baseline profile, Macrobenchmark startup, R8 full + resource shrinking, Paging 3 for history/variables, `reportFullyDrawn()` | ✅ |

The runtime core is strong. The remaining distance to "best in the world" is not
more triggers — it is **convergence**: one canonical execution contract, per-capability
verification metadata, deeper device-matrix evidence, and governance that keeps every
future contribution inside the same rails.

---

## 1. Strategy: five pillars

1. **Truthful execution** — an action result is `SUCCESS` only with postcondition
   evidence, `UNKNOWN` when evidence is absent, never a guessed `SUCCESS`.
2. **Device-adaptive by default** — the UI shows what *this* device can do; capability
   discovery is live, not a static table.
3. **Durability** — process death, reboots, Doze, alarm revocation, and clock changes
   never lose or duplicate an automation run.
4. **Total catalog coverage** — every enum value has an editor, default config,
   localized strings, compatibility requirement, and test.
5. **Governed growth** — CI gates, architecture tests, and release automation make the
   fast path and the safe path the same path.

---

## 2. Workstreams

### WS-1 · Reliability & durable execution (P0)

| # | Item | Outcome | Status |
|---|---|---|---|
| 1.1 | Canonical `NodeExecution` state machine adapter over existing lifecycle records | One repository-wide contract; illegal transitions fail at the boundary | ✅ (transition contract + tests, v3.54–v3.56) |
| 1.2 | Per-capability metadata: `idempotent`, `verifiable`, `compensable`, `irreversible` | Retry/recovery decisions become data, not per-handler folklore | 📋 (design in §3.3) |
| 1.3 | Failure-injection suite: crash between checkpoint ↔ side effect ↔ terminal write | Proves cross-store atomicity claims with tests, not prose | 📋 |
| 1.4 | SMS durable dedup ledger (fingerprint-based, bounded, privacy-safe) | Closes audit finding AE-27 (in-memory-only SMS cooldown) | 📋 |
| 1.5 | Workflow-level rollback policy flag (`stop-on-first-failure` vs. `continue`) exposed per automation | Makes the current partial-execution semantic a user choice | 📋 |

### WS-2 · Device-adaptive compatibility (P0)

| # | Item | Outcome | Status |
|---|---|---|---|
| 2.1 | Catalog parity gates (trigger/action enum ↔ builder picker) in CI | A new enum value without editor + picker entry fails the build | ✅ (this release) |
| 2.2 | Per-device capability report screen ("what works here and why") | Turns the hidden gate into user-visible trust | 📋 |
| 2.3 | ROM/OEM matrix expansion: Xiaomi HyperOS, OPPO/realme ColorOS, Huawei, Pixel (AOSP baseline) autostart deep links | Removes the #1 "automation did not run" support cause | 📋 |
| 2.4 | Android 16/17 acceptance re-run recorded per release (`docs/android17_device_acceptance.md`) | Regression evidence for exact alarms, Doze, reboot recovery | 🎯 per release |
| 2.5 | Accessibility-service degradation path (watchdog rebind + user banner) | Survives OEM kills of the accessibility process | 📋 |

### WS-3 · Trigger & action catalog completeness (P1)

The catalog is already the widest in class (53 triggers / 157 actions). The remaining
work is **depth of editors + verification**, plus a small set of net-new primitives:

| # | Item | Notes |
|---|---|---|
| 3.1 | Trigger editor completeness audit: every trigger type has ≥1 config field validated in the builder | 📋 |
| 3.2 | Action verification upgrade path: read-back verification for settings-family actions that currently trust the write result | 📋 |
| 3.3 | Net-new triggers (each ships with monitor, matcher, defaults, strings ×11 locales, tests): day/night ambient-light window; per-app data-usage threshold; roaming SIM identity change | 📋 |
| 3.4 | Net-new actions: per-app network deny (elevated, verified), wallpaper switch (public API), per-workflow variables export to file (opt-in, scoped) | 📋 |
| 3.5 | Constraints v2: per-trigger cooldown, quiet hours, and "require charging" as first-class reusable constraint objects | 📋 |

### WS-4 · Performance & resource discipline (P1)

| # | Item | Outcome | Status |
|---|---|---|---|
| 4.1 | Startup budget gate: Macrobenchmark `startup` metric must stay under a pinned budget in CI (device-lab days) | Prevents silent cold-start regressions | 📋 |
| 4.2 | Memory budget for the monitoring service (heap + broadcast backlog) | Prevents OEM OOM-kill loops | 📋 |
| 4.3 | APK size budget gate on release artifacts (CI computes and pins a ceiling) | Keeps the 2.9 MB release honest | 🎯 |
| 4.4 | Compose stability audit for dashboard grid + builder lists (strong-skipping mode evaluation) | Fewer recompositions on low-end hardware | 📋 |
| 4.5 | Room: index audit for the highest-traffic history/health queries with `EXPLAIN QUERY PLAN` evidence | Keeps paging smooth at 10⁵+ records | 📋 |

### WS-5 · Security & trust (P0)

| # | Item | Outcome | Status |
|---|---|---|---|
| 5.1 | Taint-style architecture tests: imported/plugin/user values reach privileged execution only through `SafeCommandBuilder`/validated contracts | Enforces the existing boundary mechanically | 📋 |
| 5.2 | Plugin trust lifecycle: verification states (unsigned → verified → quarantined) with compatibility matrix tests | Closes the plugin-quarantine gap from the gap analysis | 📋 |
| 5.3 | Secrets redaction evidence: global variables marked sensitive are never serialized into history records or logs (test-enforced) | Privacy guarantee with proof | 📋 |
| 5.4 | Dependency-verification renewal automation (monthly scheduled CI job regenerating metadata diff-only) | Supply-chain metadata never rots | 🎯 |

### WS-6 · Release engineering & governance (P0)

| # | Item | Outcome | Status |
|---|---|---|---|
| 6.1 | Catalog parity gates (shared with 2.1) | Catalog drift is a build failure | ✅ (this release) |
| 6.2 | Version-tag hygiene gate: tag must match `CHANGELOG.md` newest entry | Release notes can never be stale | ✅ (this release) |
| 6.3 | Release notes generator: `scripts/generate_release_notes.py` renders the changelog entry + evidence table into the GitHub Release body | Professional, consistent releases | ✅ (this release) |
| 6.4 | Required-status-checks manifest (`docs/REQUIRED_CHECKS.md`) documenting every CI gate for branch protection setup | Repo-owner enablement | ✅ (this release) |
| 6.5 | Scheduled nightly CI (lint + detekt + tests) on `main` | Catches dependency rot before release day | ✅ (this release) |
| 6.6 | Dependabot: monthly cadence for Gradle + Actions pins (already partially configured) | Predictable upgrade windows | 📋 |

---

## 3. Catalog & execution contract (target design)

### 3.1 Catalog invariants (now CI-enforced)

1. Every `TriggerType` value exists exactly once in `triggerTypeOptions` **unless**
   it is in the documented restricted set (`PLUGIN_EVENT`) — enforced by
   `scripts/audit_trigger_catalog.py` + `scripts/audit_action_catalog.py` in CI.
2. Every `ActionType` value exists exactly once in `actionOptions` — same gates.
3. Every catalog entry keeps localized strings (MissingTranslation + parity gates
   already enforce the string side).
4. Default-config completeness: every exposed trigger/action must resolve a
   non-crashing default config in the builder (unit-tested).

### 3.2 Capability metadata model (target)

```kotlin
enum class EffectKind { READ, WRITE, IRREVERSIBLE }

data class CapabilityMetadata(
    val idempotent: Boolean,      // safe to re-apply
    val verifiable: Boolean,      // postcondition readable back
    val compensable: Boolean,     // a compensation action exists
    val effect: EffectKind,
)
```

The execution engine consumes this instead of per-handler special cases:
- non-idempotent + non-verifiable ⇒ never auto-retried;
- `IRREVERSIBLE` ⇒ requires explicit user configuration at build time (the builder
  marks these, e.g. reboot/shutdown/clear-app-data);
- verification failure downgrades `SUCCESS` to a failed/unknown truthful outcome.

### 3.3 Exit & recovery semantics (already shipped, retained as contract)

Atomic occurrence admission → single-owner `ExitCoordinator` claims → truthful
`EXIT_FAILED` with bounded recovery → no replay of uncertain side effects.

---

## 4. Device matrix policy (the honesty rule)

NexaFlow never claims a capability without evidence. The matrix below is the required
acceptance set for calling a release "device-verified"; results are recorded per release
in `docs/android17_device_acceptance.md` (or a successor document).

| Tier | Devices | Required evidence |
|---|---|---|
| T1 | AOSP/Pixel emulator (API 26, 33, 36, 37) | Robolectric + instrumented smoke, exact-alarm grant path |
| T2 | Samsung One UI, Xiaomi HyperOS, ColorOS | Autostart deep link opens; battery-optimization exemption flow; reboot recovery |
| T3 | Privileged (root / Shizuku) | Elevated command path, network-mode read-back, Private DNS write verification |
| T4 | Play-distributed release build | Signing, 16 KB alignment, dependency verification, R8 round-trip of backup import/export |

---

## 5. Release engineering (operating manual)

- **Versioning:** SemVer, tags `vMAJOR.MINOR.PATCH` (`-alpha/-beta/-rc` prereleases).
  Version name/code derive from `git describe` (`buildSrc` GitVersion) — no manual
  version bumps anywhere.
- **Changelog:** [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format,
  English, one section per release, written *before* tagging; the tag-hygiene gate
  (6.2) fails the release if the tag has no changelog entry.
- **Flow:** merge to `main` (CI green) → write changelog entry → tag → CI builds and
  publishes the GitHub Release with APK + generated notes.
- **Rollback policy:** never delete a published tag; publish a patch release instead.

---

## 6. Execution order (next three tranches)

| Tranche | Contents |
|---|---|
| **T-1 (this release)** | Catalog parity gates (triggers + actions), tag/changelog hygiene gate, release-notes generator, nightly CI schedule, required-checks doc, SMS dedup ledger groundwork audit |
| **T-2** | Capability metadata model + engine consumption (WS-1.2), verification upgrade for settings-family actions (WS-3.2), secrets-redaction test (WS-5.3) |
| **T-3** | Failure-injection suite (WS-1.3), plugin trust lifecycle (WS-5.2), capability report screen (WS-2.2), OEM matrix expansion (WS-2.3) |

---

*Evidence rule for this document, same as the architecture audit: source code, tests,
CI definitions, and generated evidence are authoritative; prose is corroboration only.*
