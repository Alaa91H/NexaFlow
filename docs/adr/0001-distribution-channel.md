# ADR 0001 — Distribution channel and permission profile

- Status: **Proposed — awaiting owner decision** (OQ-02 / OQ-05)
- Date: 2026-10-29
- Deciders: project owner, release maintainer, security reviewer
- Related: `docs/OPEN_QUESTIONS.md`, `docs/security/PERMISSION_DISCLOSURES.md`,
  `docs/audit/FULL_AUDIT_REMEDIATION_PLAN_2026-10-28_AR.md` (NFA-10)

## Context

Today NexaFlow ships APKs only through GitHub Releases
(`.github/workflows/nexaflow-ci.yml`, `release` job). The manifest declares 42
permission entries, including capabilities Google Play restricts or rejects in
their default form:

- `QUERY_ALL_PACKAGES` — allowed only for a core user-facing function and with
  a Play declaration;
- `REQUEST_INSTALL_PACKAGES` — self-update outside Play; Play requires an
  approved use case or its removal;
- `SEND_SMS` / `RECEIVE_SMS` — restricted; requires a declared core function and
  may be rejected;
- `READ_LOGS`, `FORCE_STOP_PACKAGES`, `MODIFY_PHONE_STATE`,
  `WRITE_SECURE_SETTINGS`, `STATUS_BAR` — signature/privileged permissions that
  are only granted on custom ROMs or through `adb`/Shizuku;
- Accessibility is not declared as a `BIND_ACCESSIBILITY_SERVICE` in this app;
  it is used through user-granted system settings where applicable.

The build has no product flavors, so one artifact must satisfy every channel.
That is incompatible with a Play submission that cannot declare the restricted
permissions.

## Options

1. **GitHub-only (status quo).** No store review, full privileged feature set,
   but no Play distribution and no Play Data Safety surface.
2. **Play-only.** Remove `QUERY_ALL_PACKAGES`/`REQUEST_INSTALL_PACKAGES`, drop
   or gate privileged integrations, publish privacy policy and Data Safety.
   Loses app/plugin discovery breadth and self-update.
3. **Dual channel with flavors (recommended if Play is a goal).** A
   `distribution` flavor dimension:
   - `github`: current manifest, self-update, full discovery;
   - `play`: no `QUERY_ALL_PACKAGES` (use `<queries>` for known intents),
     no `REQUEST_INSTALL_PACKAGES`, in-app update via Play Core, Data Safety
     and permission declarations completed.
   Both flavors share the same code; only the manifest, updater and a few
   capability gates differ.

## Decision

**Not yet made.** The owner must choose between option 1 and option 3 before
flavor work starts. Option 2 is not viable without dropping documented
capabilities.

## Consequences if option 3 is chosen

- New source set `app/src/play/AndroidManifest.xml` plus
  `app/src/play/res/xml/` for the restricted-permission removals.
- `UpdateChecker`/`UpdateViewModel` gain a Play-Core path behind the flavor.
- `CapabilityCenter` must report discovery/self-update as unavailable on
  `play` instead of failing at runtime.
- CI builds `assembleGithubRelease` and `assemblePlayRelease`; the release job
  uploads the GitHub flavor and, separately, an AAB for Play.
- Data Safety answers derive from `docs/security/DATA_SAFETY.md`.
- The current single-artifact release pipeline and its verification steps must
  be updated in the same change.

## Consequences if option 1 is chosen

- Keep the single artifact and the GitHub release pipeline.
- Record the decision in `docs/OPEN_QUESTIONS.md` (close OQ-02/OQ-05) and state
  explicitly that Play distribution is out of scope.
- No flavor work is needed; the permission disclosures remain documentation
  only.

## Required next action

Owner signs one option. Until then, flavor work stays `BLOCKED` and the
permission documentation in `docs/security/` is the single source of truth.
