# Data inventory and Play Data Safety (preparation)

Status: inputs prepared; submitting the Play form is **BLOCKED** until ADR 0001
chooses a Play channel and a Play Console account exists.

## What the app stores locally (on device only)

| Data | Where | Leaves the device? |
|---|---|---|
| Task definitions, triggers, actions, schedules | Room (`core:database`) | only through an explicit user backup export/share |
| Execution history and per-action results | Room | only through the diagnostic bundle or backup the user shares |
| Run logs (in-memory and DataStore checkpoints) | app storage | diagnostic bundle, redacted |
| AI provider credentials | encrypted keystore/preferences (`core:security`, `core:agent-security`) | never; sent only to the provider the user configured |
| Webhook URLs and payloads | Room task definitions | only when the user's own task calls the URL |
| Agent pairing tokens/sessions | agent security store (hashed) | never |
| Location samples | used in-memory / task storage depending on the task | only if the user writes a location value into a webhook/provider action |
| SMS content for SMS triggers | processed on device | never by the app; only user-authored actions could forward it, and only to the user's endpoint |
| Diagnostics (version, schedule summary, last 50 runs, logs) | local cache file shared by the user | only when the user exports and shares it |

The app has no analytics SDK, no ad SDK and no server operated by the project.
Sentry reporting exists behind an explicit opt-in and a build-time DSN; when
disabled nothing is sent.

## Network destinations

1. The user-configured AI provider endpoint (HTTPS only; cleartext blocked).
2. The user-configured webhook/HTTP action URL (HTTPS enforced except for
   explicitly allowed loopback/private hosts where the app documents it).
3. The update check endpoint (GitHub releases API), which sends no personal
   data beyond the version query.
4. The loopback agent API (`127.0.0.1`), which never leaves the device; LAN
   access stays disabled until TLS transport exists.

## Data Safety form answers (play flavor)

| Question | Answer |
|---|---|
| Does your app collect or share any required user data types? | No data is collected by the developer. User-initiated transfers (AI provider, webhooks, backup share) are user-directed and not developer collection. |
| Is all of the user data encrypted in transit? | Yes — HTTPS/TLS is enforced; cleartext is blocked in the network security config. |
| Can users request data deletion? | Yes — uninstalling or clearing app data deletes everything; backup/diagnostics are user files. |
| Data collected: Location | Not collected by the developer; used on-device for user tasks. |
| Data collected: Messages (SMS) | Not collected by the developer; processed on-device only when an SMS trigger is enabled. |
| Data collected: App activity / other app info | Not collected by the developer; package visibility is used on-device. |
| Data collected: Files and docs | Not collected; user-selected backup import/export only. |
| Data collected: Credentials | Not collected; AI credentials stay in the device keystore. |

If a future build enables anonymous telemetry, this document and the form must
be updated in the same change, and the telemetry must be off by default.

## Privacy policy requirements

Before any Play submission, publish a policy covering: no developer-side
collection, on-device processing, user-directed transfers, keystore storage,
diagnostic export contents and redaction, retention (until uninstall/clear
data), and contact. Link it from the Play listing and from the in-app About
section.
