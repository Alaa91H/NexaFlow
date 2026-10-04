package com.nexaflow.core.engine

/** Matches a Bluetooth event against an optional name and/or MAC address. */
internal fun bluetoothDeviceMatches(
    config: Map<String, String>,
    address: String,
    deviceName: String
): Boolean {
    val configuredName = config["deviceName"].orEmpty().trim()
    val storedAddress = config["deviceAddress"].orEmpty().trim()
    val anyName = configuredName.isEmpty() || configuredName == "__ANY__" ||
        configuredName == "*" || configuredName.equals("ANY", ignoreCase = true)
    if (anyName && storedAddress.isEmpty()) return true
    return (configuredName.isNotEmpty() && !anyName &&
        deviceName.equals(configuredName, ignoreCase = true)) ||
        (storedAddress.isNotEmpty() && storedAddress.equals(address, ignoreCase = true))
}
