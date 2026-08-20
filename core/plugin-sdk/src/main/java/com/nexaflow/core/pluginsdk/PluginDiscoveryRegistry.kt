package com.nexaflow.core.pluginsdk

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Public Tasker extension surface used for discovery only. Event delivery stays
 * disabled until the dedicated EventBus adapter verifies the extension's
 * correlation/message-id requirements.
 */
object TaskerExtensionContract {
    const val ACTION_EDIT_EVENT = "net.dinglisch.android.tasker.ACTION_EDIT_EVENT"
}

/** Immutable, bounded result of one explicit discovery refresh. */
data class PluginDiscoverySnapshot(
    val descriptors: List<PluginDescriptor>,
    val refreshedAtMs: Long,
    val truncated: Boolean = false
)

/**
 * Discovers declared Locale/Tasker components through Android's public package
 * manager APIs. It does not load plug-in code, execute a plug-in, or scan on a
 * timer. Callers invalidate on package lifecycle changes and refresh explicitly
 * when a UI needs new information.
 */
class PluginDiscoveryRegistry(
    context: Context,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var cached = PluginDiscoverySnapshot(emptyList(), refreshedAtMs = 0L)

    init {
        require(maxEntries in 1..MAX_ENTRIES) { "maxEntries must be in 1..$MAX_ENTRIES" }
    }

    /** Returns the last complete immutable scan without performing package I/O. */
    fun snapshot(): PluginDiscoverySnapshot = cached

    /** Invalidates cache on package install/update/remove; discovery remains demand-driven. */
    fun invalidate() {
        cached = PluginDiscoverySnapshot(emptyList(), refreshedAtMs = 0L)
    }

    /**
     * Performs one bounded scan and publishes the full result atomically. Each
     * type is classified separately because the Locale protocol requires a
     * matching edit activity and exactly one matching receiver per package.
     */
    suspend fun refresh(): PluginDiscoverySnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pm = appContext.packageManager
            val setting = discoverType(
                pm = pm,
                type = PluginType.SETTING,
                protocol = PluginProtocol.LOCALE_BASE,
                editAction = LocaleContract.ACTION_EDIT_SETTING,
                receiverAction = LocaleContract.ACTION_FIRE_SETTING
            )
            val condition = discoverType(
                pm = pm,
                type = PluginType.CONDITION,
                protocol = PluginProtocol.LOCALE_BASE,
                editAction = LocaleContract.ACTION_EDIT_CONDITION,
                receiverAction = LocaleContract.ACTION_QUERY_CONDITION
            )
            // Tasker event plug-ins use the Locale query receiver but require
            // extra event correlation behaviour. Until phase 5 registers the
            // event adapter, these entries remain PARTIALLY_COMPATIBLE.
            val event = discoverType(
                pm = pm,
                type = PluginType.EVENT,
                protocol = PluginProtocol.TASKER_EXTENSION,
                editAction = TaskerExtensionContract.ACTION_EDIT_EVENT,
                receiverAction = LocaleContract.ACTION_QUERY_CONDITION,
                forcePartialCompatibility = true
            )
            val all = (setting + condition + event)
                .distinctBy { it.id }
                .sortedWith(compareBy<PluginDescriptor> { it.displayName.lowercase() }.thenBy { it.id })
            val next = PluginDiscoverySnapshot(
                descriptors = all.take(maxEntries),
                refreshedAtMs = nowMs(),
                truncated = all.size > maxEntries
            )
            cached = next
            next
        }
    }

    private fun discoverType(
        pm: PackageManager,
        type: PluginType,
        protocol: PluginProtocol,
        editAction: String,
        receiverAction: String,
        forcePartialCompatibility: Boolean = false
    ): List<PluginDescriptor> {
        val edits: Map<String, List<DiscoveredComponent>> =
            queryActivities(pm, editAction).groupBy { it.ref.packageName }
        val receivers: Map<String, List<DiscoveredComponent>> =
            queryReceivers(pm, receiverAction).groupBy { it.ref.packageName }
        return (edits.keys + receivers.keys).sorted().flatMap { packageName ->
            val packageEdits = edits[packageName].orEmpty()
            val packageReceivers = receivers[packageName].orEmpty()
            when {
                packageEdits.isEmpty() -> packageReceivers.map { receiver ->
                    descriptor(
                        type = type,
                        protocol = protocol,
                        edit = null,
                        receiver = receiver,
                        compatibility = PluginCompatibilityStatus.MISSING_EDIT_ACTIVITY,
                        forcePartialCompatibility = forcePartialCompatibility
                    )
                }
                packageReceivers.isEmpty() -> packageEdits.map { edit ->
                    descriptor(
                        type = type,
                        protocol = protocol,
                        edit = edit,
                        receiver = null,
                        compatibility = PluginCompatibilityStatus.MISSING_RECEIVER,
                        forcePartialCompatibility = forcePartialCompatibility
                    )
                }
                packageReceivers.size != 1 -> packageEdits.map { edit ->
                    descriptor(
                        type = type,
                        protocol = protocol,
                        edit = edit,
                        receiver = null,
                        compatibility = PluginCompatibilityStatus.AMBIGUOUS_RECEIVER,
                        forcePartialCompatibility = forcePartialCompatibility
                    )
                }
                else -> packageEdits.map { edit ->
                    val receiver = packageReceivers.single()
                    descriptor(
                        type = type,
                        protocol = protocol,
                        edit = edit,
                        receiver = receiver,
                        compatibility = compatibilityFor(edit, receiver, forcePartialCompatibility),
                        forcePartialCompatibility = forcePartialCompatibility
                    )
                }
            }
        }
    }

    private fun queryActivities(pm: PackageManager, action: String): List<DiscoveredComponent> =
        runCatching {
            pm.queryIntentActivities(Intent(action), PACKAGE_QUERY_FLAGS)
                .mapNotNull { it.activityInfo?.let(::toComponent) }
        }.getOrDefault(emptyList())

    private fun queryReceivers(pm: PackageManager, action: String): List<DiscoveredComponent> =
        runCatching {
            pm.queryBroadcastReceivers(Intent(action), PACKAGE_QUERY_FLAGS)
                .mapNotNull { it.activityInfo?.let(::toComponent) }
        }.getOrDefault(emptyList())

    private fun toComponent(info: ActivityInfo): DiscoveredComponent {
        val packageName = info.packageName
        val className = normalizeClassName(packageName, info.name)
        val appInfo = info.applicationInfo
        val isApplicationEnabled = appInfo?.enabled ?: false
        val isInternalStorage = appInfo != null && appInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE == 0
        val canSendIntent = info.permission.isNullOrBlank() ||
            appContext.checkPermission(info.permission, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED
        return DiscoveredComponent(
            ref = PluginComponentRef(packageName, className),
            displayName = runCatching { info.loadLabel(appContext.packageManager).toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: packageName,
            versionName = packageVersion(packageName),
            applicationEnabled = isApplicationEnabled,
            componentEnabled = info.enabled,
            exported = info.exported,
            canSendIntent = canSendIntent,
            internalStorage = isInternalStorage
        )
    }

    private fun packageVersion(packageName: String): String? = runCatching {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()

    private fun compatibilityFor(
        edit: DiscoveredComponent,
        receiver: DiscoveredComponent,
        forcePartialCompatibility: Boolean
    ): PluginCompatibilityStatus = when {
        !edit.applicationEnabled || !receiver.applicationEnabled ||
            !edit.componentEnabled || !receiver.componentEnabled -> PluginCompatibilityStatus.DISABLED
        !edit.exported || !receiver.exported || !edit.canSendIntent || !receiver.canSendIntent ||
            !edit.internalStorage || !receiver.internalStorage -> PluginCompatibilityStatus.PERMISSION_DENIED
        forcePartialCompatibility -> PluginCompatibilityStatus.PARTIALLY_COMPATIBLE
        else -> PluginCompatibilityStatus.COMPATIBLE
    }

    private fun descriptor(
        type: PluginType,
        protocol: PluginProtocol,
        edit: DiscoveredComponent?,
        receiver: DiscoveredComponent?,
        compatibility: PluginCompatibilityStatus,
        forcePartialCompatibility: Boolean
    ): PluginDescriptor {
        val primary = edit ?: receiver ?: error("Plugin descriptor requires at least one component")
        return PluginDescriptor(
            id = "${protocol.name.lowercase()}:${primary.ref.packageName}:${type.name.lowercase()}:${primary.ref.className}",
            packageName = primary.ref.packageName,
            versionName = primary.versionName,
            type = type,
            protocol = protocol,
            editActivity = edit?.ref,
            receiver = receiver?.ref,
            displayName = edit?.displayName ?: receiver?.displayName ?: primary.ref.packageName,
            requiredChecks = REQUIRED_COMPONENT_CHECKS,
            supportsConfiguration = edit != null,
            // Extensions must be discovered and later negotiated by their adapter;
            // never advertise outputs or event payloads based on package presence.
            supportsOutputVariables = false,
            supportsEventPayload = false,
            trustLevel = PluginTrustLevel.UNTRUSTED,
            compatibility = if (forcePartialCompatibility && compatibility == PluginCompatibilityStatus.COMPATIBLE) {
                PluginCompatibilityStatus.PARTIALLY_COMPATIBLE
            } else {
                compatibility
            }
        )
    }

    private fun normalizeClassName(packageName: String, rawName: String): String = when {
        rawName.startsWith('.') -> packageName + rawName
        '.' !in rawName -> "$packageName.$rawName"
        else -> rawName
    }

    private data class DiscoveredComponent(
        val ref: PluginComponentRef,
        val displayName: String,
        val versionName: String?,
        val applicationEnabled: Boolean,
        val componentEnabled: Boolean,
        val exported: Boolean,
        val canSendIntent: Boolean,
        val internalStorage: Boolean
    )

    private companion object {
        private const val DEFAULT_MAX_ENTRIES = 256
        private const val MAX_ENTRIES = 1_024
        @Suppress("DEPRECATION")
        private const val PACKAGE_QUERY_FLAGS = PackageManager.MATCH_DISABLED_COMPONENTS
        private val REQUIRED_COMPONENT_CHECKS = setOf(
            PluginPermissionRequirement.APPLICATION_ENABLED,
            PluginPermissionRequirement.EDIT_ACTIVITY_EXPORTED,
            PluginPermissionRequirement.RECEIVER_EXPORTED,
            PluginPermissionRequirement.EDIT_ACTIVITY_ENABLED,
            PluginPermissionRequirement.RECEIVER_ENABLED,
            PluginPermissionRequirement.HOST_CAN_SEND_INTENT,
            PluginPermissionRequirement.INTERNAL_STORAGE_INSTALL
        )
    }
}
