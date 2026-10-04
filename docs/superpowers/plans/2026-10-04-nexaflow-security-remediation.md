# NexaFlow Security and Reliability Remediation Plan

> **For agentic workers:** Use `executing-plans` to implement this plan inline. Each task ships as a separately verified change; use tests before production edits.

**Goal:** Reduce persistent agent authority, secure LAN transport, detect refresh-token replay, preserve coroutine cancellation, authenticate Wear commands, and close measured test and maintenance gaps without breaking stored grants or device behavior silently.

**Architecture:** Keep `AgentAccessManager` as the credential authority, route approval state through the existing durable `agent-runtime` approval ledger where semantics match, and enforce automation risk at both mutation and execution boundaries. Keep the agent HTTP listener loopback-only until authenticated TLS and pinning are implemented and verified; do not treat a bearer token or echoed fingerprint as transport authentication. Apply focused cancellation and Wear fixes through existing command, audit, repository, and application-scope abstractions.

**Tech Stack:** Kotlin, Android, Room/DataStore, kotlinx.serialization, coroutines, Jetpack Compose, Detekt, Gradle/JUnit, Python repository gates.

**Spec:** User-provided “خطة إصلاحات NexaFlow” dated 2026-10-04 (conversation request).

## Global Constraints

- Preserve historical serialized `PERMANENT_FULL_ACCESS` grants and existing credentials during schema evolution.
- New pairings default to `STANDARD`; never silently lower or broaden an existing grant.
- HIGH/CRITICAL agent-originated changes require a human approval outside the agent channel; approvals bind to exact content and expire/invalidate safely.
- Non-loopback HTTP is forbidden until TLS certificate possession is authenticated and pinned by the client.
- Refresh-token reuse revokes the credential family and all sessions; there is no grace window.
- Catching ordinary errors must rethrow `CancellationException`; do not catch `Throwable` to hide VM/runtime failures.
- Wear mutations must authenticate the source node, use application-lifetime work, and route `run` through human-equivalent guards.
- Never log raw refresh/access tokens, phone numbers, message bodies, or secret values.
- Device-specific claims require physical-device evidence; CI/emulators alone do not establish OEM compatibility.

## Review Focus

- Old serialized grants missing new fields or containing an unknown future mode still deserialize safely and fail closed.
- Concurrent refresh-token exchanges cannot both issue valid sessions or erase evidence of replay.
- Approval is invalidated by any content, identity, or risk-relevant change and cannot be replayed for another automation.
- LAN enable requests cannot expose cleartext listener during a TLS transition, including after process recreation.
- Cancellation during suspend cleanup propagates while cleanup failure remains visible to the user.
- Spoofed Wear node IDs, stale paired devices, duplicate messages, and service destruction do not execute commands.
- Old reports remain discoverable as historical documents after archival and do not retain active security claims.

## Delivery order

### Phase 0 — Baseline and guardrails

- [ ] D1: Remove stale capability and schema counts from `README.md` or generate them from canonical inventory; correct JDK and Android requirements only where source contradicts README. Keep `docs/SECURITY.md` as the current threat guide; archive explicitly stale `*_AUDIT*`, `*RESEARCH*`, and `fix_report_ar.md` documents with dated historical banners and update `docs/README.md` links.
- [ ] Q0: Measure current empty catches and suppression counts; enable Detekt `EmptyCatchBlock` as error, `SwallowedException` and `TooGenericExceptionCaught` as warnings, and `SuspendFunSwallowedCancellation` only if this pinned Detekt version provides it. Add a repository gate that rejects an increase over checked-in suppression budgets. Run the full existing Detekt task before choosing any baseline exception.

### Phase 1 — Agent authority and network security

- [ ] S1a: Add compatible grant modes, default new pairings to `STANDARD`, surface existing permanent grants with explicit downgrade/reconfirmation UI, and safely deserialize old/unknown values.
- [ ] S1b: Reuse durable runtime approvals only after confirming ownership, expiry, redaction, and UI channel. Return a typed approval-required mutation result for HIGH/CRITICAL agent changes.
- [ ] S1c: Persist agent origin and approved content hash; revalidate the exact current automation before each CRITICAL execution; mutation invalidates approval. Add audit events and integration tests.
- [ ] S2a: Until TLS is ready, prohibit non-loopback listener binding and LAN pairing/session exchange; disclose why in settings. Add explicit LAN activation lifetime and a visible expiration notification only if it can be enforced through existing lifecycle services.
- [ ] S2b: Implement AndroidKeyStore-backed TLS, certificate fingerprint proof-of-possession and pinning in the desktop bridge. Validate API 26+ behavior on physical devices before enabling LAN outside loopback.
- [ ] S2c: Add total request deadlines and per-IP connection budgets; test slow-drip connections while proving a valid client remains serviceable.
- [ ] S3: Track the previous refresh-secret hash and rotation time, atomically revoke credential plus sessions when the previous token is replayed, audit without token data, and test old-state migration plus concurrent exchanges.

### Phase 2 — Cancellation and Wear command reliability

- [ ] R1a: Add cancellable exception helper in `core/common`; first cover execution, automation, agent, dashboard and builder critical paths, replacing empty catches with user-visible or structured diagnostic outcomes.
- [ ] R1b: Prove coroutine cancellation propagates through disable cleanup/condition gates and surface restoration failures.
- [ ] R2a: Validate `MessageEvent.sourceNodeId` against registered devices; reject unknown nodes before parsing or executing commands.
- [ ] R2b: Move command work to application-scoped execution/WorkManager, dedupe replayed commands, and route `run` through the human command/condition gate.
- [ ] R2c: Verify service termination behavior and command completion on a physical phone/watch pair; record skipped when hardware is unavailable.

### Phase 3 — Tests and secure storage

- [ ] T1a: Add adversarial HTTP parser tests and an exhaustive operation-by-scope authorization matrix; use deterministic JVM-generated fuzz inputs with no new dependency unless existing infrastructure supports it.
- [ ] T1b: Add behavior tests for `feature/ai`, `capability-manager`, and `feature/themes`; raise `agent-security` / `agent-api` coverage thresholds only after a CI baseline measurement.
- [ ] K1: Distinguish missing secrets from undecryptable secrets, log only key lifecycle metadata, and evaluate locked-device/StrongBox policy with API-gated fallback tests.

### Phase 4 — Maintainability

- [ ] M1a: Characterize behavior, then split one oversized file per change, beginning with `ExecutionEngine`; preserve public behavior and run module tests per split.
- [ ] M1b: Establish realistic `LargeClass` / `LongMethod` thresholds with a baseline and review suppression reasons without increasing budgets.
- [ ] M2: Publish an ADR for canonical vs legacy automation models with an owner, removal condition, target version and tracking issue before deleting any adapter.

### Phase 5 — Distribution and project sustainability

- [ ] P1a: Document the distribution target; only create Play/full flavors after enumerating permission-dependent product paths and verifying each flavor's merged manifest.
- [ ] P1b: Verify Maps key restrictions in Google Cloud Console without exposing credentials; add a local release-signing guard with an explicit debug-signing opt-in.
- [ ] B1: Add CODEOWNERS and a concise contributor guide with architecture tour, test commands and issue/PR templates; only create issue labels if authorized repository access and clear ownership exist.

## Acceptance evidence

- Phase 0: current generated inventory check passes; README has no stale fixed counts; `detekt` and suppression-budget tests pass.
- Phase 1: tests prove STANDARD cannot perform HIGH/CRITICAL actions without visible human approval; edits invalidate approval; old refresh replay revokes all sessions; no unauthenticated cleartext LAN path exists.
- Phase 2: zero empty catches in named critical modules; cancellation propagates; unregistered Wear source is rejected; physical command completion is documented separately from JVM/CI.
- Phase 3: hostile HTTP inputs and full authorization matrix are covered; no production module remains without a behavior test; secret retrieval exposes distinct failure states.
- Phase 4: per-file decomposition keeps tests and behavior stable; legacy removal has an accepted ADR.
- Phase 5: package/flavor manifests and signing policy are verified, and contributor ownership docs are present.

## Deferred until evidence exists

- Do not claim `SSLServerSocket`/AndroidKeyStore works on API 26–28 from JVM tests; require real-device checks.
- Do not accept an echoed `transportKeyFingerprint` as proof of possession.
- Do not upgrade agent coverage thresholds based on estimated test:main ratios.
- Do not delete history, old reports, stored grant data, adapters or release tags to make metrics look clean.
