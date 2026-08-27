# NexaFlow v3.49.0 — Durable State and Recovery Safety

**Release date:** 2026-08-27

**Release scope:** Automation-engine reliability hardening

**Baseline:** v3.48.0

## Release summary

NexaFlow v3.49.0 hardens the automation lifecycle at the boundary where Android state reads, action execution, recovery, and end behavior meet. The release extends occurrence-aware ownership to the consolidated settings-state monitor, prevents interrupted actions from losing their recovery evidence, and makes whole-device restore failures visible to the exit coordinator instead of reporting a false successful exit.

The implementation is intentionally conservative. An unreadable setting, unavailable adapter, missing platform service, or event that cannot be re-derived later is treated as **unknown**, not as a confirmed false condition. In those cases NexaFlow preserves lifecycle evidence and records a safe manual skip rather than running end actions speculatively.

## What changed

| Area | Delivered behavior |
|---|---|
| Settings-state triggers | `POWER_SAVER`, Bluetooth/Wi-Fi/NFC state, brightness, storage, rotation, data saver, device lock, location switch, and screen rotation now use `SATISFIED` / `NOT_SATISFIED` / `UNKNOWN` evaluation. A source-owned durable occurrence is admitted before main effects, and only a confirmed false state can request coordinated exit. |
| Interrupted actions | An action cancelled after durable start is retained as `ACTION_UNKNOWN`. Startup recovery claims it and marks it `RECOVERY_REQUIRED` for verification or compensation; it is never replayed automatically. One-shot end actions do not run after an uncertain main action. |
| Restore-on-exit | Whole-snapshot restore aggregates actual `SystemControlResult` values for the setting families changed by the automation. `STATE_RESTORE` now reports the true result, allowing a failed exit to remain durable as `EXIT_FAILED`. |
| Manual Run now | Manual condition gating now consumes typed `ConditionResult`. Unverifiable event-only and ambiguous legacy reads produce a visible skip with no synthesized end behavior. Confirmed false conditions retain the explicit end-behavior path. |
| Connectivity | A failed hotspot `tether_on` read now remains unknown rather than being interpreted as `OFF`, preventing an accidental lifecycle exit. |
| Battery and charging | Corrected sustained-state handling so an active battery threshold or charger occurrence stays active while its configured condition still holds. |
| Privileged commands | The location-mode helper now builds its elevated `settings put` command through the shared `SafeCommandBuilder`, rather than composing a variable shell string. |

## Verification

The implementation candidate passed the complete remote GitHub Actions pipeline in [run 33055298598](https://github.com/Alaa91H/NexaFlow/actions/runs/33055298598). The accepted workflow completed resource hygiene and locale parity checks, Python resource-gate tests, Detekt, Android Lint, the Android unit-test suite, debug and release APK builds, release AAB build, dependency-verification validation, packaged-manifest/permission checks, APK signature verification, 16 KB native-library alignment, zip alignment, and bundle validation.

> No local Gradle build, lint task, or unit test was executed. Gradle acceptance for this release is provided exclusively by remote GitHub Actions.

## Scope and limitations

This release hardens the **settings-state** source family in addition to the prior time-range, connectivity, and battery/charger lifecycle work. It does **not** claim that every monitor is occurrence-aware. Location geofence, Bluetooth-device, device-event, ringer, media, notification, sensor, ROM-setting, calendar, and other legacy direct-exit sources still require source-specific migration and regression coverage.

Cross-automation ownership of the same device setting is also intentionally not introduced here. NexaFlow does not yet provide a global setting-ownership stack, precedence policy, or user-intervention reconciliation for overlapping reversible automations. Deleting an active automation before coordinated exit, stale legacy active-key expiry outside migrated sources, one-shot alarm late-delivery policy, elapsed-time cooldown migration, SMS cross-ingress deduplication, and Android/OEM background-delivery variation remain tracked risks.

Android can restrict background delivery, exact-alarm access, carrier/telephony behavior, and privileged setting writes depending on device, OEM, user permission, power management, and available elevated runtime. This release preserves observable failure and recovery state rather than claiming universal platform execution.

## Full audit record

The code-derived architecture map, trigger catalog, implementation evidence, Android references, and explicit deferred risks are recorded in [`docs/AUTOMATION_ENGINE_AUDIT_2026.md`](docs/AUTOMATION_ENGINE_AUDIT_2026.md).

## Upgrade notes

No database migration, new runtime permission, cloud service, telemetry, or user-data collection is introduced by v3.49.0. Users upgrading from v3.48.0 retain their existing automations. Existing settings-state compatibility keys are promoted to a local durable occurrence on monitor startup when an eligible automation remains available; no missing original-state snapshot is invented during that compatibility promotion.
