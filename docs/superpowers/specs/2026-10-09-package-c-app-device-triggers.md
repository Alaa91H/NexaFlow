# Package C: App and device state triggers

## Goal

Complete the existing application and device trigger paths required by issue
#134 Package C without advertising a new trigger capability or changing saved
workflow behavior.

## Existing behavior to preserve

- `APPLICATION` observes app foreground changes through the existing
  accessibility service. A matching foreground app starts one lifecycle; a
  transition away requests the existing exit path. The active execution ledger
  remains authoritative across process restart.
- `APP_INSTALLED` observes `ACTION_PACKAGE_ADDED` and
  `ACTION_PACKAGE_REMOVED`, with `event` values `INSTALLED`, `REMOVED`, and
  `UPDATED` plus an optional exact package filter. `UPDATED` represents an OS
  package replacement/version update.
- `DEVICE` observes screen, power, wired-headset, and Bluetooth state events;
  event-specific runtime sources remain the existing device and Bluetooth
  monitors.
- `USB_CONNECTED` remains a USB charging-state trigger backed by
  `DeviceStateMonitor28` and the battery-changed state snapshot.
- No new `TriggerType`, package-enumeration permission, polling loop, or direct
  execution backend is introduced.

## Changes

1. Type the `DEVICE.event` schema as the exact event strings already exposed by
   the builder and consumed by the monitors: `SCREEN_ON`, `SCREEN_OFF`,
   `POWER_CONNECTED`, `POWER_DISCONNECTED`, `HEADSET_CONNECTED`,
   `HEADSET_DISCONNECTED`, `BLUETOOTH_CONNECTED`, and
   `BLUETOOTH_DISCONNECTED`. Preserve `SCREEN_ON` as the default. Keep the
   existing USB `state` enum (`ON`/`OFF`).
2. Reuse the existing app picker for the optional `APP_INSTALLED.package`
   filter in single-select mode. Persist only the package name, retain the
   existing raw package value on edit, and leave `APPLICATION` multi-select
   behavior intact.
3. Classify package broadcasts deterministically:
   - package added without replacement -> `INSTALLED`;
   - package added with replacement -> `UPDATED`;
   - package removed without replacement -> `REMOVED`;
   - package removed with replacement -> ignored as the intermediate half of
     an update, preventing the old remove/add pair from dispatching `UPDATED`
     twice;
   - unknown action -> ignored.
4. Add unit coverage for schema contracts, app-picker selection mapping, and
   every package broadcast classification. Keep existing foreground duplicate,
   process-recovery, device-exit recovery, and USB state-monitor coverage as
   regression gates; extend only where a concrete uncovered case is found.
5. Record exact local and hosted CI evidence. Emulator/device/OEM behavior is
   explicitly `NOT TESTED` unless a run provides evidence.

## Compatibility and safety

- The event classifier changes only replacement broadcasts: the remove half is
  not a completed install/update and must not fire automation. The add half is
  the sole update event.
- Existing `APPLICATION` and package-filter values remain readable. Selecting
  an installed-package filter does not require `QUERY_ALL_PACKAGES`.
- Unknown package actions and unknown device event values fail closed through
  the existing source/schema contracts; they do not synthesize an exit or run.
- Momentary install/remove/update triggers keep immediate end behavior, and
  stateful screen/foreground events keep the durable lifecycle coordinator.

## Acceptance evidence

- Exact typed schema values match current editor/runtime event strings.
- App picker writes one package key for `APP_INSTALLED` and still writes the
  existing comma-delimited package list for `APPLICATION`.
- Package install, replacement/update pair, removal, and unknown action tests
  pass; replacement yields one `UPDATED` event.
- Focused domain/builder/runtime Gradle tests and source/string audits pass.
- Exact-SHA hosted CI succeeds before the package PR is merged.
- Physical-device, emulator integration, and OEM validation remain `NOT TESTED`
  if CI skips them.
