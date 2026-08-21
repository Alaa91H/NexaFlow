package com.nexaflow.feature.settings

import androidx.annotation.StringRes
import com.nexaflow.core.pluginsdk.PluginCompatibilityStatus
import com.nexaflow.core.pluginsdk.PluginDescriptor
import com.nexaflow.core.pluginsdk.PluginType
import com.nexaflow.domain.models.PluginInfo

/** A reviewed catalog entry; discovery still decides whether the device can use it. */
data class PluginCatalogDefinition(
    val packageName: String,
    val displayName: String,
    val capabilities: List<PluginCatalogCapability>,
    val section: PluginCatalogSection = PluginCatalogSection.RECOMMENDED,
    val risk: PluginCatalogRisk = PluginCatalogRisk.STANDARD
)

enum class PluginCatalogSection {
    RECOMMENDED,
    ADVANCED
}

enum class PluginCatalogRisk {
    STANDARD,
    HIGH
}

enum class PluginCatalogCapability(@StringRes val labelRes: Int) {
    NOTIFICATIONS(R.string.plugin_capability_notifications),
    UI_INTERACTION(R.string.plugin_capability_ui_interaction),
    AUTOMATION_TOOLS(R.string.plugin_capability_automation_tools),
    SHARING(R.string.plugin_capability_sharing),
    VOICE(R.string.plugin_capability_voice),
    LOCATION(R.string.plugin_capability_location),
    MULTI_DEVICE(R.string.plugin_capability_multi_device),
    NETWORK(R.string.plugin_capability_network),
    WEB_REQUESTS(R.string.plugin_capability_web_requests),
    TASKS(R.string.plugin_capability_tasks),
    SLEEP(R.string.plugin_capability_sleep),
    WEARABLES(R.string.plugin_capability_wearables),
    CALENDAR(R.string.plugin_capability_calendar),
    MEDIA(R.string.plugin_capability_media),
    PUSH(R.string.plugin_capability_push),
    BLUETOOTH(R.string.plugin_capability_bluetooth),
    QR_CODES(R.string.plugin_capability_qr_codes),
    CAMERAS(R.string.plugin_capability_cameras),
    SHELL_COMMANDS(R.string.plugin_capability_shell_commands)
}

/** One card rendered by the catalog. Unknown discovered plugins are retained too. */
data class PluginCatalogEntry(
    val packageName: String,
    val displayName: String,
    val definition: PluginCatalogDefinition? = null,
    val descriptors: List<PluginDescriptor> = emptyList(),
    val installed: Boolean,
    val appEnabled: Boolean = true
) {
    val settingDescriptor: PluginDescriptor?
        get() = descriptors.firstOrNull { it.type == PluginType.SETTING }

    val compatibility: PluginCompatibilityStatus?
        get() = settingDescriptor?.compatibility ?: descriptors.firstOrNull()?.compatibility

    val isAdvanced: Boolean
        get() = definition?.section == PluginCatalogSection.ADVANCED

    val isHighRisk: Boolean
        get() = definition?.risk == PluginCatalogRisk.HIGH

    val testablePlugin: PluginInfo?
        get() {
            val descriptor = settingDescriptor
                ?.takeIf { it.compatibility == PluginCompatibilityStatus.COMPATIBLE }
                ?: return null
            val receiver = descriptor.receiver ?: return null
            return PluginInfo(
                packageName = descriptor.packageName,
                receiverClass = receiver.className,
                label = descriptor.displayName,
                editActivityClass = descriptor.editActivity?.className.orEmpty()
            )
        }
}

data class PluginCatalogUiState(
    val installed: List<PluginCatalogEntry> = emptyList(),
    val recommendedAvailable: List<PluginCatalogEntry> = emptyList(),
    val advancedAvailable: List<PluginCatalogEntry> = emptyList()
)

/**
 * A local, reviewable catalog rather than a remote feed.
 *
 * The catalog does not assert plug-in compatibility: Android discovery validates
 * the actual component pair and permissions on each device. Advanced entries are
 * visibly isolated because their configuration can execute shell/ADB commands.
 */
object PluginCatalog {
    val recommended: List<PluginCatalogDefinition> = listOf(
        PluginCatalogDefinition("com.joaomgcd.autonotification", "AutoNotification", listOf(PluginCatalogCapability.NOTIFICATIONS)),
        PluginCatalogDefinition("com.joaomgcd.autoinput", "AutoInput", listOf(PluginCatalogCapability.UI_INTERACTION)),
        PluginCatalogDefinition("com.joaomgcd.autotools", "AutoTools", listOf(PluginCatalogCapability.AUTOMATION_TOOLS)),
        PluginCatalogDefinition("com.joaomgcd.autoshare", "AutoShare", listOf(PluginCatalogCapability.SHARING)),
        PluginCatalogDefinition("com.joaomgcd.autovoice", "AutoVoice", listOf(PluginCatalogCapability.VOICE)),
        PluginCatalogDefinition("com.joaomgcd.autolocation", "AutoLocation", listOf(PluginCatalogCapability.LOCATION)),
        PluginCatalogDefinition("com.joaomgcd.join", "Join", listOf(PluginCatalogCapability.MULTI_DEVICE)),
        PluginCatalogDefinition("com.joaomgcd.autoremote", "AutoRemote", listOf(PluginCatalogCapability.MULTI_DEVICE, PluginCatalogCapability.NETWORK)),
        PluginCatalogDefinition("ch.rmy.android.http_shortcuts", "HTTP Request Shortcuts", listOf(PluginCatalogCapability.WEB_REQUESTS, PluginCatalogCapability.NETWORK)),
        PluginCatalogDefinition("org.tasks", "Tasks.org", listOf(PluginCatalogCapability.TASKS)),
        PluginCatalogDefinition("com.urbandroid.sleep", "Sleep as Android", listOf(PluginCatalogCapability.SLEEP)),
        PluginCatalogDefinition("com.joaomgcd.autowear", "AutoWear", listOf(PluginCatalogCapability.WEARABLES)),
        PluginCatalogDefinition("com.joaomgcd.autocalendar", "AutoCalendar", listOf(PluginCatalogCapability.CALENDAR)),
        PluginCatalogDefinition("com.joaomgcd.autobluetooth", "AutoBluetooth", listOf(PluginCatalogCapability.BLUETOOTH)),
        PluginCatalogDefinition("org.leetzone.android.yatsewidgetfree", "Yatse", listOf(PluginCatalogCapability.MEDIA, PluginCatalogCapability.NETWORK)),
        PluginCatalogDefinition("net.superblock.pushover", "Pushover", listOf(PluginCatalogCapability.PUSH, PluginCatalogCapability.NETWORK)),
        PluginCatalogDefinition("com.joaomgcd.autocast", "AutoCast", listOf(PluginCatalogCapability.MEDIA, PluginCatalogCapability.NETWORK)),
        PluginCatalogDefinition("com.joaomgcd.barcode", "AutoBarcode", listOf(PluginCatalogCapability.QR_CODES)),
        PluginCatalogDefinition("com.bitklog.wolon", "WolOn", listOf(PluginCatalogCapability.NETWORK)),
        PluginCatalogDefinition("com.alexvas.dvr.pro", "tinyCam Monitor PRO", listOf(PluginCatalogCapability.CAMERAS, PluginCatalogCapability.NETWORK)),
        PluginCatalogDefinition(
            packageName = "com.termux.tasker",
            displayName = "Termux:Tasker",
            capabilities = listOf(PluginCatalogCapability.SHELL_COMMANDS),
            section = PluginCatalogSection.ADVANCED,
            risk = PluginCatalogRisk.HIGH
        ),
        PluginCatalogDefinition(
            packageName = "com.ADBPlugin",
            displayName = "ADB Shell [Tasker Plugin]",
            capabilities = listOf(PluginCatalogCapability.SHELL_COMMANDS, PluginCatalogCapability.NETWORK),
            section = PluginCatalogSection.ADVANCED,
            risk = PluginCatalogRisk.HIGH
        )
    )

    fun organize(
        installedPackages: Map<String, Boolean>,
        descriptors: List<PluginDescriptor>
    ): PluginCatalogUiState {
        val descriptorsByPackage = descriptors.groupBy { it.packageName }
        val knownPackages = recommended.mapTo(mutableSetOf()) { it.packageName }
        val installedKnown = recommended
            .filter { it.packageName in installedPackages }
            .map { definition ->
                PluginCatalogEntry(
                    packageName = definition.packageName,
                    displayName = definition.displayName,
                    definition = definition,
                    descriptors = descriptorsByPackage[definition.packageName].orEmpty(),
                    installed = true,
                    appEnabled = installedPackages.getValue(definition.packageName)
                )
            }
        val discoveredExternal = descriptorsByPackage
            .filterKeys { it !in knownPackages }
            .map { (packageName, packageDescriptors) ->
                PluginCatalogEntry(
                    packageName = packageName,
                    displayName = packageDescriptors.first().displayName,
                    descriptors = packageDescriptors,
                    installed = true,
                    appEnabled = packageDescriptors.none { it.compatibility == PluginCompatibilityStatus.DISABLED }
                )
            }
        val available = recommended
            .filter { it.packageName !in installedPackages }
            .map { definition ->
                PluginCatalogEntry(
                    packageName = definition.packageName,
                    displayName = definition.displayName,
                    definition = definition,
                    installed = false
                )
            }
        return PluginCatalogUiState(
            installed = (installedKnown + discoveredExternal).sortedBy { it.displayName.lowercase() },
            recommendedAvailable = available
                .filter { !it.isAdvanced }
                .sortedBy { it.displayName.lowercase() },
            advancedAvailable = available
                .filter { it.isAdvanced }
                .sortedBy { it.displayName.lowercase() }
        )
    }
}
