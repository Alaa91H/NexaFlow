package com.nexaflow.core.execution.compat

import android.Manifest
import android.os.Build
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.capability.ExecutionRequirement
import com.nexaflow.domain.capability.ExecutionRequirementResolution
import com.nexaflow.domain.capability.ExecutionRequirementResolver
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.capability.PrivilegeSurface
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.security.HttpAccessPolicy

/**
 * UI-neutral permission hint retained for the builder. Execution admission uses
 * [ExecutionRequirement] and may accept an alternative authority even when the
 * preferred user-facing grant is a special-access screen.
 */
enum class WorkflowSpecialPermission {
    WRITE_SETTINGS,
    DND_ACCESS,
    NOTIFICATION_ACCESS,
    ACCESSIBILITY,
    SHIZUKU,
    ROOT,
    ELEVATED,
    EXACT_ALARM
}

data class WorkflowPermissionRequirement(
    val runtimePermissions: List<String> = emptyList(),
    val special: WorkflowSpecialPermission? = null
)

data class WorkflowRequirementEntry(
    val owner: String,
    val requirement: ExecutionRequirement,
    val permissionHint: WorkflowPermissionRequirement = WorkflowPermissionRequirement()
)

data class WorkflowRequirementEntryResolution(
    val owner: String,
    val resolution: ExecutionRequirementResolution
)

data class WorkflowPermissionRepairPlan(
    val runtimePermissions: List<String> = emptyList(),
    val specialPermissions: List<WorkflowSpecialPermission> = emptyList(),
    val blockedOwners: Set<String> = emptySet(),
    val unknownOwners: Set<String> = emptySet()
) {
    val requiresUserAction: Boolean
        get() = runtimePermissions.isNotEmpty() || specialPermissions.isNotEmpty()
}

data class WorkflowRequirementPlan(
    val entries: List<WorkflowRequirementEntry>
) {
    val requirement: ExecutionRequirement =
        entries.map { it.requirement }
            .filterNot { it == ExecutionRequirement.None }
            .let { children ->
                when (children.size) {
                    0 -> ExecutionRequirement.None
                    1 -> children.single()
                    else -> ExecutionRequirement.AllOf(children)
                }
            }

    fun resolveEntries(
        capabilitySnapshot: CapabilitySnapshot,
        privilegeSnapshot: PrivilegeSnapshot
    ): List<WorkflowRequirementEntryResolution> = entries.map { entry ->
        WorkflowRequirementEntryResolution(
            owner = entry.owner,
            resolution = ExecutionRequirementResolver.resolve(
                entry.requirement,
                capabilitySnapshot,
                privilegeSnapshot
            )
        )
    }
}

/**
 * Canonical requirement catalog shared by runtime preflight and permission UI.
 *
 * It separates two concepts that used to be mixed:
 * - permission hint: the most direct user-facing Android grant flow;
 * - execution requirement: every safe alternative that can actually satisfy
 *   the operation (for example WRITE_SETTINGS OR Shizuku OR Root).
 */
object WorkflowRequirementCatalog {

    /**
     * Local JVM tests expose android.jar's SDK_INT as 0 unless a Robolectric
     * sandbox is active. Zero is impossible on-device, so use compileSdk only
     * for that host-only case; production always uses the real device API.
     */
    private fun runtimeSdk(): Int =
        Build.VERSION.SDK_INT.takeIf { it > 0 } ?: HOST_TEST_FALLBACK_SDK

    private const val HOST_TEST_FALLBACK_SDK = 37

    fun plan(
        automation: Automation,
        sdk: Int = runtimeSdk()
    ): WorkflowRequirementPlan {
        val entries = buildList {
            automation.triggers.forEachIndexed { index, trigger ->
                add(
                    WorkflowRequirementEntry(
                        owner = "trigger:$index:${trigger.type.name}",
                        requirement = requirementFor(trigger, sdk),
                        permissionHint = permissionRequirementFor(trigger, sdk)
                    )
                )
            }

            automation.actions.forEachIndexed { index, action ->
                add(
                    WorkflowRequirementEntry(
                        owner = "action:$index:${action.type.name}",
                        requirement = requirementFor(action, sdk),
                        permissionHint = permissionRequirementFor(action, sdk)
                    )
                )
                action.endBehavior
                    ?.takeIf { it.mode == EndMode.SET_VALUE }
                    ?.let { endBehavior ->
                        val endAction = action.withConfig(endBehavior.config)
                        add(
                            WorkflowRequirementEntry(
                                owner = "endBehavior:$index:${action.type.name}",
                                requirement = requirementFor(endAction, sdk),
                                permissionHint = permissionRequirementFor(endAction, sdk)
                            )
                        )
                    }
            }

            automation.exitActions.forEachIndexed { index, action ->
                add(
                    WorkflowRequirementEntry(
                        owner = "exitAction:$index:${action.type.name}",
                        requirement = requirementFor(action, sdk),
                        permissionHint = permissionRequirementFor(action, sdk)
                    )
                )
            }
        }
        return WorkflowRequirementPlan(entries)
    }

    fun repairPlan(
        automation: Automation,
        capabilitySnapshot: CapabilitySnapshot,
        privilegeSnapshot: PrivilegeSnapshot,
        sdk: Int = runtimeSdk()
    ): WorkflowPermissionRepairPlan {
        val plan = plan(automation, sdk)
        val resolutions = plan.resolveEntries(capabilitySnapshot, privilegeSnapshot)
            .associateBy { it.owner }

        val blockedOwners = linkedSetOf<String>()
        val unknownOwners = linkedSetOf<String>()
        val runtimePermissions = linkedSetOf<String>()
        val specialPermissions = linkedSetOf<WorkflowSpecialPermission>()

        plan.entries.forEach { entry ->
            val resolution = resolutions[entry.owner]?.resolution ?: return@forEach
            when (resolution.state) {
                com.nexaflow.domain.capability.ExecutionRequirementState.READY -> Unit
                com.nexaflow.domain.capability.ExecutionRequirementState.UNKNOWN -> {
                    unknownOwners += entry.owner
                }
                com.nexaflow.domain.capability.ExecutionRequirementState.BLOCKED -> {
                    blockedOwners += entry.owner
                    entry.permissionHint.runtimePermissions
                        .filter { permission ->
                            privilegeSnapshot.grantedAndroidPermission(permission) == false
                        }
                        .forEach(runtimePermissions::add)

                    entry.permissionHint.special
                        ?.takeIf { special ->
                            isSpecialGrantMissing(special, privilegeSnapshot)
                        }
                        ?.let(specialPermissions::add)
                }
            }
        }

        return WorkflowPermissionRepairPlan(
            runtimePermissions = runtimePermissions.toList(),
            specialPermissions = specialPermissions.toList(),
            blockedOwners = blockedOwners,
            unknownOwners = unknownOwners
        )
    }

    fun requirementFor(
        action: Action,
        sdk: Int = runtimeSdk()
    ): ExecutionRequirement = allOf(
        capabilityRequirement(CommandRequirementCatalog.requirementFor(action.type)),
        runtimeRequirement(runtimePermissionsFor(action, sdk)),
        executionPrivilegeRequirement(action.type)
    )

    fun requirementFor(
        trigger: Trigger,
        sdk: Int = runtimeSdk()
    ): ExecutionRequirement = allOf(
        capabilityRequirement(CommandRequirementCatalog.requirementFor(trigger.type)),
        runtimeRequirement(runtimePermissionsFor(trigger, sdk)),
        executionPrivilegeRequirement(trigger.type)
    )

    fun permissionRequirementFor(
        action: Action,
        sdk: Int = runtimeSdk()
    ): WorkflowPermissionRequirement = WorkflowPermissionRequirement(
        runtimePermissions = runtimePermissionsFor(action, sdk),
        special = specialPermissionFor(action.type)
    )

    fun permissionRequirementFor(
        trigger: Trigger,
        sdk: Int = runtimeSdk()
    ): WorkflowPermissionRequirement = WorkflowPermissionRequirement(
        runtimePermissions = runtimePermissionsFor(trigger, sdk),
        special = specialPermissionFor(trigger.type)
    )

    fun runtimePermissionsFor(
        action: Action,
        sdk: Int = runtimeSdk()
    ): List<String> = if (action.type == ActionType.SYSTEM_HTTP_REQUEST) {
        HttpAccessPolicy.runtimePermissions(action.config, sdk)
    } else {
        runtimePermissionsFor(action.type, sdk)
    }

    fun runtimePermissionsFor(
        actionType: ActionType,
        sdk: Int = runtimeSdk()
    ): List<String> {
        val explicit = when (actionType) {
            ActionType.SYSTEM_SEND_SMS -> listOf(Manifest.permission.SEND_SMS)
            ActionType.CALL_BLOCK -> listOf(Manifest.permission.ANSWER_PHONE_CALLS)
            ActionType.SYSTEM_FLASHLIGHT -> listOf(Manifest.permission.CAMERA)

            ActionType.SYSTEM_SEND_NOTIFICATION,
            ActionType.SYSTEM_SEND_REMINDER,
            ActionType.BATTERY_ALERTS,
            ActionType.BATTERY_CHARGING_NOTIFICATIONS ->
                if (sdk >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                }

            ActionType.SYSTEM_LOCATION -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

            ActionType.SYSTEM_NETWORK_MODE -> listOf(Manifest.permission.READ_PHONE_STATE)

            // Config-aware local-network permission is evaluated by the Action overload.
            ActionType.SYSTEM_HTTP_REQUEST -> emptyList()
            else -> emptyList()
        }
        val declaredByCommandSpec = CommandCatalog.specFor(actionType)
            ?.permissions
            .orEmpty()
        return (explicit + declaredByCommandSpec).distinct()
    }

    fun runtimePermissionsFor(
        trigger: Trigger,
        sdk: Int = runtimeSdk()
    ): List<String> = when {
        trigger.type == TriggerType.CONNECTIVITY &&
            trigger.config["network"] == "NETWORK_MODE" ->
            listOf(Manifest.permission.READ_PHONE_STATE)

        trigger.type == TriggerType.DEVICE &&
            trigger.config["event"].orEmpty().startsWith("BLUETOOTH") &&
            sdk >= Build.VERSION_CODES.S ->
            listOf(Manifest.permission.BLUETOOTH_CONNECT)

        else -> runtimePermissionsFor(trigger.type, sdk)
    }

    fun runtimePermissionsFor(
        triggerType: TriggerType,
        sdk: Int = runtimeSdk()
    ): List<String> {
        val explicit = when (triggerType) {
            TriggerType.NETWORK_MODE -> listOf(Manifest.permission.READ_PHONE_STATE)
            TriggerType.SMS -> listOf(Manifest.permission.RECEIVE_SMS)
            TriggerType.INCOMING_CALL -> listOf(Manifest.permission.READ_PHONE_STATE)
            TriggerType.LOCATION -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            TriggerType.CALENDAR -> listOf(Manifest.permission.READ_CALENDAR)
            TriggerType.BLUETOOTH_DEVICE ->
                if (sdk >= Build.VERSION_CODES.S) {
                    listOf(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    emptyList()
                }
            TriggerType.SENSOR ->
                if (sdk >= Build.VERSION_CODES.Q) {
                    listOf(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    emptyList()
                }
            else -> emptyList()
        }
        val declaredByCommandSpec = CommandCatalog.specFor(triggerType)
            ?.permissions
            .orEmpty()
        return (explicit + declaredByCommandSpec).distinct()
    }

    fun specialPermissionFor(actionType: ActionType): WorkflowSpecialPermission? = when (actionType) {
        ActionType.SYSTEM_BRIGHTNESS,
        ActionType.SYSTEM_SCREEN_ROTATION,
        ActionType.SYSTEM_SCREEN_TIMEOUT,
        ActionType.SYSTEM_STAY_AWAKE,
        ActionType.SYSTEM_AUTO_BRIGHTNESS,
        ActionType.SYSTEM_DARK_MODE,
        ActionType.SYSTEM_ANIMATIONS,
        ActionType.SYSTEM_SET_RINGTONE -> WorkflowSpecialPermission.WRITE_SETTINGS

        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_RINGER_MODE -> WorkflowSpecialPermission.DND_ACCESS

        ActionType.ADVANCED_SHIZUKU -> WorkflowSpecialPermission.SHIZUKU
        ActionType.ADVANCED_ROOT,
        ActionType.SYSTEM_CHARGING_LIMIT -> WorkflowSpecialPermission.ROOT

        ActionType.APPLICATION_CLOSE_APP,
        ActionType.SYSTEM_FORCE_STOP_APP,
        ActionType.SYSTEM_CLEAR_APP_DATA,
        ActionType.SYSTEM_DISABLE_APP,
        ActionType.SYSTEM_ENABLE_APP,
        ActionType.SYSTEM_LOCATION,
        ActionType.SYSTEM_AIRPLANE_MODE,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_NETWORK_MODE,
        ActionType.SYSTEM_PRIVATE_DNS,
        ActionType.SYSTEM_CHARGING_FEEDBACK,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_LOCK_SCREEN,
        ActionType.SYSTEM_OPEN_RECENTS,
        ActionType.SYSTEM_GO_HOME -> WorkflowSpecialPermission.ELEVATED

        ActionType.SYSTEM_BLOCK_NOTIFICATION,
        ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS -> WorkflowSpecialPermission.NOTIFICATION_ACCESS

        else -> specialPermissionFromCommandSpec(actionType)
    }

    private fun specialPermissionFromCommandSpec(
        actionType: ActionType
    ): WorkflowSpecialPermission? {
        val spec = CommandCatalog.specFor(actionType) ?: return null
        return when (spec.requiredBackend) {
            RomCapability.ROOT_SHELL -> WorkflowSpecialPermission.ROOT
            RomCapability.SHIZUKU -> WorkflowSpecialPermission.SHIZUKU
            else -> if (spec.strategy == ExecutionStrategy.ELEVATED) {
                WorkflowSpecialPermission.ELEVATED
            } else {
                null
            }
        }
    }

    fun specialPermissionFor(triggerType: TriggerType): WorkflowSpecialPermission? = when (triggerType) {
        TriggerType.TIME -> WorkflowSpecialPermission.EXACT_ALARM
        TriggerType.NOTIFICATION -> WorkflowSpecialPermission.NOTIFICATION_ACCESS
        TriggerType.APPLICATION -> WorkflowSpecialPermission.ACCESSIBILITY
        else -> null
    }

    /**
     * Hard execution requirement. User-action strategies are intentionally not
     * counted as automatic completion paths.
     */
    private fun executionPrivilegeRequirement(actionType: ActionType): ExecutionRequirement {
        exactBackendRequirement(actionType)?.let { return it }

        val elevated = elevatedAuthority()
        return when (actionType) {
            // Public Settings APIs can satisfy these when special access exists;
            // typed Shizuku/Root writes are equally valid automatic alternatives.
            ActionType.SYSTEM_BRIGHTNESS,
            ActionType.SYSTEM_SCREEN_ROTATION,
            ActionType.SYSTEM_SCREEN_TIMEOUT ->
                anyOf(
                    special(PrivilegeSnapshot.SPECIAL_WRITE_SETTINGS),
                    elevated
                )

            ActionType.SYSTEM_DND ->
                anyOf(
                    special(PrivilegeSnapshot.SPECIAL_DND_POLICY),
                    elevated
                )

            // These semantic operations have no automatic public-API write
            // strategy. Settings hand-off is user action, not task completion.
            ActionType.SYSTEM_LOCATION,
            ActionType.SYSTEM_AIRPLANE_MODE,
            ActionType.SYSTEM_NFC,
            ActionType.SYSTEM_HOTSPOT,
            ActionType.SYSTEM_MOBILE_DATA,
            ActionType.APPLICATION_CLOSE_APP,
            ActionType.SYSTEM_FORCE_STOP_APP,
            ActionType.SYSTEM_CLEAR_APP_DATA,
            ActionType.SYSTEM_DISABLE_APP,
            ActionType.SYSTEM_ENABLE_APP -> elevated

            else -> when (specialPermissionFor(actionType)) {
                WorkflowSpecialPermission.WRITE_SETTINGS ->
                    special(PrivilegeSnapshot.SPECIAL_WRITE_SETTINGS)
                WorkflowSpecialPermission.DND_ACCESS ->
                    special(PrivilegeSnapshot.SPECIAL_DND_POLICY)
                WorkflowSpecialPermission.NOTIFICATION_ACCESS ->
                    special(PrivilegeSnapshot.SPECIAL_NOTIFICATION_LISTENER)
                WorkflowSpecialPermission.ACCESSIBILITY ->
                    special(PrivilegeSnapshot.SPECIAL_ACCESSIBILITY_SERVICE)
                WorkflowSpecialPermission.SHIZUKU -> shizukuAuthority()
                WorkflowSpecialPermission.ROOT -> rootAuthority()
                WorkflowSpecialPermission.ELEVATED -> elevated
                WorkflowSpecialPermission.EXACT_ALARM ->
                    special(PrivilegeSnapshot.SPECIAL_EXACT_ALARM)
                null -> elevatedRequirementFromCommandSpec(actionType)
            }
        }
    }

    private fun executionPrivilegeRequirement(triggerType: TriggerType): ExecutionRequirement =
        when (specialPermissionFor(triggerType)) {
            WorkflowSpecialPermission.NOTIFICATION_ACCESS ->
                special(PrivilegeSnapshot.SPECIAL_NOTIFICATION_LISTENER)
            WorkflowSpecialPermission.ACCESSIBILITY ->
                special(PrivilegeSnapshot.SPECIAL_ACCESSIBILITY_SERVICE)
            WorkflowSpecialPermission.EXACT_ALARM ->
                special(PrivilegeSnapshot.SPECIAL_EXACT_ALARM)
            WorkflowSpecialPermission.WRITE_SETTINGS ->
                special(PrivilegeSnapshot.SPECIAL_WRITE_SETTINGS)
            WorkflowSpecialPermission.DND_ACCESS ->
                special(PrivilegeSnapshot.SPECIAL_DND_POLICY)
            WorkflowSpecialPermission.SHIZUKU -> shizukuAuthority()
            WorkflowSpecialPermission.ROOT -> rootAuthority()
            WorkflowSpecialPermission.ELEVATED -> elevatedAuthority()
            null -> ExecutionRequirement.None
        }

    private fun isSpecialGrantMissing(
        special: WorkflowSpecialPermission,
        snapshot: PrivilegeSnapshot
    ): Boolean = when (special) {
        WorkflowSpecialPermission.WRITE_SETTINGS ->
            snapshot.isGranted(
                PrivilegeSurface.SPECIAL_ACCESS,
                PrivilegeSnapshot.SPECIAL_WRITE_SETTINGS
            ) == false

        WorkflowSpecialPermission.DND_ACCESS ->
            snapshot.isGranted(
                PrivilegeSurface.SPECIAL_ACCESS,
                PrivilegeSnapshot.SPECIAL_DND_POLICY
            ) == false

        WorkflowSpecialPermission.NOTIFICATION_ACCESS ->
            snapshot.isGranted(
                PrivilegeSurface.SPECIAL_ACCESS,
                PrivilegeSnapshot.SPECIAL_NOTIFICATION_LISTENER
            ) == false

        WorkflowSpecialPermission.ACCESSIBILITY ->
            snapshot.isGranted(
                PrivilegeSurface.SPECIAL_ACCESS,
                PrivilegeSnapshot.SPECIAL_ACCESSIBILITY_SERVICE
            ) == false

        WorkflowSpecialPermission.EXACT_ALARM ->
            snapshot.isGranted(
                PrivilegeSurface.SPECIAL_ACCESS,
                PrivilegeSnapshot.SPECIAL_EXACT_ALARM
            ) == false

        WorkflowSpecialPermission.SHIZUKU ->
            snapshot.isGranted(
                PrivilegeSurface.SHIZUKU,
                PrivilegeSnapshot.ENV_SHIZUKU
            ) == false

        WorkflowSpecialPermission.ROOT ->
            snapshot.isGranted(
                PrivilegeSurface.ROOT,
                PrivilegeSnapshot.ENV_ROOT
            ) == false

        WorkflowSpecialPermission.ELEVATED -> {
            val shizuku = snapshot.isGranted(
                PrivilegeSurface.SHIZUKU,
                PrivilegeSnapshot.ENV_SHIZUKU
            )
            val root = snapshot.isGranted(
                PrivilegeSurface.ROOT,
                PrivilegeSnapshot.ENV_ROOT
            )
            shizuku == false && root == false
        }
    }

    private fun exactBackendRequirement(actionType: ActionType): ExecutionRequirement? =
        when (CommandCatalog.specFor(actionType)?.requiredBackend) {
            RomCapability.ROOT_SHELL -> rootAuthority()
            RomCapability.SHIZUKU -> shizukuAuthority()
            else -> null
        }

    private fun elevatedRequirementFromCommandSpec(actionType: ActionType): ExecutionRequirement {
        val spec = CommandCatalog.specFor(actionType) ?: return ExecutionRequirement.None
        return if (spec.strategy == ExecutionStrategy.ELEVATED) {
            elevatedAuthority()
        } else {
            ExecutionRequirement.None
        }
    }

    private fun capabilityRequirement(requirement: CapabilityRequirement): ExecutionRequirement =
        when (requirement) {
            CapabilityRequirement.None -> ExecutionRequirement.None
            is CapabilityRequirement.Capability -> ExecutionRequirement.Capability(requirement.id)
            is CapabilityRequirement.AllOf ->
                allOf(*requirement.requirements.map(::capabilityRequirement).toTypedArray())
            is CapabilityRequirement.AnyOf ->
                anyOf(*requirement.requirements.map(::capabilityRequirement).toTypedArray())
            is CapabilityRequirement.Not ->
                ExecutionRequirement.Not(capabilityRequirement(requirement.requirement))
        }

    private fun runtimeRequirement(permissions: List<String>): ExecutionRequirement =
        allOf(*permissions.distinct().map { permission ->
            ExecutionRequirement.AndroidPermission(permission)
        }.toTypedArray())

    private fun special(key: String) = ExecutionRequirement.SpecialAccess(key)

    private fun shizukuAuthority() = ExecutionRequirement.Authority(
        PrivilegeSurface.SHIZUKU,
        PrivilegeSnapshot.ENV_SHIZUKU
    )

    private fun rootAuthority() = ExecutionRequirement.Authority(
        PrivilegeSurface.ROOT,
        PrivilegeSnapshot.ENV_ROOT
    )

    private fun elevatedAuthority(): ExecutionRequirement =
        anyOf(shizukuAuthority(), rootAuthority())

    private fun allOf(vararg requirements: ExecutionRequirement): ExecutionRequirement {
        val children = requirements.filterNot { it == ExecutionRequirement.None }
        return when (children.size) {
            0 -> ExecutionRequirement.None
            1 -> children.single()
            else -> ExecutionRequirement.AllOf(children)
        }
    }

    private fun anyOf(vararg requirements: ExecutionRequirement): ExecutionRequirement {
        val children = requirements.filterNot { it == ExecutionRequirement.None }
        return when (children.size) {
            0 -> ExecutionRequirement.None
            1 -> children.single()
            else -> ExecutionRequirement.AnyOf(children)
        }
    }
}
