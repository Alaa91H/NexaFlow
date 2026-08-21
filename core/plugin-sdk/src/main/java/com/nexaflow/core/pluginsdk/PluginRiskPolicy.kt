package com.nexaflow.core.pluginsdk

/**
 * Explicit user acknowledgement required for plugins that can execute arbitrary
 * shell/ADB commands. The allow-list is intentionally narrow and package based:
 * protocol discovery still decides whether the app is compatible on a device.
 */
object PluginRiskPolicy {
    const val HIGH_RISK_APPROVAL_KEY = "pluginHighRiskApproval"
    const val APPROVAL_VALUE = "approved"

    private val highRiskPackages = setOf(
        "com.termux.tasker",
        "com.ADBPlugin"
    )

    fun requiresHighRiskApproval(packageName: String): Boolean = packageName in highRiskPackages
}
