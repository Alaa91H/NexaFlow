# NexaFlow

NexaFlow is an Android automation application built with Kotlin and Jetpack Compose. Tasks combine triggers, constraints, actions and optional exit behavior. Some actions use ordinary Android APIs; others require permissions, accessibility, a system role, Shizuku or root. Availability depends on Android version, hardware and the device vendor.

## Current implementation

The source catalog contains **56 trigger enum entries (54 in the general picker)** and **176 action enum entries**. `CONNECTIVITY` is retained for older tasks and `PLUGIN_EVENT` uses a separate plugin flow. The sensor trigger offers **12 modes**; these are configurations of one trigger, not 12 additional enum entries. Counts describe implemented catalog coverage, not certification that every function works on every phone.

- Schedules, app/device events, connectivity, location, notifications, messages and hardware sensor conditions.
- Ordered actions, workflow context, constraints, execution history, cooldowns and exit/revert behavior.
- Device controls with capability checks and explicit failure reporting.
- Eight data action families: text, encoding, hashing, random values, decimal arithmetic, dates, JSON and arrays. Outputs can feed later actions in the same run.
- HTTPS requests with configurable method, body, headers, timeout, retries and output path. Private-network destinations require explicit opt-in.
- Local authenticated webhooks and revocable task links. Custom-scheme execution requires confirmation.
- Full backup and single-task sharing. Imported tasks stay disabled for review; external execution tokens are removed.
- Room database schema 19, explicit migrations, DataStore preferences, widgets and a foreground monitoring service.

See the [generated catalog](docs/CAPABILITY_CATALOG.md), [configuration reference](docs/CONFIGURATION.md), [security model](docs/SECURITY.md) and [validation record](docs/VALIDATION.md).

## Requirements and installation

- Android 8.0 / API 26 minimum; compile and target SDK 37.
- Download artifacts from [GitHub Releases](https://github.com/Alaa91H/NexaFlow/releases). Retain the same signing identity when upgrading an existing installation.
- Grant permissions from the feature that needs them. Root and Shizuku are optional capability providers, not universal requirements.
- Exact alarms, background restrictions and OEM power management can affect delivery. A foreground service does not guarantee uninterrupted execution.
- Sensor features require the corresponding hardware. Pressure, humidity, ambient temperature and hinge sensors are uncommon on many phones.

## Build and verify

Use the checked-in Gradle wrapper. Install Android SDK platform 37 and configure `ANDROID_HOME` or `local.properties`. Gradle runs with Java 17 or newer; Robolectric tests using recent Android SDKs require the configured Java 21 toolchain. Dependency checksums are enforced.

```powershell
./gradlew.bat testDebugUnitTest assembleDebug
./gradlew.bat detekt lintDebug
python scripts/auto_fix.py --check
python scripts/check_strings_parity.py
python scripts/audit_catalog_and_releases.py catalog
```

On Linux/macOS use `./gradlew`. Release signing is configured using the `NEXAFLOW_KEYSTORE_*` and `NEXAFLOW_KEY_*` environment variables or ignored local keystore properties. Never commit private keys or passwords. Untagged ad-hoc builds may use debug signing; version-tag CI requires production credentials.

## Scope and limitations

NexaFlow has **not demonstrated overall superiority over Automate**. Automate advertises more than 400 blocks; its blocks and this project's trigger/action enums are different units. A count alone does not establish parity in expressions, flow control, integrations, reliability or usability. The [comparison and roadmap](docs/ROADMAP_2026.md) identifies what remains unverified.

UI resources cover English plus 10 translated-locale directories. Newly added data/sensor configuration and some security copy currently use English fallback in other locales; key parity is not translation completion. No claim of full device coverage, complete vendor compatibility or universal restoration is made.

Historical plans and audits remain in the repository for provenance. The [documentation index](docs/README.md) distinguishes the current guides from historical/reference material. Release changes are recorded in English in [CHANGELOG.md](CHANGELOG.md).

## Development

See [architecture](docs/ARCHITECTURE.md), [contributing](CONTRIBUTING.md), [required checks](docs/REQUIRED_CHECKS.md), and [release procedure](docs/RELEASING.md). Report reproducible issues with Android version, device/ROM, permissions and a minimal task; redact tokens and private payloads.
