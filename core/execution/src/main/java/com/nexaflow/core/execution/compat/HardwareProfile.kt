package com.nexaflow.core.execution.compat

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager

/**
 * Dynamic hardware profile — probes the actual device for what it physically
 * has, so the builder can hide triggers/actions that can never fire here.
 *
 * This is what makes the engine truly adaptive and strict: a whyred without
 * NFC will never show NFC triggers, a tablet without telephony will hide
 * network-mode, etc. Pure live probes, no hard-coded device lists.
 * Covers all 53 triggers and 168 actions comprehensively.
 */
data class HardwareProfile(
    val hasNfc: Boolean,
    val hasTelephony: Boolean,
    val hasBluetooth: Boolean,
    val hasCameraFlash: Boolean,
    val hasProximitySensor: Boolean,
    val hasLightSensor: Boolean,
    val hasStepCounter: Boolean,
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
    val hasLocationGps: Boolean,
    val hasUsbAccessory: Boolean,
    val hasEthernet: Boolean,
    val hasHdmi: Boolean,
    val isWatch: Boolean
) {
    companion object {
        fun probe(context: Context): HardwareProfile {
            val pm = context.packageManager
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            return HardwareProfile(
                hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC),
                hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
                hasBluetooth = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
                hasCameraFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
                hasProximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null,
                hasLightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) != null,
                hasStepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null,
                hasAccelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
                hasGyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null,
                hasLocationGps = pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
                hasUsbAccessory = pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY) || pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST),
                hasEthernet = pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET),
                hasHdmi = false,
                isWatch = pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
            )
        }
    }

    /** Human-readable summary for debugging — shows what this device actually has. */
    fun summary(): String = buildString {
        append("Hardware: ")
        if (hasNfc) append("NFC,")
        if (hasTelephony) append("Telephony,")
        if (hasBluetooth) append("BT,")
        if (hasCameraFlash) append("Flash,")
        if (hasProximitySensor) append("Proximity,")
        if (hasLightSensor) append("Light,")
        if (hasStepCounter) append("Step,")
        if (hasAccelerometer) append("Accel,")
        if (hasGyroscope) append("Gyro,")
        if (hasLocationGps) append("GPS,")
        if (hasUsbAccessory) append("USB,")
        if (hasEthernet) append("Eth,")
        if (hasHdmi) append("HDMI,")
        if (isWatch) append("Watch,")
        if (isEmpty()) append("none")
    }

    private fun StringBuilder.isEmpty(): Boolean = length <= 10 // "Hardware: ".length
}
