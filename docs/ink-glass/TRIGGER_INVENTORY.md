# Trigger inventory — the Device group audit

The trigger picker used to mirror the domain catalog's *semantic* families, which
collapsed ten families into one `DEVICE` bucket. That bucket carried thirty
entries (screen, power, sound, display, storage, sensor, ROM, webhook…), many of
which fired the same event in a slightly different spelling, so the group read as
a wall of near-duplicates.

This document is the inventory behind the pruning: what each group now holds,
which entries were removed from the *add* flow and what covers them, and which
triggers keep a purpose-built editor instead of the schema renderer.

## 1. Groups (`TriggerCategory`)

| Group | Triggers |
| --- | --- |
| Schedule | Time, Calendar |
| Power | Battery, Charger, Power saver, Data saver, Storage low, Battery temperature |
| Sound | Ringer mode, Volume changed, Media playing, Headphones, Do not disturb |
| Display | Brightness, Auto brightness, Dark mode, Auto rotate, Screen rotation, Screen timeout, Stay awake |
| Connectivity | Wi-Fi connected, Mobile data, Hotspot, Network mode, Bluetooth, NFC, Ethernet, VPN, Data roaming, Wi-Fi signal, Cell signal, NFC tag |
| Device | Device (screen on/off), Locked/unlocked, Boot completed, HDMI, Sensor, Clipboard, Time zone, Alarm, Wear, ROM setting, Webhook |
| Location | GPS geofence, Location mode |
| Apps | App (foreground), App installed, App notification |
| Communication | SMS, Incoming call, Call state |

Two rules keep the split honest:

- `triggerCategoryOf` is an explicit map, not a derivation from
  `AutomationNodeFamily`; a new trigger without a group fails
  `CatalogParityTest.everyVisibleTriggerHasACategory`.
- Only groups with at least one visible entry appear in the picker, so pruning
  can never leave an empty accordion behind.

## 2. Duplicates removed from the add flow

| Hidden | Covered by | Why |
| --- | --- | --- |
| `CONNECTIVITY` (legacy combined) | `WIFI_CONNECTED` / `MOBILE_DATA_CONNECTED` | One trigger for two networks was never configurable per network. |
| `USB_CONNECTED` | `CHARGER` + source *USB* | A USB plug is a charger event with a known source. |
| `WIFI_STATE` | `WIFI_CONNECTED` | Wi-Fi on/off and Wi-Fi connected/disconnected are the same signal. |
| `BLUETOOTH_DEVICE` | `BLUETOOTH_STATE` | Per-device pairing needed a typed device identity; the state trigger covers the connect/disconnect events without typed data. |

Saved tasks that use a hidden type keep running and stay editable in the
builder; only the *add* catalog is pruned. `visibleTriggerTypeOptions` is the
single place that applies the rule, and
`CatalogParityTest.everyHiddenTriggerPointsAtAVisibleReplacement` fails if a
hidden type ever points at something the picker does not offer.

### Inside the generic Device trigger

`TriggerType.DEVICE` also offered `POWER_CONNECTED`, `POWER_DISCONNECTED`,
`HEADSET_CONNECTED`, `HEADSET_DISCONNECTED`, `BLUETOOTH_CONNECTED` and
`BLUETOOTH_DISCONNECTED` next to its screen events — three more copies of the
Charger, Headphones and Bluetooth triggers. The canonical editor now hides those
tokens (`hiddenDeviceEventTokens`), so Device is about the screen, which is what
it is for. The stored events remain valid.

## 3. Editors: what is a choice, what is a number

No trigger asks for a number to be typed. The rules:

| Field kind | Control |
| --- | --- |
| Fixed choice (enum) | Translated chips (`schemaTokenLabel`), single choice is labelled by its own text |
| Bounded number | `SteppedValueRow`: value line, − / +, slider |
| Duration | ROM presets where the ROM defines them, otherwise a − / + stepper in minutes |
| App | Installed-app sheet (single or multi), never a package-name box |
| Time / date / time zone | Picker dialog |
| Free text | Text box — only for a search phrase, a webhook path or an SSID |

### Per-trigger notes

- **Volume changed** — one trigger, every sound stream: each stream has a switch,
  its own *above/below* direction and its own level. Stored as
  `streams=MUSIC,RING` plus `threshold_<STREAM>` / `direction_<STREAM>`; the first
  enabled stream is mirrored onto the legacy `stream` / `threshold` / `direction`
  keys so pre-existing tasks and older readers behave exactly as before.
- **Brightness** — stored on the ROM's 0..255 scale, shown as the percentage the
  system shade shows.
- **Storage low** — stored in megabytes, shown in MB below 1 GB and GB above.
- **Battery temperature** — −20…80 °C with 0.5 °C steps.
- **Screen timeout** — the choices are the device's own `SCREEN_OFF_TIMEOUT`
  (read from the ROM) plus the standard preset ladder; the current value is
  marked in the hint.
- **Charger** — connected/disconnected *and* the charging source: any, wall
  (AC), computer (USB) or wireless. The engine filters the plug mask through
  `ChargerSourceFilter`.
- **Notifications** — the app is chosen from the installed-app sheet (it used to
  do nothing: the picker only applied to the *app* trigger), and the kinds are
  real filters mapped onto the platform's notification categories
  (messages, calls, reminders/events, promotions, ongoing/system).
- **Headphones, media playing, ringer mode** — translated chips only; these were
  showing raw tokens (`CONNECTED`, `STARTED`, `NORMAL`) with a storage-key header.
- **Signals** — 1..4 bar chips plus direction.
- **Sensors, location, time, ROM setting, webhook** keep their dedicated editors.

## 4. Engine support (behaviour, not decoration)

| Feature | Engine change |
| --- | --- |
| Per-stream volume | `VolumeMonitor` evaluates every configured stream against its own level and stays active while any of them matches. |
| Charger source | `BatteryTriggerMonitor` (`BatteryMonitor`) accepts the plug only when `ChargerSourceFilter` agrees with the configured source. |
| Notification kinds | `NotificationListener` forwards the notification category; `NotificationTriggerMatcher` rejects a category that is not in the configured list. |

Every rule above is covered by a unit test: `SchemaFieldPresentationTest`,
`ChargerSourceFilterTest`, `NotificationTriggerMatcherTest`,
`VolumeMonitorLifecycleTest` and the Robolectric render tests in
`TriggerStateConfigEditorsTest` / `LegacyTriggerCardRenderTest`.
