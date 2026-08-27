package com.nexaflow.core.execution

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import com.nexaflow.core.common.CellularNetworkReader
import com.nexaflow.core.common.DefaultNetworkSnapshot
import com.nexaflow.core.common.DefaultNetworkStateReader
import com.nexaflow.core.common.HotspotStateReader
import com.nexaflow.core.common.NetworkTransportState
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.schedule.TimeTriggerCalculator
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort evaluation of whether a task's trigger condition is currently
 * true, used by the manual "run now" gate: when the condition is satisfied
 * the task's actions run; otherwise the exit behavior ("when the task ends")
 * runs instead, so a manual run never executes actions whose condition is
 * not met.
 *
 * Trigger types that cannot be evaluated deterministically without their
 * live monitors (apps, SMS, NFC scans, clipboard, sensors, webhook, calendar,
 * ...) report "not satisfied". A manual tap must never execute main actions
 * without proof that every configured condition is currently true.
 */
object TriggerStateEvaluator {

    /**
     * True only when every configured trigger is currently and verifiably
     * satisfied. An empty trigger list remains manually runnable.
     */
    // ConnectivityManager reads (ethernet/VPN/connectivity) need
    // ACCESS_NETWORK_STATE, which is a normal permission the app declares in
    // the manifest — lint cannot see the manifest here, so suppress it; every
    // read is also wrapped in runCatching and degrades to "not satisfied".
    fun isSatisfied(context: Context, triggers: List<Trigger>): Boolean =
        triggers.isEmpty() || triggers.all { triggerSatisfied(context, it) }

    /**
     * Manual execution entry point. System and telephony probes run on IO so a
     * "run now" tap never performs a phone-state read on the main thread.
     */
    suspend fun isSatisfiedAsync(context: Context, triggers: List<Trigger>): Boolean =
        evaluateAsync(context, triggers) == ConditionResult.Satisfied

    /**
     * Typed manual-gate evaluation. A manual exit is permitted only after a
     * confirmed false condition; an event-only source, unavailable service, or
     * read whose false result cannot be distinguished from an API failure is
     * intentionally [ConditionResult.Unknown].
     */
    suspend fun evaluateAsync(context: Context, triggers: List<Trigger>): ConditionResult {
        if (triggers.isEmpty()) return ConditionResult.Satisfied
        var unknown = false
        triggers.forEach { trigger ->
            when (val result = withContext(Dispatchers.IO) { evaluateTriggerForManualGate(context, trigger) }) {
                ConditionResult.Satisfied -> Unit
                ConditionResult.Unsatisfied -> return ConditionResult.Unsatisfied
                ConditionResult.Unknown,
                ConditionResult.Unavailable,
                is ConditionResult.Error -> unknown = true
            }
        }
        return if (unknown) ConditionResult.Unknown else ConditionResult.Satisfied
    }

    private fun evaluateTriggerForManualGate(context: Context, trigger: Trigger): ConditionResult {
        if (trigger.type in MANUAL_EVENT_ONLY_TYPES) return ConditionResult.Unknown
        val satisfied = runCatching { triggerSatisfied(context, trigger) }.getOrNull()
            ?: return ConditionResult.Unknown
        if (satisfied) return ConditionResult.Satisfied
        // Most legacy boolean probes intentionally collapse service/permission
        // failures to false. Until each adapter exposes typed reads, only these
        // platform values are treated as a confirmed manual false. This is
        // conservative by design: UNKNOWN skips rather than runs end actions.
        return if (trigger.type in MANUAL_DEFINITIVE_FALSE_TYPES) {
            ConditionResult.Unsatisfied
        } else {
            ConditionResult.Unknown
        }
    }

    // ConnectivityManager reads (ethernet/VPN/connectivity) need
    // ACCESS_NETWORK_STATE, which is a normal permission the app declares in
    // the manifest — lint cannot see the manifest here, so suppress it; every
    // read is also wrapped in runCatching and degrades to "unknown".
    @SuppressLint("MissingPermission")
    fun triggerSatisfied(context: Context, trigger: Trigger): Boolean {
        val c = trigger.config
        return when (trigger.type) {
            TriggerType.NETWORK_MODE ->
                CellularNetworkReader.matchesNetworkMode(
                    c["state"] ?: CellularNetworkReader.GENERATION_4G,
                    CellularNetworkReader.read(context)
                )
            TriggerType.CONNECTIVITY -> connectivitySatisfied(context, c)
            TriggerType.HOTSPOT -> connectivitySatisfied(context, c + ("network" to "HOTSPOT"))
            TriggerType.TIME -> timeTriggerSatisfied(c)
            TriggerType.RINGER_MODE -> ringerModeSatisfied(context, c)
            TriggerType.BATTERY -> batterySatisfied(context, c)
            TriggerType.HEADPHONE -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
                val wantConnected = (c["event"] ?: "CONNECTED") == "CONNECTED"
                audio.isWiredHeadsetConnected() == wantConnected
            }
            TriggerType.CHARGER -> {
                val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
                val wantConnected = (c["event"] ?: "CONNECTED") == "CONNECTED"
                battery.isCharging == wantConnected
            }
            TriggerType.AIRPLANE_MODE -> {
                val on = Settings.Global.getInt(
                    context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0
                ) == 1
                ((c["state"] ?: "ON") == "ON") == on
            }
            TriggerType.DARK_MODE -> {
                val dark = (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                ((c["state"] ?: "ON") == "ON") == dark
            }
            TriggerType.CALL_STATE -> {
                // Incoming/outgoing calls are satisfied while a call is live;
                // ENDED is satisfied once the line is free again.
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager ?: return false
                val busy = telephony.isCallActive(context)
                when (c["event"] ?: "INCOMING") {
                    "ENDED" -> !busy
                    else -> busy
                }
            }
            TriggerType.MEDIA_PLAYING -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
                val playing = audio.isMusicActive
                (c["event"] ?: "STARTED") == "STARTED" == playing
            }
            TriggerType.VOLUME_CHANGED -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
                val stream = when (c["stream"] ?: "MUSIC") {
                    "RING" -> AudioManager.STREAM_RING
                    "ALARM" -> AudioManager.STREAM_ALARM
                    "NOTIFICATION" -> AudioManager.STREAM_NOTIFICATION
                    else -> AudioManager.STREAM_MUSIC
                }
                val level = audio.getStreamVolume(stream)
                val threshold = (c["threshold"] ?: "50").toIntOrNull() ?: 50
                if ((c["direction"] ?: "ABOVE") == "BELOW") level <= threshold else level >= threshold
            }
            // Install/remove events cannot be re-derived statically. They
            // need a live event and therefore cannot authorize a manual run.
            TriggerType.APP_INSTALLED -> false
            TriggerType.POWER_SAVER -> {
                val on = Settings.Global.getInt(
                    context.contentResolver, "low_power", 0
                ) == 1
                ((c["state"] ?: "ON") == "ON") == on
            }
            TriggerType.BLUETOOTH_STATE -> {
                val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
                    ?.adapter ?: return false
                val on = adapter.state == android.bluetooth.BluetoothAdapter.STATE_ON
                ((c["state"] ?: "ON") == "ON") == on
            }
            TriggerType.BRIGHTNESS_LEVEL -> {
                val brightness = Settings.System.getInt(
                    context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128
                )
                val threshold = (c["threshold"] ?: "128").toIntOrNull() ?: 128
                if ((c["direction"] ?: "ABOVE") == "BELOW") brightness <= threshold
                else brightness >= threshold
            }
            TriggerType.STORAGE_LOW -> {
                val thresholdMb = (c["threshold"] ?: "1024").toLongOrNull() ?: 1024L
                val freeMb = runCatching {
                    android.os.StatFs(context.filesDir.path).availableBytes / (1024L * 1024L)
                }.getOrDefault(-1L)
                if (freeMb < 0) return false
                if ((c["direction"] ?: "BELOW") == "ABOVE") freeMb >= thresholdMb
                else freeMb <= thresholdMb
            }
            TriggerType.AUTO_ROTATE -> {
                val on = Settings.System.getInt(
                    context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0
                ) == 1
                ((c["state"] ?: "ON") == "ON") == on
            }
            TriggerType.DATA_SAVER_STATE -> {
                val on = Settings.Global.getInt(
                    context.contentResolver, "data_saver", 0
                ) == 1
                ((c["state"] ?: "ON") == "ON") == on
            }
            TriggerType.DEVICE_LOCKED -> {
                val wantLocked = (c["state"] ?: "LOCKED") == "LOCKED"
                val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val locked = power?.isInteractive != true
                locked == wantLocked
            }
            TriggerType.WIFI_STATE -> {
                val wifi = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val on = wifi?.isWifiEnabled == true
                ((c["state"] ?: "ON") == "ON") == on
            }
            TriggerType.NFC_STATE -> {
                val nfc = android.nfc.NfcAdapter.getDefaultAdapter(context)
                val on = nfc?.isEnabled == true
                ((c["state"] ?: "ON") == "ON") == on
            }
            TriggerType.LOCATION_STATE -> {
                locationStateSatisfied(context, (c["mode"] ?: "HIGH").uppercase())
            }
            TriggerType.SCREEN_ROTATION_STATE -> {
                val wantPortrait = (c["state"] ?: "PORTRAIT") == "PORTRAIT"
                val portrait = (context.resources.configuration.orientation ==
                    Configuration.ORIENTATION_PORTRAIT)
                portrait == wantPortrait
            }
            // ---- v3.28 state triggers ----
            TriggerType.DND_STATE -> {
                val zen = Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
                ((c["state"] ?: "ON") == "ON") == (zen != 0)
            }
            TriggerType.STAY_AWAKE_STATE -> {
                val stay = Settings.Global.getInt(
                    context.contentResolver, "stay_on_while_plugged_in", 0
                )
                ((c["state"] ?: "ON") == "ON") == (stay != 0)
            }
            TriggerType.AUTO_BRIGHTNESS_STATE -> {
                val mode = Settings.System.getInt(
                    context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0
                )
                ((c["state"] ?: "ON") == "ON") ==
                    (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            }
            TriggerType.DATA_ROAMING_STATE -> {
                val roaming = Settings.Global.getInt(
                    context.contentResolver, "data_roaming", 0
                ) != 0
                ((c["state"] ?: "ON") == "ON") == roaming
            }
            TriggerType.WIFI_SIGNAL_STRENGTH -> {
                val rssi = connectedWifiRssi(context) ?: return false
                val level = signalLevel(rssi, 5)
                val threshold = (c["threshold"] ?: "3").toIntOrNull() ?: 3
                if ((c["direction"] ?: "ABOVE") == "BELOW") level <= threshold else level >= threshold
            }
            TriggerType.CELL_SIGNAL_STRENGTH -> {
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                val level = runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        telephony?.signalStrength?.level ?: 0
                    } else 0
                }.getOrDefault(0)
                val threshold = (c["threshold"] ?: "3").toIntOrNull() ?: 3
                if ((c["direction"] ?: "ABOVE") == "BELOW") level <= threshold else level >= threshold
            }
            TriggerType.BATTERY_TEMPERATURE -> {
                val intent = context.registerReceiver(
                    null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                ) ?: return false
                val celsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
                if (celsius < 0) return false
                val threshold = (c["threshold"] ?: "40").toFloatOrNull() ?: 40f
                if ((c["direction"] ?: "ABOVE") == "BELOW") celsius <= threshold else celsius >= threshold
            }
            TriggerType.USB_CONNECTED -> {
                val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                ((c["state"] ?: "ON") == "ON") == (plugged == BatteryManager.BATTERY_PLUGGED_USB)
            }
            TriggerType.HDMI_CONNECTED -> {
                // No public read API exposes the current HDMI state. It can
                // only be authorized by the live broadcast monitor, never by
                // a manual run that cannot verify the condition.
                false
            }
            TriggerType.ETHERNET_CONNECTED -> {
                val has = runCatching {
                    hasActiveTransport(context, android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                }.getOrDefault(false)
                ((c["state"] ?: "ON") == "ON") == has
            }
            TriggerType.VPN_CONNECTED -> {
                val has = runCatching {
                    hasActiveTransport(context, android.net.NetworkCapabilities.TRANSPORT_VPN)
                }.getOrDefault(false)
                ((c["state"] ?: "ON") == "ON") == has
            }
            // One-shot event triggers are only true at the moment their live
            // monitor receives the event. A manual tap cannot synthesize that
            // proof, so it follows the configured end behavior instead.
            TriggerType.TIMEZONE_CHANGED,
            TriggerType.BOOT_COMPLETED,
            TriggerType.NFC_TAG_SCANNED,
            TriggerType.CLIPBOARD_CHANGED,
            TriggerType.SCREEN_TIMEOUT_CHANGED,
            TriggerType.ALARM_SET_CHANGED -> false
            // New trigger types are deliberately fail-closed until a current
            // state evaluator is added; this prevents accidental direct runs.
            else -> false
        }
    }

    /**
     * TIME trigger: a range is active while "now" sits inside it (overnight
     * ranges wrap past midnight); a single time is active within a ±10 minute
     * window around the set time. The repeat schedule must match today too.
     */
    fun timeTriggerSatisfied(
        config: Map<String, String>,
        now: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val repeat = config["repeat"] ?: TimeTriggerCalculator.REPEAT_DAILY
        if (!TimeTriggerCalculator.matchesRepeat(repeat, config, today)) return false
        return if (config["timeMode"] == "RANGE") {
            val start = parseTime(config["rangeStart"]) ?: return false
            val end = parseTime(config["rangeEnd"]) ?: return false
            if (end.isAfter(start)) {
                !now.isBefore(start) && now.isBefore(end)
            } else {
                // Overnight window (e.g. 22:00 -> 06:00).
                !now.isBefore(start) || now.isBefore(end)
            }
        } else {
            val target = parseTime(config["time"]) ?: return false
            val nowMinutes = now.hour * 60 + now.minute
            val targetMinutes = target.hour * 60 + target.minute
            abs(nowMinutes - targetMinutes) <= 10
        }
    }

    private fun parseTime(value: String?): LocalTime? =
        value?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    @SuppressLint("MissingPermission")
    private fun connectivitySatisfied(context: Context, config: Map<String, String>): Boolean {
        val network = config["network"] ?: "WIFI"
        val desired = config["state"] ?: "CONNECTED"
        val snapshot = DefaultNetworkStateReader.read(context)
        val actual = when (network) {
            "NETWORK_MODE" -> CellularNetworkReader.read(context)
            "WIFI" -> defaultTransportValue(
                snapshot,
                android.net.NetworkCapabilities.TRANSPORT_WIFI
            )
            "MOBILE" -> defaultTransportValue(
                snapshot,
                android.net.NetworkCapabilities.TRANSPORT_CELLULAR
            )
            "HOTSPOT" -> HotspotStateReader.currentState(context)
                ?.let { enabled -> if (enabled) "ON" else "OFF" }
            else -> null
        }
        return if (network == "NETWORK_MODE") {
            CellularNetworkReader.matchesNetworkMode(desired, actual)
        } else {
            actual == desired
        }
    }

    private fun defaultTransportValue(
        snapshot: DefaultNetworkSnapshot,
        transport: Int
    ): String? = when (DefaultNetworkStateReader.transportState(snapshot, transport)) {
        NetworkTransportState.CONNECTED -> "CONNECTED"
        NetworkTransportState.DISCONNECTED -> "DISCONNECTED"
        NetworkTransportState.UNKNOWN -> null
    }

    private fun ringerModeSatisfied(context: Context, config: Map<String, String>): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val wanted = config["mode"] ?: "NORMAL"
        val actual = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            else -> "NORMAL"
        }
        return wanted == actual
    }

    private val MANUAL_EVENT_ONLY_TYPES = setOf(
        TriggerType.APP_INSTALLED,
        TriggerType.TIMEZONE_CHANGED,
        TriggerType.BOOT_COMPLETED,
        TriggerType.NFC_TAG_SCANNED,
        TriggerType.CLIPBOARD_CHANGED,
        TriggerType.SCREEN_TIMEOUT_CHANGED,
        TriggerType.ALARM_SET_CHANGED
    )

    private val MANUAL_DEFINITIVE_FALSE_TYPES = setOf(
        TriggerType.TIME,
        TriggerType.DARK_MODE,
        TriggerType.SCREEN_ROTATION_STATE
    )

    private fun batterySatisfied(context: Context, config: Map<String, String>): Boolean {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val threshold = (config["above"] ?: config["below"] ?: "80").toIntOrNull() ?: 80
        val direction = config["direction"] ?: "ABOVE"
        return if (direction == "BELOW") level <= threshold else level >= threshold
    }
}

private fun AudioManager.isWiredHeadsetConnected(): Boolean =
    getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
        device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
    }

@SuppressLint("MissingPermission") // Direct READ_PHONE_STATE guard below; declared by the merged app manifest.
private fun android.telephony.TelephonyManager.isCallActive(context: Context): Boolean {
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
        return false
    }
    val state = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        callStateForSubscription
    } else {
        @Suppress("DEPRECATION")
        callState
    }
    return state != android.telephony.TelephonyManager.CALL_STATE_IDLE
}

private fun locationStateSatisfied(context: Context, wantedMode: String): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        val location = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            ?: return false
        return if (wantedMode == "OFF") !location.isLocationEnabled else location.isLocationEnabled
    }
    @Suppress("DEPRECATION")
    val mode = Settings.Secure.getInt(
        context.contentResolver,
        Settings.Secure.LOCATION_MODE,
        0
    )
    val desired = when (wantedMode) {
        "OFF" -> 0
        "SENSORS" -> 1
        "BATTERY" -> 2
        else -> 3
    }
    return mode == desired
}

private fun connectedWifiRssi(context: Context): Int? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
        return null
    }
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? android.net.ConnectivityManager ?: return null
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        return connectedWifiRssiApi29(context, connectivity)
    }
    @Suppress("DEPRECATION")
    return (context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager)
        ?.connectionInfo
        ?.rssi
}

@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
@SuppressLint("MissingPermission") // Guarded directly by the caller before this API-specific path.
private fun connectedWifiRssiApi29(
    context: Context,
    connectivity: android.net.ConnectivityManager
): Int? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
        return null
    }
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return null
    val info = capabilities.transportInfo as? android.net.wifi.WifiInfo ?: return null
    return info.rssi
}

private fun signalLevel(rssi: Int, maxLevel: Int): Int = when {
    rssi <= -100 -> 0
    rssi >= -55 -> maxLevel - 1
    else -> ((rssi + 100) * (maxLevel - 1) / 45).coerceIn(0, maxLevel - 1)
}

@SuppressLint("MissingPermission") // Direct ACCESS_NETWORK_STATE guard below; declared by the merged app manifest.
private fun hasActiveTransport(context: Context, transport: Int): Boolean {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
        return false
    }
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? android.net.ConnectivityManager ?: return false
    return connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasTransport(transport) == true
}
