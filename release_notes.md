### Added

- Eight production data action families: text transformations, Base64/URL/hex encoding, SHA-256/SHA-512 hashing, secure random values, decimal arithmetic, date/time processing, JSON Pointer operations and array processing. Each has an editor, registry dispatch, bounded inputs and workflow-context output.
- Eight numeric sensor modes: pressure, ambient temperature, relative humidity, magnetic field, linear acceleration, gyroscope, gravity and hinge angle. Conditions support strict/inclusive thresholds and ranges, with device hardware checks.
- Custom HTTP request headers, HEAD/OPTIONS methods and explicit content-type configuration. User headers cannot override framing and are removed across origin redirects.
- Current architecture, configuration, security, validation and release guides, plus a reproducible catalog containing 56 trigger entries (54 generally exposed) and 176 action entries.

### Fixed

- Import streams now use API-26-compatible bounded reads. Full and single-task imports enforce byte, lexical and typed-model quotas before persistence and remain disabled for review.
- Webhook request/header limits apply during byte reads, preventing unbounded line allocation. Empty/oversized tokens cannot authorize execution; request deadlines, concurrency and rate limits are enforced.
- Task details now wires external access enablement, rotation, revocation, token-bearing sharing and legacy webhook token repair. Tokenless links open review; valid custom-scheme links require confirmation and a fresh authorization check.
- Portable serialization omits task-link capabilities and clears webhook tokens. Shared files cannot transfer external execution authorization.
- HTTP destination checks fail closed on unresolved or mixed public/private DNS answers. The transport pins validated addresses, checks each redirect and enforces HTTPS consistently. Private-network permission follows the actual explicit configuration.
- Oversized HTTP responses fail with an empty body and truncation metadata rather than silently passing partial JSON to later actions.
- Elevated failure warnings omit dynamic configuration and result messages. Release auditing checks production and merged manifests against exact permission gates.
- DataStore regression tests now use an isolated atomic Preferences store, avoiding Robolectric/Windows file-rename failures while preserving the store transition contracts under test.

### Changed

- HTTP-only endpoints must migrate to HTTPS, including private-network endpoints. Legacy empty-token webhooks require token regeneration; imported/shared tasks require local capability enablement after review.
- Tag-release publication waits for the lint job as well as the build/package gates. English release notes are derived from this versioned changelog section.
- Historical plans and audits are explicitly distinguished from the current project guides; competitive claims and device coverage are bounded by available evidence.

### Known limitations

- This release does not establish overall superiority or full feature parity with Automate. Catalog counts use different units from Automate blocks.
- New configuration copy has English fallback in several locales. Key parity is not complete translation.
- Physical-device/OEM background reliability, new sensor hardware behavior and framework-permission instrumentation results remain separate validation requirements. Original exported schema snapshots 2 and 13 remain unavailable.

## Quality evidence

The tag workflow gates publication on the following automated checks. Consult the exact tag run and validation record for results; these checks do not establish physical-device coverage:

- Android Lint (zero-tolerance: UnusedResources, MissingTranslation, ExtraTranslation,
  UnusedIds, CheckResult, HardcodedText, TypographyDashes) and Detekt static analysis.
- Catalog parity gates: every `TriggerType` and `ActionType` enum value must exist
  exactly once in the builder picker (restricted entries are pinned explicitly).
- Full unit-test suite, including Room migration, scheduler, lifecycle, and recovery
  regression coverage.
- Release build with R8 full shrinking, APK signature verification (v2/v3 schemes and
  certificate-fingerprint match against the production keystore), 16 KB page-size
  alignment check, zipalign verification, bundletool AAB validation, and Gradle
  dependency verification (SHA-256 checksums for every artifact).
- Tag hygiene: the tag must match the newest `CHANGELOG.md` entry, so the notes below
  are the actual, reviewed change record for this release.

## Install

- Download `NexaFlow-<version>.apk` from the assets below and install it.
- Android updates require a compatible version code and the same signing certificate.
  Back up important tasks before upgrading.
- Pre-release tags (`alpha` / `beta` / `rc`) are marked as pre-releases automatically.

## Documentation

- [Architecture](https://github.com/Alaa91H/NexaFlow/blob/main/docs/ARCHITECTURE.md)
- [Current roadmap](https://github.com/Alaa91H/NexaFlow/blob/main/docs/ROADMAP_2026.md)
- [Validation record](https://github.com/Alaa91H/NexaFlow/blob/main/docs/VALIDATION.md)
- [Plugin SDK](https://github.com/Alaa91H/NexaFlow/blob/main/docs/PLUGIN_SDK.md)
