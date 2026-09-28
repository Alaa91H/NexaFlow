package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.PrivilegeLevel
import com.nexaflow.domain.capability.operation.DeviceFeature
import com.nexaflow.domain.capability.operation.StrategyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityGraphTest {

    private val op = OperationId("core.operation.set_state")
    private val wifiTarget = TargetId("core.connectivity.wifi")

    private fun provider(
        id: String,
        backend: CapabilityBackendId,
        privilege: PrivilegeLevel? = null,
        features: Set<DeviceFeature> = emptySet(),
    ) = ProviderDescriptor(
        providerId = id,
        strategy = StrategyId.ANDROID_PUBLIC_API,
        capabilities = setOf(ProviderCapability(backend, privilege)),
        requiredFeatures = features,
    )

    private fun requirements(vararg providers: ProviderDescriptor) =
        OperationCapabilityRequirements(operation = op, providers = providers.toList())

    private fun snapshot(
        availability: Map<CapabilityBackendId, CapabilityAvailability>,
        privileges: Set<PrivilegeLevel> = emptySet(),
        features: Set<DeviceFeature> = setOf(DeviceFeature.WIFI_HARDWARE),
    ) = CapabilityGraphSnapshot(
        backendAvailability = availability,
        grantedPrivileges = privileges,
        deviceFeatures = features,
    )

    @Test
    fun highestPriorityEligibleProviderIsSelected() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(
                    provider("core.provider.settings", CapabilityBackendId.INTENT),
                    provider("core.provider.api", CapabilityBackendId.ANDROID_API),
                ),
            ),
        )

        val resolution = resolver.resolve(
            operation = op,
            snapshot = snapshot(
                mapOf(
                    CapabilityBackendId.ANDROID_API to CapabilityAvailability.AVAILABLE,
                    CapabilityBackendId.INTENT to CapabilityAvailability.AVAILABLE,
                ),
            ),
        )

        assertEquals(CapabilityResolutionStatus.RESOLVED, resolution.status)
        assertEquals("core.provider.api", resolution.selectedProviderId)
        assertEquals(listOf("core.provider.settings"), resolution.fallbackProviderIds)
    }

    @Test
    fun providerSelectionIsDeterministicAcrossRepeatedCalls() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(
                    provider("core.provider.b", CapabilityBackendId.ADB),
                    provider("core.provider.a", CapabilityBackendId.ADB),
                ),
            ),
        )
        val snapshot = snapshot(mapOf(CapabilityBackendId.ADB to CapabilityAvailability.AVAILABLE))

        val results = List(50) {
            resolver.resolve(op, CapabilitySelectionPolicy.PRIVILEGED, snapshot)
        }

        assertTrue(results.all { it == results.first() })
        assertEquals("core.provider.a", results.first().selectedProviderId)
    }

    @Test
    fun pinnedBackendWinsOverRanking() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(
                    provider("core.provider.api", CapabilityBackendId.ANDROID_API),
                    provider("core.provider.shizuku", CapabilityBackendId.SHIZUKU, PrivilegeLevel.SHIZUKU),
                ),
            ),
        )
        val snapshot = snapshot(
            mapOf(
                CapabilityBackendId.ANDROID_API to CapabilityAvailability.AVAILABLE,
                CapabilityBackendId.SHIZUKU to CapabilityAvailability.AVAILABLE,
            ),
            privileges = setOf(PrivilegeLevel.SHIZUKU),
        )

        val resolution = resolver.resolve(
            op,
            CapabilitySelectionPolicy(
                pinnedBackend = CapabilityBackendId.SHIZUKU,
                allowPrivilegedBackends = true,
            ),
            snapshot,
        )

        assertEquals("core.provider.shizuku", resolution.selectedProviderId)
    }

    @Test
    fun preferredBackendsReorderEligibleProviders() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(
                    provider("core.provider.api", CapabilityBackendId.ANDROID_API),
                    provider("core.provider.shizuku", CapabilityBackendId.SHIZUKU, PrivilegeLevel.SHIZUKU),
                ),
            ),
        )
        val snapshot = snapshot(
            mapOf(
                CapabilityBackendId.ANDROID_API to CapabilityAvailability.AVAILABLE,
                CapabilityBackendId.SHIZUKU to CapabilityAvailability.AVAILABLE,
            ),
            privileges = setOf(PrivilegeLevel.SHIZUKU),
        )

        val resolution = resolver.resolve(
            op,
            CapabilitySelectionPolicy(
                preferredBackends = setOf(CapabilityBackendId.SHIZUKU),
                allowPrivilegedBackends = true,
            ),
            snapshot,
        )

        assertEquals("core.provider.shizuku", resolution.selectedProviderId)
    }

    @Test
    fun privilegedProviderRequiresExplicitOptIn() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(
                    provider("core.provider.shizuku", CapabilityBackendId.SHIZUKU, PrivilegeLevel.SHIZUKU),
                ),
            ),
        )
        val snapshot = snapshot(
            mapOf(CapabilityBackendId.SHIZUKU to CapabilityAvailability.AVAILABLE),
            privileges = setOf(PrivilegeLevel.SHIZUKU),
        )

        val withoutOptIn = resolver.resolve(op, CapabilitySelectionPolicy(), snapshot)

        assertEquals(CapabilityResolutionStatus.UNSUPPORTED, withoutOptIn.status)
        assertTrue(
            withoutOptIn.exclusions.any {
                it.errorCode == CapabilityResolutionError.PRIVILEGE_NOT_GRANTED
            },
        )

        val withOptIn = resolver.resolve(op, CapabilitySelectionPolicy.PRIVILEGED, snapshot)
        assertEquals(CapabilityResolutionStatus.RESOLVED, withOptIn.status)
    }

    @Test
    fun missingHardwareExcludesProviderBeforeAvailability() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(
                    provider(
                        "core.provider.nfc",
                        CapabilityBackendId.ANDROID_API,
                        features = setOf(DeviceFeature.NFC_HARDWARE),
                    ),
                ),
            ),
        )

        val resolution = resolver.resolve(
            op,
            CapabilitySelectionPolicy(),
            snapshot(mapOf(CapabilityBackendId.ANDROID_API to CapabilityAvailability.AVAILABLE), features = emptySet()),
        )

        assertEquals(CapabilityResolutionStatus.UNSUPPORTED, resolution.status)
        assertTrue(resolution.exclusions.any { it.errorCode == CapabilityResolutionError.HARDWARE_MISSING })
    }

    @Test
    fun partialAvailabilityNeverUpgradesToExecutable() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(provider("core.provider.api", CapabilityBackendId.ANDROID_API)),
            ),
        )

        val resolution = resolver.resolve(
            op,
            CapabilitySelectionPolicy(),
            snapshot(mapOf(CapabilityBackendId.ANDROID_API to CapabilityAvailability.PARTIAL)),
        )

        assertEquals(CapabilityResolutionStatus.UNSUPPORTED, resolution.status)
        assertTrue(resolution.exclusions.all { it.errorCode == CapabilityResolutionError.CAPABILITY_UNAVAILABLE })
    }

    @Test
    fun unobservedBackendFailsClosed() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(provider("core.provider.api", CapabilityBackendId.ANDROID_API)),
            ),
        )

        val resolution = resolver.resolve(op, CapabilitySelectionPolicy(), snapshot(emptyMap()))

        assertEquals(CapabilityResolutionStatus.UNSUPPORTED, resolution.status)
        assertTrue(resolution.exclusions.any { it.errorCode == CapabilityResolutionError.CAPABILITY_UNAVAILABLE })
    }

    @Test
    fun permissionRequiredMapsToPendingUserAction() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(provider("core.provider.api", CapabilityBackendId.ANDROID_API)),
            ),
        )

        val resolution = resolver.resolve(
            op,
            CapabilitySelectionPolicy(),
            snapshot(mapOf(CapabilityBackendId.ANDROID_API to CapabilityAvailability.PERMISSION_REQUIRED)),
        )

        assertEquals(CapabilityResolutionStatus.PENDING_USER_ACTION, resolution.status)
        assertTrue(resolution.exclusions.any { it.errorCode == CapabilityResolutionError.CAPABILITY_MISSING })
    }

    @Test
    fun unsupportedProviderIsNeverSelectedAsSilentFallback() {
        // One live backend that does NOT declare the operation's intent and
        // one ineligible backend: the resolution must stay non-executable.
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(
                    provider("core.provider.offline", CapabilityBackendId.ADB),
                ),
            ),
        )
        // ADB exists but was never observed, so it cannot silently serve.
        val resolution = resolver.resolve(op, CapabilitySelectionPolicy(), snapshot(emptyMap()))

        assertEquals(CapabilityResolutionStatus.UNSUPPORTED, resolution.status)
        assertEquals(null, resolution.selectedProviderId)
    }

    @Test
    fun backendNotAllowedByPolicyIsExcluded() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(
                    provider("core.provider.api", CapabilityBackendId.ANDROID_API),
                    provider("core.provider.intent", CapabilityBackendId.INTENT),
                ),
            ),
        )
        val snapshot = snapshot(
            mapOf(
                CapabilityBackendId.ANDROID_API to CapabilityAvailability.AVAILABLE,
                CapabilityBackendId.INTENT to CapabilityAvailability.AVAILABLE,
            ),
        )

        val resolution = resolver.resolve(
            op,
            CapabilitySelectionPolicy(allowedBackends = setOf(CapabilityBackendId.INTENT)),
            snapshot,
        )

        assertEquals("core.provider.intent", resolution.selectedProviderId)
        assertTrue(
            resolution.exclusions.any {
                it.providerId == "core.provider.api" &&
                    it.errorCode == CapabilityResolutionError.BACKEND_NOT_ALLOWED
            },
        )
    }

    @Test
    fun unknownOperationIsUnsupportedNotGuessed() {
        val resolver = CanonicalCapabilityResolver(listOf(requirements(provider("core.provider.api", CapabilityBackendId.ANDROID_API))))

        val resolution = resolver.resolve(OperationId("core.operation.not_registered"))

        assertEquals(CapabilityResolutionStatus.UNSUPPORTED, resolution.status)
        assertEquals(CapabilityResolutionError.NO_PROVIDERS_DECLARED, resolution.errorCode)
    }

    @Test
    fun fallbackOrderFollowsRankingAfterSelection() {
        val resolver = CanonicalCapabilityResolver(
            listOf(
                requirements(
                    provider("core.provider.root", CapabilityBackendId.ROOT, PrivilegeLevel.ROOT),
                    provider("core.provider.shizuku", CapabilityBackendId.SHIZUKU, PrivilegeLevel.SHIZUKU),
                    provider("core.provider.api", CapabilityBackendId.ANDROID_API),
                ),
            ),
        )
        val snapshot = snapshot(
            mapOf(
                CapabilityBackendId.ANDROID_API to CapabilityAvailability.AVAILABLE,
                CapabilityBackendId.SHIZUKU to CapabilityAvailability.AVAILABLE,
                CapabilityBackendId.ROOT to CapabilityAvailability.AVAILABLE,
            ),
            privileges = setOf(PrivilegeLevel.ROOT, PrivilegeLevel.SHIZUKU),
        )

        val resolution = resolver.resolve(op, CapabilitySelectionPolicy.PRIVILEGED, snapshot)

        assertEquals(
            listOf("core.provider.api", "core.provider.shizuku", "core.provider.root"),
            listOf(resolution.selectedProviderId) + resolution.fallbackProviderIds,
        )
    }

    @Test
    fun duplicateProviderIdsAreRejected() {
        try {
            requirements(
                provider("core.provider.api", CapabilityBackendId.ANDROID_API),
                provider("core.provider.api", CapabilityBackendId.ROOT, PrivilegeLevel.ROOT),
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun emptyProviderListIsRejected() {
        try {
            OperationCapabilityRequirements(operation = op, providers = emptyList())
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun pinnedBackendOutsideAllowedBackendsIsRejected() {
        try {
            CapabilitySelectionPolicy(
                allowedBackends = setOf(CapabilityBackendId.INTENT),
                pinnedBackend = CapabilityBackendId.SHIZUKU,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun providerIdFormatIsEnforced() {
        try {
            provider("Core.Provider", CapabilityBackendId.ANDROID_API)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
