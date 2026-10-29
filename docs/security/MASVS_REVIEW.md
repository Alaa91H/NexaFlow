# MASVS review (code-grounded)

Scope: the local agent API, agent pairing/session storage, privileged execution
channels (Shizuku/Root/ROM), backups, diagnostics export and the release
pipeline. This is a **static review plus the existing test evidence**, not a
penetration test or certification. A third-party assessment is still required
before claiming MASVS compliance.

Legend: ✅ implemented and covered by a test or CI gate; ⚠️ implemented with a
residual risk; ⛔ not implemented / blocked.

## MASVS-STORAGE

| Control | Evidence | Status |
|---|---|---|
| No secrets in plain preferences | `core:security/KeystoreSecureStorage` wraps Android Keystore; agent secrets stored as SHA-256 hashes (`AgentTokenCodec.hash`, constant-time `matches`) | ✅ |
| Backups exclude sensitive data | `android:allowBackup="false"`, `backup_rules.xml`/`data_extraction_rules.xml` on phone **and** Wear (added in NFA-16) | ✅ |
| Logs redact credentials | `core:logging/RedactingLogStore` + `SecretRedactor` at the logging boundary; the diagnostic bundle redacts again (`DiagnosticBundleTest`) | ✅ |
| No world-readable files | Files are written to app-private cache; FileProvider exposes only `backup/`, `updates/`, `diagnostics/` with one-shot grants | ✅ |
| Credentials never leave the device | AI credentials are only sent to the provider the user configured | ⚠️ inherent to the feature; documented in `DATA_SAFETY.md` |

## MASVS-CRYPTO

| Control | Evidence | Status |
|---|---|---|
| Platform crypto only | `MessageDigest`/`SecureRandom`/Keystore; no custom primitives | ✅ |
| Token comparison is constant time | `MessageDigest.isEqual` in `AgentTokenCodec.matches` | ✅ |
| Transport is TLS | `usesCleartextTraffic="false"`; AI/webhook endpoints use HTTPS; LAN agent access disabled until TLS exists (`AgentApiHostPolicy.LAN_TLS_TRANSPORT_AVAILABLE`) | ✅ |
| Pinned trust for updates | Release APKs are signed; update verification checks the signing certificate SHA-256 in CI | ✅ |

## MASVS-AUTH

| Control | Evidence | Status |
|---|---|---|
| Bearer token required on every agent route | `AgentApiControllerTest` (missing/invalid token, read-only scope, disabled access) | ✅ |
| Pairing/session lifecycle | `AgentAccessManagerTest`; tokens are hashed, revocable and expiring | ✅ |
| Authorization scopes | read-only grant cannot run/disable/delete (`scope_denied` cases) | ✅ |
| Browser-origin requests rejected | `Origin` header check (`browser_origin_rejected`) | ✅ |
| Host header constrained | `AgentApiHostPolicy` allows loopback only; DNS names rejected | ✅ |
| Rate limiting / brute force | Controller tests cover limits; long-lived lockout is a follow-up | ⚠️ |
| Human approval for high-risk agent mutations | `ApprovalRequired` → HTTP 428 with a content hash bound to the exact definition | ✅ |

## MASVS-NETWORK

| Control | Evidence | Status |
|---|---|---|
| Cleartext blocked | manifest `usesCleartextTraffic="false"` | ✅ |
| Local API bound to loopback | `AgentApiServer` binds `127.0.0.1`; `ALL_INTERFACES` constant removed (NFA-16) | ✅ |
| Request size/parser bounds | `AgentHttpRequestParser` caps header (32 KiB) and body (256 KiB), rejects chunked encoding and negative content length; covered by controller/parser tests | ✅ |
| Webhook SSRF guard | `WebhookRequestGuard` + `HttpUrlPolicy` restrict schemes/hosts | ✅ |
| LAN mode | intentionally disabled, cannot be enabled by preference | ✅ (feature absent) |

## MASVS-PLATFORM

| Control | Evidence | Status |
|---|---|---|
| Exported components audited | `scripts/audit_exported_components.py` + instrumentation `FrameworkPermissionGatesTest`; CI runs the merged-manifest audit | ✅ |
| Intents validated | Exported receivers/services validate actions and extras; boot/alarm receivers use explicit intents | ✅ |
| No dynamic code loading outside plugins | Plugin SDK loads only user-installed, signature-checked plugins | ⚠️ plugin boundary reviewed separately in `docs/AGENT_SECURITY.md` |
| Deep links validated | Agent deep-link tokens are single-use and bound to a task id | ✅ |
| Accessibility service scoped | Declared service is app-trigger-only and user-enabled; no keylogging surface | ⚠️ manual review on device still required |

## MASVS-RESILIENCE

| Control | Evidence | Status |
|---|---|---|
| Release is minified | `isMinifyEnabled = true`; R8 mapping uploaded as a release asset and to the release job | ✅ |
| Debug signing cannot ship | release workflow rejects debug certificates (`-PallowDebugSigning` only for CI assemble checks) | ✅ |
| Integrity/provenance | SHA-256 sums + SLSA attestation added to the release job (`actions/attest-build-provenance` pinned by SHA) | ✅ (first attested release still to be produced) |
| Tamper detection | not implemented | ⛔ out of scope for a sideloaded automation app; documented as accepted risk |
| Anti-debugging/root detection | intentionally absent (root/Shizuku are supported features) | ✅ by design |

## Privileged execution boundary

- All shell strings are built with `SafeCommandBuilder` (single-quote escaping,
  control-character rejection, length cap) — `SafeCommandBuilder` tests cover
  injection attempts.
- Privileged commands run only through the selected provider
  (`ExecutionChannelSelector`); a denied grant produces a typed failure, not a
  silent retry storm.
- Root/Shizuku detection has injectable path resolution
  (`SystemAppStatusDetector`), which also removed the host-dependent test
  failures (NFA-17).

## Residual risks / required follow-up

1. **Third-party penetration test of the agent API** — BLOCKED on procurement;
   the in-repo contract tests are not a substitute.
2. **Rate-limit lockout policy** — needs a product decision (lockout duration,
   user notification) before implementation.
3. **Plugin sandbox review** — plugin code runs in-process; the trust model is
   documented but not independently reviewed.
4. **Physical-device validation** of the accessibility service and privileged
   actions — BLOCKED on devices (see `docs/evidence/device/`).
