# NexaFlow v3.50.4 — Android 17 Automation Reliability and CI Hardening

**Release date:** 2026-08-27
**Release scope:** Correct Android 17 Hotspot callback permission declaration, fail-closed workflow execution, observable recovery failures, and verified background-audio outcomes.

## Overview

NexaFlow v3.50.4 is a reliability patch focused on **truthful automation outcomes** under modern Android restrictions. It closes a CI-blocking permission declaration gap for Hotspot state observation and hardens the workflow engine so uncertain platform outcomes are recorded as actionable failures rather than reported as successful automation.

## Delivered changes

| Area | Delivered behavior |
|---|---|
| Hotspot callback contract | Declares `ACCESS_NETWORK_STATE` in the `core:common` library manifest, the owning module for `TetheringManager.registerTetheringEventCallback()`. Android Lint now validates the public API contract without suppression or a baseline exception. |
| Workflow branches | A thrown condition evaluation fails closed. NexaFlow records the diagnostic and executes neither the true nor false action path, preventing a condition-read error from being interpreted as a valid false state. |
| Rollback evidence | A failed compensation attempt is retained in the workflow timeline beside the original action failure rather than being swallowed. |
| Wait-until evidence | An expired `WaitUntil` node now includes the last condition-evaluation error, distinguishing an unavailable device state from a condition that remained unmet. |
| Android 17 audio | Volume changes verify `AudioManager` read-back after calling `setStreamVolume()`. A background restriction that silently rejects the change is reported as a failure instead of a false success. |
| Device acceptance | Added Android 17 platform research, a quality-audit record, and a device acceptance protocol for exact alarms, 22:00–06:00 ranges, Doze, reboot recovery, Hotspot, telephony, permissions, local-network HTTP, and background UI restrictions. |

## Operational behavior

This release retains NexaFlow’s capability-gated automation model. Cellular network-mode, Hotspot, and protected-settings operations require an available privileged route where Android or the device manufacturer requires one. A successful operation must still satisfy its postcondition check when the platform exposes one. Root or Shizuku does not override SIM, carrier, modem, OEM, or Android framework limitations.

No saved automation is migrated, deleted, or rewritten by this release. Precise time automations continue to need **Alarms & reminders** access, while protected network-mode operations require a live verified Root or Shizuku session and validation on the target SIM.

> Device-dependent functionality must be verified on the intended handset, ROM, carrier, and SIM. When Android or an OEM blocks an operation, NexaFlow records the inability to verify or apply it instead of claiming that it succeeded.

## Verification

The release candidate passed the complete GitHub Actions Android CI workflow: [run 33105036238](https://github.com/Alaa91H/NexaFlow/actions/runs/33105036238).

| Verification gate | Result |
|---|---|
| Android unit tests | Passed |
| Detekt and Android Lint | Passed |
| Debug and release APK builds | Passed |
| Release AAB and bundletool validation | Passed |
| Dependency-verification metadata | Passed |
| APK signature and zipalign validation | Passed |
| 16 KB page-alignment and native-library audit | Passed |
| Resource quality and locale-parity gates | Passed |

## Upgrade guidance

Install the release over the existing application or as a clean installation. For exact time tasks, retain **Alarms & reminders** access. For a Cellular Network action, verify that Root or Shizuku remains granted, reopen the capability selector after access is available, and confirm the selected profile on the target SIM. The device acceptance protocol in the repository should be followed before production rollout on a new ROM or carrier.

## Full changelog

See [`CHANGELOG.md`](https://github.com/Alaa91H/NexaFlow/blob/main/CHANGELOG.md) for the complete release history.
