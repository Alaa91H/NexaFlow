package com.nexaflow.domain.capability

import kotlinx.serialization.Serializable

/**
 * Declarative capability predicate shared by action, trigger, template and
 * workflow metadata. This model intentionally has no Android, Root or Shizuku
 * implementation details; those remain in capability backends.
 */
@Serializable
sealed interface CapabilityRequirement {
    /** The item needs no additional runtime capability beyond compatibility checks. */
    @Serializable
    data object None : CapabilityRequirement

    /** The item is executable only when this concrete capability is available. */
    @Serializable
    data class Capability(val id: CapabilityId) : CapabilityRequirement

    /** Every child must be executable. */
    @Serializable
    data class AllOf(val requirements: List<CapabilityRequirement>) : CapabilityRequirement {
        init {
            require(requirements.isNotEmpty()) { "AllOf requires at least one child" }
        }
    }

    /** At least one child must be executable. */
    @Serializable
    data class AnyOf(val requirements: List<CapabilityRequirement>) : CapabilityRequirement {
        init {
            require(requirements.isNotEmpty()) { "AnyOf requires at least one child" }
        }
    }

    /** The child must not be executable. Primarily useful for mutually exclusive feature variants. */
    @Serializable
    data class Not(val requirement: CapabilityRequirement) : CapabilityRequirement
}

/** One immutable, cacheable observation consumed by the requirement evaluator. */
data class CapabilitySnapshot(
    val reports: Map<CapabilityId, CapabilityAvailabilityReport> = emptyMap(),
    val observedAtMs: Long = 0L
) {
    fun availabilityOf(id: CapabilityId): CapabilityAvailability =
        reports[id]?.availability ?: CapabilityAvailability.UNSUPPORTED
}

/** Typed diagnostic consumed by the UI and workflow validator; no human text is generated here. */
data class CapabilityRequirementResolution(
    val available: Boolean,
    val missingCapabilities: Set<CapabilityId> = emptySet(),
    val observedAvailability: Map<CapabilityId, CapabilityAvailability> = emptyMap()
)

/**
 * Pure requirement evaluator. Only [CapabilityAvailability.AVAILABLE] is a
 * positive execution admission result: PARTIAL is deliberately not upgraded to
 * executable merely because a backend was detected.
 */
object CapabilityRequirementResolver {
    fun resolve(
        requirement: CapabilityRequirement,
        snapshot: CapabilitySnapshot
    ): CapabilityRequirementResolution = when (requirement) {
        CapabilityRequirement.None -> CapabilityRequirementResolution(available = true)
        is CapabilityRequirement.Capability -> resolveCapability(requirement.id, snapshot)
        is CapabilityRequirement.AllOf -> resolveAll(requirement.requirements, snapshot)
        is CapabilityRequirement.AnyOf -> resolveAny(requirement.requirements, snapshot)
        is CapabilityRequirement.Not -> resolveNot(requirement.requirement, snapshot)
    }

    private fun resolveCapability(
        id: CapabilityId,
        snapshot: CapabilitySnapshot
    ): CapabilityRequirementResolution {
        val availability = snapshot.availabilityOf(id)
        return CapabilityRequirementResolution(
            available = availability == CapabilityAvailability.AVAILABLE,
            missingCapabilities = if (availability == CapabilityAvailability.AVAILABLE) emptySet() else setOf(id),
            observedAvailability = mapOf(id to availability)
        )
    }

    private fun resolveAll(
        requirements: List<CapabilityRequirement>,
        snapshot: CapabilitySnapshot
    ): CapabilityRequirementResolution = merge(
        requirements.map { resolve(it, snapshot) },
        available = true
    )

    private fun resolveAny(
        requirements: List<CapabilityRequirement>,
        snapshot: CapabilitySnapshot
    ): CapabilityRequirementResolution {
        val children = requirements.map { resolve(it, snapshot) }
        val selected = children.firstOrNull { it.available }
        return selected ?: merge(children, available = false)
    }

    private fun resolveNot(
        requirement: CapabilityRequirement,
        snapshot: CapabilitySnapshot
    ): CapabilityRequirementResolution {
        val child = resolve(requirement, snapshot)
        return CapabilityRequirementResolution(
            available = !child.available,
            missingCapabilities = if (child.available) child.observedAvailability.keys else emptySet(),
            observedAvailability = child.observedAvailability
        )
    }

    private fun merge(
        children: List<CapabilityRequirementResolution>,
        available: Boolean
    ): CapabilityRequirementResolution = CapabilityRequirementResolution(
        available = available && children.all { it.available },
        missingCapabilities = children.flatMapTo(linkedSetOf()) { it.missingCapabilities },
        observedAvailability = children.flatMap { it.observedAvailability.entries }.associate { it.toPair() }
    )
}
