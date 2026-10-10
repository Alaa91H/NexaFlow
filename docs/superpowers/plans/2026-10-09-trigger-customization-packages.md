# Eight Trigger Customization Packages — #134

This plan executes issue #134 as eight independently reviewable packages. Each
package gets its own PR, exact-SHA CI evidence, and merge before work advances.
The parent issue remains open until all packages meet their evidence gates.

## Package order and evidence boundary

1. **A — Time/calendar recurrence and exclusions.** Preserve the existing
   recurrence, timezone, DST, misfire, and range lifecycle contracts. Add only
   verified gaps; current device/OEM behavior remains `NOT TESTED` unless run.
2. **B — Connectivity/network.** Validate/captive/metered/VPN/SSID transitions
   and bounded probes; do not persist SSIDs or BSSIDs as diagnostics.
3. **C — Apps/device state.** Foreground, install/version, screen, USB, and power
   transitions, gated by real monitor/backend paths.
4. **D — Battery/thermal/sensors.** Thresholds, calibration, hysteresis, and
   sampling budgets bounded by actual Android sources.
5. **E — Notification/SMS/call.** Content privacy, role/permission/consent,
   revocation, and bounded matching.
6. **F — Location/motion.** Multi-geofence entry/dwell/exit, accuracy, and
   background limits.
7. **G — Bluetooth/BLE/NFC/USB.** Device/profile/UUID/vendor filters, scan
   budgets, and disconnect semantics.
8. **H — Plugin/Wear/webhook/external events.** Authenticated typed payloads,
   stable identity, TTL, quotas, and reconnect behavior.

## Package A: excluded dates

- [x] Confirmed current shared scheduler covers repeat rules, fixed/device
  timezone policy, DST gap/fold behavior, bounded misfires, and range start/end.
- [x] Added optional `excludedDates` as a comma-delimited list of up to 64 unique
  ISO local dates; absent key preserves legacy schedules.
- [x] Added builder date picker, removable selections, and translations in all
  ten supported locales.
- [x] Runtime skips excluded occurrences and fails closed for malformed or
  oversized persisted lists.
- [x] Catalog validation rejects malformed, duplicate, and over-limit lists.
- [x] Domain regression tests and builder compilation passed locally.
- [ ] Exact-SHA hosted CI and PR merge.
- [ ] Physical-device/OEM scheduling validation (`NOT TESTED`).

### Local evidence (2026-10-09)

- `:domain:testDebugUnitTest --tests '*TimeTriggerCalculatorTest' --tests '*AutomationNodeCatalogTest' :feature:automation-builder:compileDebugKotlin --no-configuration-cache --no-daemon --console=plain` — exit 0.
- `scripts/check_strings_parity.py` — `PARITY_PROBLEMS: 0`.
- `scripts/audit_translation_completeness.py` — `TRANSLATION_PROBLEMS: 0`.
- `scripts/check_resources.py` — all resource, translation, typography, and lint checks zero; `RESOURCE_GATE: OK`.
- `git diff --check` — exit 0.
- Android alarm delivery, emulator integration, physical device, and OEM behavior: `NOT TESTED`.

## Package C: app and device state

- [x] Ignore package-replacement removal and emit one update on replacement add; cover every classifier branch with unit tests.
- [x] Constrain `DEVICE.event` to current screen, power, headset, and Bluetooth event strings while preserving USB `ON`/`OFF`.
- [x] Reuse the app picker for the optional `APP_INSTALLED.package` filter; retain `APPLICATION` multi-select behavior.
- [x] Remove the obsolete `optional_package` translation from all supported locales; resource and parity gates pass.
- [x] Focused classifier, schema/catalog, picker tests passed locally; builder Kotlin compilation passed.
- [x] Complete combined monitor regression suite and final inventory/resource/string/diff gates.
- [x] Exact-SHA hosted CI passed for PR #241 and the PR merged.
- [ ] Emulator, physical-device, and OEM package-manager validation (`NOT TESTED`).

## Package D: battery, thermal, and sensors

- [x] Reuse existing battery and battery-temperature threshold, hysteresis, and
  stable-duration runtime paths; no duplicate power monitor was introduced.
- [x] Add unit-bounded numeric sensor calibration offsets, with legacy configs
  retaining zero-offset behavior and sensor changes clearing stale calibration.
- [x] Add bounded sensor sampling choices and apply the fastest configured rate
  among enabled triggers sharing a physical sensor; re-register on rate changes.
- [x] Add sensor editor controls and typed schema fields for calibration and
  sampling; existing battery and battery-temperature hysteresis remains in its
  established runtime paths.
- [ ] Numeric sensor hysteresis and stable-duration runtime wiring remain a
  follow-up; no new controls claim support for them in this package.
- [x] Run focused domain, sensor matcher and lifecycle tests, plus builder
  compilation; all passed locally.
- [x] String parity and resource gates passed; no physical sensor, thermal,
  emulator, or OEM behavior was claimed as tested.
- [x] Exact-SHA hosted CI passed and PR #243 merged as `b21e1918c9eb70c811f8ae345cb6bed2c55218cc`.
- [ ] Physical sensor, thermal transition, emulator, and OEM validation
  (`NOT TESTED`).

## Package E: notifications, SMS, and calls

- [x] Incoming-call triggers request Android's call-screening role and expose
  its live grant status; broad phone-state access is no longer incorrectly
  shown as the trigger requirement. Existing `CALL_STATE` keeps its own
  phone-state permission.
- [x] Contact caller classification requests `READ_CONTACTS` only when the
  trigger category is `CONTACT`; unavailable contact access safely classifies
  callers as unknown.
- [x] Notification matching applies both package lists and content filters,
  and continues to honor legacy singular `package` configurations.
- [x] SMS and call matchers reject unknown persisted match modes instead of
  broadening a trigger unexpectedly.
- [x] Added focused regression tests for call-role/contact requirements,
  notification package compatibility, and fail-closed SMS/call modes.
- [x] Local focused Gradle tests and builder Kotlin compilation passed; string,
  translation, resource, atomic-inventory, and diff gates passed.
- [ ] Exact-SHA hosted CI and PR merge.
- [ ] Validate role grant/revocation and notification/SMS delivery on physical
  devices and supported OEM builds (`NOT TESTED`).

## Development branch workflow

- [x] Use `feat/trigger-customization-followup` as the single ongoing development
  branch, based on current `main`; package changes are reviewed and merged to
  `main` via their package PR before work advances.
- [ ] Continue from the updated `main` after each package merge and retain the
  clean integration branch for ongoing follow-up work.
