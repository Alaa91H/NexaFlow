# NexaFlow v3.56.2 — Trigger Catalog and GPS Geofence Expansion

**Release date:** 2026-09-02
**Release scope:** Expand the trigger catalog with a clearly labeled GPS geofence and complete the user-facing coverage of supported connectivity and location-state triggers.

## Overview

NexaFlow v3.56.2 improves automation discoverability and control without changing the persisted workflow model or runtime contracts. The builder now exposes the production-ready GPS geofence capability and the previously hidden unified connectivity and location-mode triggers, while retaining the security boundary that protects verified plugin events.

## Delivered changes

| Area | Delivered behavior |
|---|---|
| GPS geofence trigger | Presents the existing location monitor as **GPS Geofence**, with enter/exit events, validated coordinates and radius, adaptive provider polling, lifecycle recovery, and explicit location permission handling. |
| Complete trigger catalog | Exposes the unified `CONNECTIVITY` and `LOCATION_STATE` triggers in the grouped builder picker. `PLUGIN_EVENT` remains restricted to the verified plugin configuration path. |
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

The complete verification matrix is executed by GitHub Actions for the release tag. The final release entry will link the exact immutable workflow run after all gates complete.

| Verification gate | Result |
|---|---|
| Trigger catalog audit and whitespace check | Passed locally |
| Focused Android unit tests | Pending CI: the local sandbox has no Android SDK |
| Detekt, Android Lint, APK/AAB, and resource gates | Pending CI |
| Release artifact, signature, alignment, and dependency gates | Pending CI |

## Upgrade guidance

Install the release over the existing application or as a clean installation. For exact time tasks, retain **Alarms & reminders** access. For a Cellular Network action, verify that Root or Shizuku remains granted, reopen the capability selector after access is available, and confirm the selected profile on the target SIM. The device acceptance protocol in the repository should be followed before production rollout on a new ROM or carrier.

## Full changelog

See [`CHANGELOG.md`](https://github.com/Alaa91H/NexaFlow/blob/main/CHANGELOG.md) for the complete release history.
