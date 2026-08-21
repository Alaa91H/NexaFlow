package com.nexaflow.core.pluginsdk

/**
 * Explicit user acknowledgement required for plugins that can execute arbitrary
 * shell/ADB commands or privileged system/root control. The allow-list is
 * intentionally narrow and package based:
 * protocol discovery still decides whether the app is compatible on a device.
 */
object PluginRiskPolicy {
    const val HIGH_RISK_APPROVAL_KEY = "pluginHighRiskApproval"
    const val APPROVAL_VALUE = "approved"

    private val highRiskPackages = setOf(
        "com.termux.tasker",
        "com.ADBPlugin",
        "com.joaomgcd.autotoolsroot",
        "eu.chainfire.lumen",
        "mobi.omegacentauri.red",
        "com.oasisfeng.greenify",
        "com.catchingnow.icebox"
    )

    fun requiresHighRiskApproval(packageName: String): Boolean = packageName in highRiskPackages
}
