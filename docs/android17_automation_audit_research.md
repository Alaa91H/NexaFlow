# Android 17 automation audit research

## Sources reviewed

1. Android Developers, [Behavior changes: Apps targeting Android 17 or higher](https://developer.android.com/about/versions/17/behavior-changes-17), retrieved 2026-08-27.
2. Android Developers, [Behavior changes: all apps](https://developer.android.com/about/versions/17/behavior-changes-all), retrieved 2026-08-27.
3. Android Developers, [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms), retrieved 2026-08-27.
4. Android Developers, [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission), retrieved 2026-08-27.

## Audit rules derived from official Android guidance

| Platform area | Android 17 rule | Required NexaFlow review |
|---|---|---|
| Exact time triggers | `SCHEDULE_EXACT_ALARM` must be declared and users can revoke it. The app must check `canScheduleExactAlarms()`, react to the permission-state broadcast, and recreate needed alarms. Exact alarms are cancelled if access is revoked. | Verify declaration, permission gating, revocation receiver, boot/time-change rescheduling, and visible degraded state. |
| Background audio and volume | Audio playback, focus and volume interactions can fail silently in background on API 37 without a valid foreground-service state or exact-alarm/`USAGE_ALARM` exception. | Audit alarm/ringer/volume actions and any foreground service type; add a deterministic result or supported deferral where calls could otherwise report a false success. |
| Local HTTP / IoT | `ACCESS_LOCAL_NETWORK` is mandatory for target SDK 37 access to LAN sockets. Denial/revocation blocks LAN traffic; public Internet traffic remains unaffected. | Verify manifest declaration, runtime request only on API 37+, error classification and actionable failure for local HTTP calls. |
| SMS trigger | SMS OTP protection can withhold covered OTP messages or provider queries for three hours for non-exempt apps. | Audit SMS trigger to make no prompt-time guarantee for OTP content and document/route official SMS Retriever/User Consent where needed. |
| Activity launches | Background activity-launch protections are tightened, including `IntentSender`. | Audit actions that open apps/settings/URLs from alarms or background triggers; use notification-mediated or visible-user routes when the platform blocks a launch. |
| Memory limits | Android 17 imposes memory limits and exposes `ApplicationExitInfo` diagnostics. | Audit monitor lifecycle, callback registration, executor shutdown, and crash/memory-exit recovery. |
| Bluetooth reads | `BluetoothSocket.read()` may return `-1` after connection loss. | Audit all RFCOMM read loops to terminate on `-1`, not only exceptions. |

The official sources define platform limits. They do not make it possible for a normal application to grant itself privileged telephony, mobile-data, hotspot or secure-settings access. These paths must remain explicitly capability-gated and report their true outcome.

## Additional sources reviewed

5. Android Developers, [Background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio), retrieved 2026-08-27.
6. Android Developers, [Activity security and background activity launches](https://developer.android.com/guide/components/activities/secure-bal), retrieved 2026-08-27.

## Additional audit rules

| Platform area | Android 17 rule | Required NexaFlow review |
|---|---|---|
| Volume/ringer actions from alarms | Background volume/ringer calls can silently have no effect. On API 37, a valid foreground service with while-in-use capability is normally required; the documented exact-alarm exception is limited to `USAGE_ALARM` streams. | Check actions that use `AudioManager.setStreamVolume`, `setRingerMode`, or audio focus. They must have an explicit Android-17-compatible execution state and postcondition check; otherwise report unsupported/deferred rather than success. |
| Opening an activity from automation | Background activity launch restrictions can block an app/settings/URL activity without a normal application exception. Android 17 hardens `IntentSender` too. | Check actions that call `startActivity` from alarms, receivers, services, notifications or plugin callbacks. Prefer user-tapped notification actions for UI navigation and surface a deterministic blocked result. |
| Debugging blocked launch | Android provides StrictMode detection for blocked background activity launch and recommends ActivityTaskManager log review. | Enable debug-only strict-mode diagnostics in the test build and write repeatable adb acceptance checks for launch-sensitive actions. |

These review rules do not authorize background UI interruption or privileged access; they require that the engine detect platform denial and preserve a truthful audit record.
