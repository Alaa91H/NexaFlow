# Capability catalog

Generated from source by `python scripts/generate_capability_catalog.py`. This inventory counts enum entries, not equivalent competitor blocks or device-certified capabilities.

**56 trigger entries; 176 action entries.** Two triggers have restricted creation paths. The SENSOR entry has 12 configuration modes; the eight DATA actions each offer several operations. See [configuration](CONFIGURATION.md) and [validation](VALIDATION.md).

## Semantic operations (Capability-Adaptive Execution)

Twelve device-state action types are additionally expressed as 24 paired semantic operations in `OperationRegistry` (`WIFI_GET_STATE`/`WIFI_SET_STATE`, `BLUETOOTH_*`, `MOBILE_DATA_*`, `HOTSPOT_*`, `NFC_*`, `LOCATION_*`, `AIRPLANE_MODE_*`, `ROTATION_*`, `BRIGHTNESS_GET/SET`, `SCREEN_TIMEOUT_*`, `DND_*`, `DATA_SAVER_*`), plus four package operations (`PACKAGE_FORCE_STOP`, `PACKAGE_CLEAR_DATA`, `PACKAGE_SET_ENABLED_STATE`, `PACKAGE_GET_ENABLED_STATE`) served by the Shizuku and Root typed strategies with bounded `pm` argv and a real read-back post-condition; clear-data is honestly classified as irreversible (no compensation). The five legacy package action types (`APPLICATION_CLOSE_APP`, `SYSTEM_FORCE_STOP_APP`, `SYSTEM_CLEAR_APP_DATA`, `SYSTEM_ENABLE_APP`, `SYSTEM_DISABLE_APP`) map into the router with full config-compatibility. For each, the router advertises only the strategies that really ship: public Android API, Shizuku typed operations (closed `PrivilegedOperation` argv over the UserService AIDL; availability requires a bound service, not merely a granted permission), root typed operations, and an explicit Settings hand-off. Strategies marked in the specs but not yet implemented (device owner, OEM-specific) are never selected; unsupported is reported honestly. See the [execution contract](architecture/capability-adaptive-execution.md).

## Triggers

| Enum | Creation path |
| --- | --- |
| `TIME` | General builder picker |
| `BATTERY` | General builder picker |
| `APPLICATION` | General builder picker |
| `DEVICE` | General builder picker |
| `CONNECTIVITY` | Legacy saved-task compatibility |
| `WIFI_CONNECTED` | General builder picker |
| `MOBILE_DATA_CONNECTED` | General builder picker |
| `HOTSPOT` | General builder picker |
| `LOCATION` | General builder picker |
| `SMS` | General builder picker |
| `BLUETOOTH_DEVICE` | General builder picker |
| `RINGER_MODE` | General builder picker |
| `NETWORK_MODE` | General builder picker |
| `NOTIFICATION` | General builder picker |
| `CALENDAR` | General builder picker |
| `SENSOR` | General builder picker |
| `WEBHOOK` | General builder picker |
| `ROM_SETTING` | General builder picker |
| `HEADPHONE` | General builder picker |
| `CHARGER` | General builder picker |
| `AIRPLANE_MODE` | General builder picker |
| `DARK_MODE` | General builder picker |
| `CALL_STATE` | General builder picker |
| `INCOMING_CALL` | General builder picker |
| `APP_INSTALLED` | General builder picker |
| `MEDIA_PLAYING` | General builder picker |
| `VOLUME_CHANGED` | General builder picker |
| `POWER_SAVER` | General builder picker |
| `BLUETOOTH_STATE` | General builder picker |
| `BRIGHTNESS_LEVEL` | General builder picker |
| `STORAGE_LOW` | General builder picker |
| `AUTO_ROTATE` | General builder picker |
| `DATA_SAVER_STATE` | General builder picker |
| `DEVICE_LOCKED` | General builder picker |
| `WIFI_STATE` | General builder picker |
| `NFC_STATE` | General builder picker |
| `LOCATION_STATE` | General builder picker |
| `SCREEN_ROTATION_STATE` | General builder picker |
| `WIFI_SIGNAL_STRENGTH` | General builder picker |
| `CELL_SIGNAL_STRENGTH` | General builder picker |
| `BATTERY_TEMPERATURE` | General builder picker |
| `USB_CONNECTED` | General builder picker |
| `HDMI_CONNECTED` | General builder picker |
| `ETHERNET_CONNECTED` | General builder picker |
| `VPN_CONNECTED` | General builder picker |
| `CLIPBOARD_CHANGED` | General builder picker |
| `DND_STATE` | General builder picker |
| `STAY_AWAKE_STATE` | General builder picker |
| `AUTO_BRIGHTNESS_STATE` | General builder picker |
| `SCREEN_TIMEOUT_CHANGED` | General builder picker |
| `DATA_ROAMING_STATE` | General builder picker |
| `TIMEZONE_CHANGED` | General builder picker |
| `BOOT_COMPLETED` | General builder picker |
| `NFC_TAG_SCANNED` | General builder picker |
| `ALARM_SET_CHANGED` | General builder picker |
| `PLUGIN_EVENT` | Plugin configuration flow |

## Actions

Availability depends on permissions, capabilities, Android version and hardware. Settings-opening actions open system UI; their presence does not mean the app can silently change that setting. Elevated actions require a supported provider. Registry and catalog parity tests check dispatch/picker coverage, not every device outcome.

| Enum | Builder label |
| --- | --- |
| `SYSTEM_BRIGHTNESS` | Brightness |
| `SYSTEM_VOLUME` | Volume |
| `SYSTEM_STREAM_VOLUME` | Channel volume |
| `SYSTEM_DND` | Do Not Disturb |
| `SYSTEM_SCREEN_ROTATION` | Screen Rotation |
| `SYSTEM_OPEN_APP` | Open app(s) |
| `SYSTEM_SEND_NOTIFICATION` | Notification |
| `SYSTEM_BLOCK_NOTIFICATION` | Block notifications |
| `SYSTEM_CLEAR_APP_NOTIFICATIONS` | Clear app notifications |
| `SYSTEM_WIFI` | Wi-Fi |
| `SYSTEM_BLUETOOTH` | Bluetooth |
| `SYSTEM_FLASHLIGHT` | Flashlight |
| `SYSTEM_AIRPLANE_MODE` | Airplane mode |
| `SYSTEM_MEDIA_PLAY_PAUSE` | Media: play/pause |
| `SYSTEM_MEDIA_NEXT` | Media: next |
| `SYSTEM_MEDIA_PREVIOUS` | Media: previous |
| `SYSTEM_OPEN_URL` | Open URL |
| `SYSTEM_CLEAR_NOTIFICATIONS` | Clear notifications |
| `SYSTEM_EXPAND_STATUS_BAR` | Expand status bar |
| `SYSTEM_COLLAPSE_STATUS_BAR` | Collapse status bar |
| `SYSTEM_SCREEN_TIMEOUT` | Screen timeout |
| `SYSTEM_STAY_AWAKE` | Stay awake |
| `SYSTEM_AUTO_BRIGHTNESS` | Auto brightness |
| `SYSTEM_RINGER_MODE` | Ringer mode |
| `SYSTEM_MOBILE_DATA` | Mobile data |
| `SYSTEM_NETWORK_MODE` | Network mode |
| `SYSTEM_PRIVATE_DNS` | Private DNS |
| `SYSTEM_HOTSPOT` | Hotspot |
| `SYSTEM_NFC` | NFC |
| `SYSTEM_POWER_SAVER` | Battery saver |
| `SYSTEM_ANIMATIONS` | Animations |
| `SYSTEM_LOCK_SCREEN` | Lock screen |
| `SYSTEM_SET_ALARM` | Set alarm |
| `SYSTEM_SET_TIMER` | Start timer |
| `SYSTEM_DARK_MODE` | Dark mode |
| `SYSTEM_OPEN_RECENTS` | Recent apps |
| `SYSTEM_GO_HOME` | Go home |
| `APPLICATION_OPEN_APP_SETTINGS` | App settings |
| `SYSTEM_RING_VOLUME` | Ring volume |
| `SYSTEM_SET_RINGTONE` | Set ringtone |
| `SYSTEM_LOCATION` | Location (GPS) |
| `SYSTEM_UPDATE_GOOGLE_PLAY_APPS` | Google Play app update check |
| `SYSTEM_OPEN_PLAY_UPDATES` | Play Store updates |
| `SYSTEM_OPEN_DEVICE_STORE` | Device app store |
| `SYSTEM_SEND_SMS` | Send SMS |
| `SYSTEM_SEND_REMINDER` | Reminder |
| `SYSTEM_OPEN_SETTINGS` | Open settings |
| `SYSTEM_WAIT` | Wait / Delay |
| `BATTERY_ALERTS` | Battery alert |
| `BATTERY_CHARGING_NOTIFICATIONS` | Charging alert |
| `APPLICATION_LAUNCH_APP` | Launch app |
| `APPLICATION_CLOSE_APP` | Close app |
| `ADVANCED_SHIZUKU` | Shizuku command |
| `ADVANCED_ROOT` | Root command |
| `SYSTEM_HTTP_REQUEST` | HTTP request |
| `PLUGIN_FIRE` | Plugin action |
| `SYSTEM_VIBRATE` | Vibrate |
| `SYSTEM_WAKE_SCREEN` | Wake screen |
| `SYSTEM_CLIPBOARD_SET` | Set clipboard |
| `SYSTEM_MEDIA_STOP` | Stop media |
| `SYSTEM_OPEN_NOTIFICATIONS` | Notification shade |
| `SYSTEM_OPEN_QUICK_SETTINGS` | Quick settings |
| `SYSTEM_SET_SETTING` | Set setting |
| `SYSTEM_SCREENSHOT` | Screenshot |
| `SYSTEM_INPUT_TEXT` | Type text |
| `SYSTEM_KEY_EVENT` | Key event |
| `SYSTEM_INPUT_TAP` | Tap |
| `SYSTEM_INPUT_SWIPE` | Swipe |
| `SYSTEM_COLOR_INVERSION` | Color inversion |
| `SYSTEM_GRAYSCALE` | Grayscale |
| `SYSTEM_EXTRA_DIM` | Extra dim |
| `SYSTEM_NIGHT_LIGHT` | Night light |
| `SYSTEM_HAPTIC_FEEDBACK` | Haptic feedback |
| `SYSTEM_SOUND_EFFECTS` | Touch sounds |
| `SYSTEM_FORCE_STOP_APP` | Force stop app |
| `SYSTEM_CLEAR_APP_DATA` | Clear app data |
| `SYSTEM_LOCATION_MODE` | Location mode |
| `SYSTEM_DATA_SAVER` | Data saver |
| `SYSTEM_FONT_SCALE` | Font scale |
| `SYSTEM_DISPLAY_DENSITY` | Display density |
| `SYSTEM_SCREENSAVER` | Screensaver |
| `SYSTEM_BATTERY_SAVER_THRESHOLD` | Battery saver threshold |
| `SYSTEM_CHARGING_LIMIT` | Charging limit |
| `SYSTEM_CHARGING_FEEDBACK` | Charging sound and vibration |
| `SYSTEM_ALWAYS_ON_DISPLAY` | Always-on display |
| `SYSTEM_SHOW_TAPS` | Show taps |
| `SYSTEM_POINTER_LOCATION` | Pointer location |
| `SYSTEM_ADAPTIVE_BATTERY` | Adaptive battery |
| `SYSTEM_WIFI_SLEEP_POLICY` | Wi-Fi sleep policy |
| `SYSTEM_BLUETOOTH_DISCOVERABILITY` | Bluetooth discoverability |
| `SYSTEM_AUTO_TIME` | Automatic date & time |
| `SYSTEM_AUTO_TIMEZONE` | Automatic time zone |
| `SYSTEM_HAPTIC_INTENSITY` | Haptic intensity |
| `SYSTEM_CAMERA_SHUTTER_SOUND` | Camera shutter sound |
| `SYSTEM_WIFI_SCANNING` | Always-on Wi-Fi scanning |
| `SYSTEM_OPEN_WIFI_SETTINGS` | Open Wi-Fi settings |
| `SYSTEM_OPEN_BLUETOOTH_SETTINGS` | Open Bluetooth settings |
| `SYSTEM_OPEN_LOCATION_SETTINGS` | Open location settings |
| `SYSTEM_OPEN_DATA_USAGE_SETTINGS` | Open data usage settings |
| `SYSTEM_OPEN_BATTERY_SETTINGS` | Open battery settings |
| `SYSTEM_OPEN_DISPLAY_SETTINGS` | Open display settings |
| `SYSTEM_OPEN_SOUND_SETTINGS` | Open sound settings |
| `SYSTEM_OPEN_STORAGE_SETTINGS` | Open storage settings |
| `SYSTEM_OPEN_SECURITY_SETTINGS` | Open security settings |
| `SYSTEM_OPEN_ACCESSIBILITY_SETTINGS` | Open accessibility settings |
| `SYSTEM_OPEN_APP_SETTINGS_LIST` | Open app settings list |
| `SYSTEM_OPEN_ABOUT_PHONE` | Open about phone |
| `SYSTEM_MEDIA_FAST_FORWARD` | Media fast forward |
| `SYSTEM_MEDIA_REWIND` | Media rewind |
| `SYSTEM_DIAL_NUMBER` | Dial number |
| `SYSTEM_OPEN_CAMERA` | Open camera |
| `SYSTEM_OPEN_PLAY_STORE_APP` | Open Play Store |
| `SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS` | System update |
| `SYSTEM_MEDIA_PLAY_FROM_SEARCH` | Play music search |
| `SYSTEM_REBOOT` | Reboot device |
| `SYSTEM_SHUTDOWN` | Shut down device |
| `SYSTEM_RESTART_SYSTEM_UI` | Restart System UI |
| `SYSTEM_TOAST` | Show toast |
| `SYSTEM_ALERT` | Show alert |
| `SYSTEM_VIBRATE_PATTERN` | Vibrate pattern |
| `SYSTEM_PASTE` | Paste text |
| `SYSTEM_OPEN_APP_DRAWER` | Open app drawer |
| `SYSTEM_TOGGLE_PIP` | Picture-in-picture |
| `SYSTEM_WIFI_CONNECT` | Connect to Wi-Fi |
| `SYSTEM_WIFI_FORGET` | Forget Wi-Fi |
| `SYSTEM_DATA_ROAMING` | Data roaming |
| `SYSTEM_SCREENSAVER_TIMEOUT` | Screensaver timeout |
| `SYSTEM_POINTER_SPEED` | Pointer speed |
| `SYSTEM_INSTALL_APK` | Install APK |
| `SYSTEM_UNINSTALL_APP` | Uninstall app |
| `SYSTEM_DISABLE_APP` | Disable app |
| `SYSTEM_ENABLE_APP` | Enable app |
| `SYSTEM_SET_NOTIFICATION_TONE` | Notification tone |
| `SYSTEM_CALL_VIBRATION` | Call vibration |
| `SYSTEM_OPEN_NETWORK_SETTINGS` | Network settings |
| `SYSTEM_OPEN_NFC_SETTINGS` | NFC settings |
| `SYSTEM_OPEN_DATA_SAVER_SETTINGS` | Data saver settings |
| `SYSTEM_OPEN_DEVELOPER_SETTINGS` | Developer options |
| `SYSTEM_OPEN_MAPS` | Open in Maps |
| `SYSTEM_SOFT_RESTART` | Soft restart |
| `SYSTEM_STATUS_BAR_TOGGLE` | Status bar |
| `SYSTEM_OPEN_CONTACTS` | Open contacts |
| `SYSTEM_SEND_EMAIL` | Send email |
| `SYSTEM_OPEN_NOTIFICATION_SETTINGS` | Notification settings |
| `SYSTEM_OPEN_PRIVACY_SETTINGS` | Privacy settings |
| `SYSTEM_OPEN_CAST_SETTINGS` | Cast settings |
| `SYSTEM_OPEN_INPUT_METHOD_SETTINGS` | Keyboard settings |
| `SYSTEM_OPEN_DEFAULT_APPS_SETTINGS` | Default apps |
| `SYSTEM_OPEN_VPN_SETTINGS` | VPN settings |
| `SYSTEM_OPEN_DATE_SETTINGS` | Date & time |
| `SYSTEM_OPEN_PRINT_SETTINGS` | Print settings |
| `SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS` | Device admin apps |
| `SYSTEM_OPEN_USAGE_ACCESS_SETTINGS` | Usage access |
| `SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS` | Airplane mode settings |
| `SYSTEM_BLUETOOTH_SCAN` | Bluetooth scan |
| `SYSTEM_WIFI_SCAN_NOW` | Scan for Wi-Fi |
| `SYSTEM_SET_TIMEZONE` | Set timezone |
| `CALL_BLOCK` | Block call |
| `CALL_SILENCE` | Silence call |
| `ROM_CUSTOM_SETTING` | Custom ROM setting |
| `ROM_QS_TILES` | QS Tiles |
| `ROM_STATUS_BAR` | Status bar |
| `ROM_LOCKSCREEN` | Lockscreen |
| `ROM_NAVIGATION` | Navigation |
| `ROM_THEME` | Theme & Monet |
| `ROM_AMBIENT_AOD` | Ambient & AOD |
| `ROM_NOTIFICATIONS` | Heads-up |
| `ROM_BATCH` | Batch custom settings |
| `DATA_TEXT` | Text transform |
| `DATA_ENCODING` | Text encoding |
| `DATA_HASH` | Cryptographic hash |
| `DATA_RANDOM` | Random value |
| `DATA_MATH` | Decimal arithmetic |
| `DATA_DATE_TIME` | Date and time |
| `DATA_JSON` | JSON transform |
| `DATA_ARRAY` | Array transform |
