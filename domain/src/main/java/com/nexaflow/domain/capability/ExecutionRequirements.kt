package com.nexaflow.domain.capability

import kotlinx.serialization.Serializable

/**
 * One declarative prerequisite for executing a workflow node. Unlike the older
 * capability-only predicate, this graph can express Android permission gates
 * and alternative authorities (for example Shizuku OR Root) without leaking
 * implementation code into the workflow model.
 */
@Serializable
sealed interface ExecutionRequirement {
    @Serializable
    data object None : ExecutionRequirement

    @Serializable
    data class Capability(val id: CapabilityId) : ExecutionRequirement

    @Serializable
    data class AndroidPermission(val permission: String) : ExecutionRequirement {
        init {
            require(permission.isNotBlank()) { "Android permission must not be blank" }
        }
    }

    @Serializable
    data class SpecialAccess(val key: String) : ExecutionRequirement {
        init {
            require(key.isNotBlank()) { "Special-access key must not be blank" }
        }
    }

    @Serializable
    data class AppOp(val op: String) : ExecutionRequirement {
        init {
            require(op.isNotBlank()) { "App-op must not be blank" }
        }
    }

    @Serializable
    data class Authority(
        val surface: PrivilegeSurface,
        val key: String
    ) : ExecutionRequirement {
        init {
            require(
                surface == PrivilegeSurface.SHIZUKU ||
                    surface == PrivilegeSurface.ROOT ||
                    surface == PrivilegeSurface.DEVICE_OWNER
            ) { "Authority requirements are only valid for elevated authority surfaces" }
            require(key.isNotBlank()) { "Authority key must not be blank" }
        }
    }

    @Serializable
    data class AllOf(val requirements: List<ExecutionRequirement>) : ExecutionRequirement {
        init {
            require(requirements.isNotEmpty()) { "AllOf requires at least one child" }
        }
    }

    @Serializable
    data class AnyOf(val requirements: List<ExecutionRequirement>) : ExecutionRequirement {
        init {
            require(requirements.isNotEmpty()) { "AnyOf requires at least one child" }
        }
    }

    @Serializable
    data class Not(val requirement: ExecutionRequirement) : ExecutionRequirement
}

/**
 * READY means execution admission may proceed. UNKNOWN is deliberately not a
 * denial: at process start a snapshot can be unobserved, and blocking on that
 * race would strand otherwise runnable automations. BLOCKED is emitted only
 * from a completed observation that proves no declared branch is currently
 * usable.
 */
@Serializable
enum class ExecutionRequirementState {
    READY,
    UNKNOWN,
    BLOCKED
}

/** Stable reference to one missing or undecided privilege leaf. */
@Serializable
data class PrivilegeRequirementRef(
    val surface: PrivilegeSurface,
    val key: String
)

/** Machine-readable result used by preflight, runtime admission and UI. */
data class ExecutionRequirementResolution(
    val state: ExecutionRequirementState,
    val missingCapabilities: Set<CapabilityId> = emptySet(),
    val missingPrivileges: Set<PrivilegeRequirementRef> = emptySet(),
    val unknownCapabilities: Set<CapabilityId> = emptySet(),
    val unknownPrivileges: Set<PrivilegeRequirementRef> = emptySet()
) {
    val ready: Boolean get() = state == ExecutionRequirementState.READY
    val blocked: Boolean get() = state == ExecutionRequirementState.BLOCKED
    val unknown: Boolean get() = state == ExecutionRequirementState.UNKNOWN
}

/**
 * Pure tri-state graph resolver.
 *
 * - ALL: one proven blocker blocks the group; otherwise unknown propagates.
 * - ANY: one ready branch is enough; unknown outranks blocked while another
 *   branch has not been observed yet.
 * - Permission/AppOps/authority leaves trust only the unified verified
 *   [PrivilegeSnapshot], never backend names or cached UI state.
 */
object ExecutionRequirementResolver {
    fun resolve(
        requirement: ExecutionRequirement,
        capabilitySnapshot: CapabilitySnapshot,
        privilegeSnapshot: PrivilegeSnapshot
    ): ExecutionRequirementResolution = when (requirement) {
        ExecutionRequirement.None -> ready()
        is ExecutionRequirement.Capability ->
            resolveCapability(requirement.id, capabilitySnapshot)
        is ExecutionRequirement.AndroidPermission ->
            resolvePrivilegeLeaf(
                privilegeSnapshot,
                PrivilegeRequirementRef(
                    PrivilegeSurface.ANDROID_PERMISSION,
                    requirement.permission
                ),
                privilegeSnapshot.grantedAndroidPermission(requirement.permission)
            )
        is ExecutionRequirement.SpecialAccess ->
            resolvePrivilegeLeaf(
                privilegeSnapshot,
                PrivilegeRequirementRef(PrivilegeSurface.SPECIAL_ACCESS, requirement.key),
                privilegeSnapshot.isGranted(PrivilegeSurface.SPECIAL_ACCESS, requirement.key)
            )
        is ExecutionRequirement.AppOp ->
            resolvePrivilegeLeaf(
                privilegeSnapshot,
                PrivilegeRequirementRef(PrivilegeSurface.APP_OP, requirement.op),
                privilegeSnapshot.isGranted(PrivilegeSurface.APP_OP, requirement.op)
            )
        is ExecutionRequirement.Authority ->
            resolvePrivilegeLeaf(
                privilegeSnapshot,
                PrivilegeRequirementRef(requirement.surface, requirement.key),
                privilegeSnapshot.isGranted(requirement.surface, requirement.key)
            )
        is ExecutionRequirement.AllOf ->
            resolveAll(requirement.requirements, capabilitySnapshot, privilegeSnapshot)
        is ExecutionRequirement.AnyOf ->
            resolveAny(requirement.requirements, capabilitySnapshot, privilegeSnapshot)
        is ExecutionRequirement.Not -> {
            val child = resolve(requirement.requirement, capabilitySnapshot, privilegeSnapshot)
            when (child.state) {
                ExecutionRequirementState.READY -> ExecutionRequirementResolution(
                    state = ExecutionRequirementState.BLOCKED,
                    missingCapabilities = child.missingCapabilities,
                    missingPrivileges = child.missingPrivileges
                )
                ExecutionRequirementState.BLOCKED -> ready()
                ExecutionRequirementState.UNKNOWN -> child
            }
        }
    }

    private fun resolveCapability(
        id: CapabilityId,
        snapshot: CapabilitySnapshot
    ): ExecutionRequirementResolution {
        if (snapshot.neverObserved) {
            return ExecutionRequirementResolution(
                state = ExecutionRequirementState.UNKNOWN,
                unknownCapabilities = setOf(id)
            )
        }
        val availability = snapshot.availabilityOf(id)
        return if (availability == CapabilityAvailability.AVAILABLE) {
            ready()
        } else {
            ExecutionRequirementResolution(
                state = ExecutionRequirementState.BLOCKED,
                missingCapabilities = setOf(id)
            )
        }
    }

    private fun resolvePrivilegeLeaf(
        snapshot: PrivilegeSnapshot,
        ref: PrivilegeRequirementRef,
        granted: Boolean?
    ): ExecutionRequirementResolution {
        if (snapshot.neverObserved || granted == null) {
            return ExecutionRequirementResolution(
                state = ExecutionRequirementState.UNKNOWN,
                unknownPrivileges = setOf(ref)
            )
        }
        return if (granted) {
            ready()
        } else {
            ExecutionRequirementResolution(
                state = ExecutionRequirementState.BLOCKED,
                missingPrivileges = setOf(ref)
            )
        }
    }

    private fun resolveAll(
        requirements: List<ExecutionRequirement>,
        capabilities: CapabilitySnapshot,
        privileges: PrivilegeSnapshot
    ): ExecutionRequirementResolution {
        val children = requirements.map { resolve(it, capabilities, privileges) }
        val state = when {
            children.any { it.state == ExecutionRequirementState.BLOCKED } ->
                ExecutionRequirementState.BLOCKED
            children.any { it.state == ExecutionRequirementState.UNKNOWN } ->
                ExecutionRequirementState.UNKNOWN
            else -> ExecutionRequirementState.READY
        }
        return merge(children, state)
    }

    private fun resolveAny(
        requirements: List<ExecutionRequirement>,
        capabilities: CapabilitySnapshot,
        privileges: PrivilegeSnapshot
    ): ExecutionRequirementResolution {
        val children = requirements.map { resolve(it, capabilities, privileges) }
        children.firstOrNull { it.state == ExecutionRequirementState.READY }?.let { return ready() }
        val state = if (children.any { it.state == ExecutionRequirementState.UNKNOWN }) {
            ExecutionRequirementState.UNKNOWN
        } else {
            ExecutionRequirementState.BLOCKED
        }
        return merge(children, state)
    }

    private fun merge(
        children: List<ExecutionRequirementResolution>,
        state: ExecutionRequirementState
    ) = ExecutionRequirementResolution(
        state = state,
        missingCapabilities = children.flatMapTo(linkedSetOf()) { it.missingCapabilities },
        missingPrivileges = children.flatMapTo(linkedSetOf()) { it.missingPrivileges },
        unknownCapabilities = children.flatMapTo(linkedSetOf()) { it.unknownCapabilities },
        unknownPrivileges = children.flatMapTo(linkedSetOf()) { it.unknownPrivileges }
    )

    private fun ready() = ExecutionRequirementResolution(
        state = ExecutionRequirementState.READY
    )
}
