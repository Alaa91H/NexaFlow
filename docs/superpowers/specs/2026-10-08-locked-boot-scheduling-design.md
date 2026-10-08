# Locked Boot and Scheduled Automation Design

## Intent

Implement issue #128 on the merged T04 base `4a643b00a84a9e28eced53ecaec7e40cd5a9bb7a`. Scheduled automations use credential-protected Room/DataStore state, so they wait until Android has unlocked the user after boot. The runtime must not advertise a pre-unlock receiver path that cannot safely access its dependencies.

## Decisions

### Boot and storage

- Keep `AutomationAlarmReceiver` non-direct-boot-aware and keep automation state in the existing credential-protected stores.
- Remove `LOCKED_BOOT_COMPLETED` from its manifest filter and recovery dispatch. Do not add device-protected storage, a second scheduler, or a pre-unlock execution subset.
- Run boot recovery on `BOOT_COMPLETED` after user unlock and package-update recovery on `MY_PACKAGE_REPLACED`. Package replacement must not synthesize a user `BOOT_COMPLETED` trigger.
- Recovery order stays: reconcile only durable/known elapsed exits; load automations; cancel/rebuild schedule identities and re-arm a retained active range end; then deliver true boot triggers only for `BOOT_COMPLETED`; finally schedule monitoring startup through its existing alarm path.

### Calendar and occurrence identity

- `TimeTriggerCalculator` remains the only wall-clock schedule engine. Both `AutomationScheduler` and Dashboard preview consume its next-occurrence result.
- Device-local schedules follow the device zone; fixed IANA schedules retain their named zone when the device travels. Manual clock, zone-ID, and offset changes trigger reconciliation.
- A nonexistent scheduled start during a DST gap is skipped for that calendar date. A repeated local time during a DST fold is one occurrence at the earlier offset. A range end in a gap is shifted forward by the gap length; an end in a fold uses the earlier offset. Overnight ends use the next local calendar date, so elapsed duration may be 23 or 25 hours.
- The immutable occurrence ID derives from scheduled start/end instants; the generation binds automation ID, normalized trigger configuration, and those instants. PendingIntents remain untrusted until their ID and generation match the durable schedule ledger.

### Misfires and capability fallback

- A delivered point occurrence has a 15-minute grace measured from its immutable scheduled start. Later delivery is skipped and recorded in existing execution history using the existing `Skipped:` classification; it is never silently replayed after reboot, clock changes, or permission restoration.
- A range start may run late only while its window is still open. At or after its immutable end it is skipped and recorded without running start actions. A range that starts before the end is closed through the existing `ExitCoordinator`; its durable lifecycle and END identity remain authoritative.
- Exact-alarm access continues to use the exact API when available and the existing idle-safe inexact fallback otherwise. Grant/revocation events rebuild from current configuration. Inexact delivery may exceed the grace, in which case the occurrence is visibly skipped rather than replayed later.
- Pause, edit, delete, and reboot preserve current durable lifecycle rules: cancel stale future identities, keep an active range's matching old-generation END only when the lifecycle still owns it, and retain failed or uncertain exits for reconciliation/review.

## Alternatives considered

1. **Recommended: wait for first unlock.** Remove the misleading locked-boot filter and retain credential-protected storage; this matches issue #128 and avoids a second source of scheduler state.
2. **Device-protected boot marker.** Would require separately approved minimal state, migration, synchronization, and device acceptance; issue #128 explicitly says not to create this subset by default.
3. **Keep the filter but ignore its intent.** Leaves a false manifest contract and invites accidental pre-unlock behavior later, so it is rejected.

## Components and verification

- `app/src/main/AndroidManifest.xml` and `AutomationRuntimeManifestTest`: prove the receiver is not direct-boot-aware and has no locked-boot action.
- `AutomationAlarmReceiver`: recover only after unlock; classify expired deliveries before side effects and write a skipped history record.
- `TimeTriggerCalculator`: produce canonical start/end/identity data and pin gap/fold/overnight semantics.
- `AutomationScheduler` and `DashboardScreen`: use the same calculator result; preserve durable ledger and PendingIntent generation behavior.
- Tests cover boot action routing, misfire boundaries, exact-alarm fallback policy, time/zone-change broadcasts, DST gap/fold cases, overnight transitions, and schedule generation invalidation. Emulator/physical behavior is reported only if actually run.

## Evidence limits

JVM/Robolectric/CI results do not prove first-unlock timing, AlarmManager delivery under OEM power management, permission Settings behavior, or reboot behavior on a physical device. These remain `NOT TESTED` without connected-device evidence.
