# Security model

NexaFlow executes user-configured actions with the permissions and elevated providers granted to the application. A saved task is executable configuration. Review imported tasks before enabling them, particularly shell, plugin, device-administration and network actions.

## External boundaries

- Custom task links require a per-task random token plus a confirmation dialog. Tokens can be rotated or revoked. Tokenless links are review-only. Custom schemes are not verified HTTPS App Links and can be intercepted by other applications.
- Local webhooks require authentication. The reader bounds bytes while reading CRLF lines, aggregate headers, concurrent handlers and total read time. Oversized tokens are rejected before constant-time comparison. Authentication does not create a remote networking service.
- HTTP actions use HTTPS, validate every resolved address, pin DNS results for the connection and revalidate every redirect. Private-network access is explicit. Normal TLS validation is retained.
- Import streams are bounded to 4 MiB before parsing. A lexical scan bounds JSON recursion, string size and aggregate parser work; typed quotas cover metadata, nested configuration and workflow shape. Full and single-task imports use this boundary. Imported tasks remain disabled.
- Portable serialization excludes deep-link tokens, and backup export/import clears webhook tokens. Other action credentials and payloads are not automatically secret-scrubbed.
- Elevated warning messages do not include dynamic configuration or command results. This does not certify that arbitrary plugin code or all external providers avoid logging.
- CI audits production source manifests and the merged release manifest against explicit exported-component gates. A declared permission name alone is insufficient.

## Storage and migration

Room schema 19 persists task capability tokens separately from portable serialization. Migration 18→19 adds a nullable column. Historical migration chains are exercised by tests; original exported schema snapshots 2 and 13 remain unavailable and have not been fabricated. Back up important workflows before upgrades.

## Evidence and limits

See the [audit disposition](security-audit-2026-09-16-implementation.md) and [validation record](VALIDATION.md). JVM/Robolectric and socket tests do not replace Android device tests, security review, OEM validation or long-duration background testing. Instrumentation checks for framework permission protection levels are supplied separately and require a device/emulator.

Report vulnerabilities privately through the repository owner's available contact/security channel. Include a minimal reproduction and affected revision; redact live secrets. Do not publish production tokens in issues.
