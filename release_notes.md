# NexaFlow v3.56.3 — Simplified GPS Controls and Flexible Timers

**Release date:** 2026-09-03
**Release scope:** Simplify GPS location-mode control and introduce quick and custom timers from 1 second through 24 hours.

## Overview

NexaFlow v3.56.3 improves everyday device control through a simpler GPS switch workflow and a flexible bounded timer editor. The update preserves the existing persisted workflow model and runtime contracts while making common durations immediately selectable and allowing precise custom delays.

## Delivered changes

| Area | Delivered behavior |
|---|---|
| GPS location mode | Presents explicit **ON** and **OFF** choices for controlling whether the device location mode should be active, alongside the existing GPS geofence workflow. |
| Quick timers | Provides one-tap presets for 1 minute, 5 minutes, 10 minutes, and 24 hours. |
| Custom timer | Accepts a bounded duration from 1 second through 24 hours and displays the stored duration clearly in the task summary. |
| Runtime safety | Applies the same 1–86,400 second bounds during execution, including for imported workflows. |
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

The complete verification matrix is executed by GitHub Actions for the release tag. The final release entry links the exact immutable workflow run after all gates complete.

| Verification gate | Result |
|---|---|
| Trigger catalog audit and whitespace check | Passed locally |
| Resource parity, hygiene, and configuration gates | Passed locally |
| Focused Android unit tests | Pending CI |
| Detekt, Android Lint, APK/AAB, and release-artifact gates | Pending CI |

## Upgrade guidance

Install the release over the existing application or as a clean installation. For exact time tasks, retain **Alarms & reminders** access. For a Cellular Network action, verify that Root or Shizuku remains granted, reopen the capability selector after access is available, and confirm the selected profile on the target SIM. The device acceptance protocol in the repository should be followed before production rollout on a new ROM or carrier.

## Full changelog

See [`CHANGELOG.md`](https://github.com/Alaa91H/NexaFlow/blob/main/CHANGELOG.md) for the complete release history.
