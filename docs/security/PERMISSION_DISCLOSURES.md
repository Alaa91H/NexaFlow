# Permission disclosures

Source of truth: `app/src/main/AndroidManifest.xml` (42 `uses-permission`
entries) plus the Wear module manifest. This document maps each capability to
its user-facing purpose and to the channel-specific disclosure required by
`docs/adr/0001-distribution-channel.md`.

Legend: **G** = GitHub build (current artifact), **P** = Play flavor if ADR
0001 option 3 is chosen; "n/a" means the permission is absent from that
channel.

## Normal permissions (install-time, no prompt)

| Permission | Why NexaFlow needs it | G | P |
|---|---|---|---|
| `INTERNET` | AI providers, webhooks, update check | yes | yes |
| `ACCESS_NETWORK_STATE` | network triggers and connectivity checks | yes | yes |
| `WAKE_LOCK` | keep a short critical section alive during a run | yes | yes |
| `VIBRATE` | vibrate action and notification feedback | yes | yes |
| `POST_NOTIFICATIONS` | run results, reminders, foreground-service notice (runtime prompt 33+) | yes | yes |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | long-running automation execution with a visible notice | yes | yes |
| `RECEIVE_BOOT_COMPLETED` | rebuild schedules after reboot (post-unlock) | yes | yes |
| `SCHEDULE_EXACT_ALARM` | exact-time tasks (user can revoke; app degrades to inexact) | yes | yes |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | prompt for exemption so scheduled runs survive Doze | yes | yes |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (<=30), `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (31+) | Bluetooth triggers/actions | yes | yes |
| `CAMERA` | flashlight/camera action and QR pairing | yes | yes |
| `FLASHLIGHT` | flashlight action where exposed | yes | yes |
| `ACTIVITY_RECOGNITION` | motion/step triggers (runtime prompt) | yes | yes |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | location triggers and geofence-style conditions (runtime prompt) | yes | yes |
| `READ_CALENDAR` | calendar trigger | yes | yes |
| `READ_PHONE_STATE`, `READ_BASIC_PHONE_STATE` | call/telephony state triggers | yes | yes |
| `READ_LOGS` | diagnostics on ROM builds that grant it; app never relies on it for features | yes | n/a |
| `PACKAGE_USAGE_STATS` | app-usage triggers, granted via system settings | yes | yes |
| `SYSTEM_ALERT_WINDOW` | floating overlay actions, granted via system settings | yes | yes |
| `MODIFY_AUDIO_SETTINGS` | volume/ringer actions | yes | yes |
| `EXPAND_STATUS_BAR`, `STATUS_BAR` | status-bar actions (privileged on most builds) | yes | n/a |
| `WRITE_SETTINGS` | system settings actions, granted via system settings | yes | yes |
| `WRITE_SECURE_SETTINGS` | privileged settings actions via adb/Shizuku/root | yes | n/a |
| `FORCE_STOP_PACKAGES`, `KILL_BACKGROUND_PROCESSES` | app-control actions | yes | n/a |
| `MODIFY_PHONE_STATE` | network-mode actions (privileged) | yes | n/a |

## Restricted / declaration-required permissions

| Permission | Purpose | GitHub | Play |
|---|---|---|---|
| `QUERY_ALL_PACKAGES` | discover installed apps for app triggers, task launcher and plugin catalog | declared | **removed** — replace with `<queries>` for the intents NexaFlow launches; discovery lists only visible apps and the capability center says so |
| `REQUEST_INSTALL_PACKAGES` | in-app update of the sideloaded APK | declared | **removed** — Play Core in-app updates |
| `SEND_SMS`, `RECEIVE_SMS` | SMS actions and SMS triggers (documented as a core automation capability) | declared | only with a Play-approved core-function declaration; otherwise the SMS capability is hidden and the permission removed |
| `ACCESS_LOCAL_NETWORK` (Android 16+) | LAN agent gateway / local device discovery | declared | declared, with local-network disclosure |

## Runtime and special-access prompts

The app must request, in context, and explain each of: notifications,
location, camera, activity recognition, Bluetooth scan/connect, SMS (if
enabled), exact alarms, overlay, usage access, write-settings and
ignore-battery-optimizations. Denial must degrade the specific capability and
never crash or silently claim success.

## Data Safety inputs

See `docs/security/DATA_SAFETY.md`. The permission list above feeds the Play
Data Safety form only for the `play` flavor; the GitHub build has no store
form.
