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
