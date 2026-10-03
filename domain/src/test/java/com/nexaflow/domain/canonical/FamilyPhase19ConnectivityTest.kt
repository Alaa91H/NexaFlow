package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.PrivilegeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyPhase19ConnectivityTest {

    private val baseAdapter = LegacyCanonicalAdapter(LegacyMappingTable.all())
    private val familyAdapter = FamilyPhase19Connectivity.adapterWithFamily()
    private val resolver = CanonicalCapabilityResolver(
        listOf(FamilyPhase19Connectivity.stateWriteProviders()),
    )

    private fun overrides() = FamilyPhase19Connectivity.ruleOverrides()

    @Test
    fun familyOverridesCoverConnectivityMembers() {
        // 17 state/value/session actions + 16 triggers (settings pages stay
        // with the T17 pilot family).
        assertEquals(17, overrides().count { it.kind == LegacyNodeKind.ACTION })
        assertEquals(16, overrides().count { it.kind == LegacyNodeKind.TRIGGER })
    }

    @Test
    fun familyAdapterKeepsTheFullTable() {
        assertEquals(237, familyAdapter.declaredRules)
    }

    @Test
    fun enableActionsUpgradeToTypedSetState() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_WIFI",
                kind = LegacyNodeKind.ACTION,
                config = listOf(LegacyConfigEntry("enabled", "true")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as SetStateNode
        assertEquals(TargetId("core.connectivity.wifi"), node.target)
        assertEquals(BooleanValue(true), node.state)
        // Parity with the skeleton mapping.
        val skeleton = baseAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_WIFI", LegacyNodeKind.ACTION, listOf(LegacyConfigEntry("enabled", "true"))),
        ) as LegacyAdapterOutcome.Canonicalized
        assertEquals((skeleton.node as InvokeNode).target, node.target)
    }

    @Test
    fun missingEnabledKeyDefersToCatalogContract() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_BLUETOOTH", LegacyNodeKind.ACTION, emptyList()),
        )
        val canonicalized = outcome as LegacyAdapterOutcome.Canonicalized
        assertTrue(canonicalized.node is InvokeNode)
    }

    @Test
    fun bogusEnabledValueIsRejected() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_HOTSPOT",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("enabled", "on")),
            ),
        )
        assertEquals(
            LegacyAdapterRejection.UNPARSABLE_CONFIG_VALUE,
            (outcome as LegacyAdapterOutcome.Rejected).reason,
        )
    }

    @Test
    fun wifiConnectCarriesSsidAndSecretPasswordReference() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                legacyType = "SYSTEM_WIFI_CONNECT",
                kind = LegacyNodeKind.ACTION,
                config = listOf(
                    LegacyConfigEntry("ssid", "HomeNet"),
                    LegacyConfigEntry("password", "sup3r-secret"),
                ),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(TextValue("HomeNet"), node.arguments[CanonicalFieldId("ssid")])
        // The raw password never enters the AST: only a stable reference does.
        val password = node.arguments[CanonicalFieldId("password")]
        assertTrue(password is SecretReferenceValue)
        assertEquals("legacy.wifi_password", (password as SecretReferenceValue).referenceId)
    }

    @Test
    fun triggersUpgradeEnabledStateConditionally() {
        val withState = familyAdapter.canonicalize(
            LegacyNodeInput(
                legacyType = "WIFI_CONNECTED",
                kind = LegacyNodeKind.TRIGGER,
                config = listOf(LegacyConfigEntry("enabled", "true")),
            ),
        ) as LegacyAdapterOutcome.Canonicalized
        val observation = withState.node as ObserveNode
        assertEquals(
            BooleanValue(true),
            observation.arguments[CanonicalFieldId("connected")],
        )

        // Without the key, the observation stays stateless (any-state match).
        val stateless = familyAdapter.canonicalize(
            LegacyNodeInput("WIFI_CONNECTED", LegacyNodeKind.TRIGGER, emptyList()),
        ) as LegacyAdapterOutcome.Canonicalized
        assertEquals(0, (stateless.node as ObserveNode).arguments.entries.size)
    }

    @Test
    fun contradictoryConnectivityBatchIsRejectedBySemanticRules() {
        val writes = listOf<CanonicalActionNode>(
            SetStateNode(CanonicalNodeId("w1"), TargetId("core.connectivity.wifi"), BooleanValue(true)),
            SetStateNode(CanonicalNodeId("w2"), TargetId("core.connectivity.wifi"), BooleanValue(false)),
        )
        val violations = evaluateWriteConflicts(writes)
        assertTrue(violations.any { it is DuplicateConflictingWrites })
    }

    @Test
    fun multiTargetOrderedSemanticsAreExecutable() {
        val errors = validateSelectionSemantics(
            semantics = FamilyPhase19Connectivity.enableSemantics,
            selectedTargetCount = 3,
            cardinality = FamilyPhase19Connectivity.enableCardinality,
        )
        assertTrue("expected no violations, got $errors", errors.isEmpty())
    }

    @Test
    fun stateWriteProvidersResolvePublicApiFirstWithPrivilegedFallback() {
        val available = CapabilityGraphSnapshot(
            backendAvailability = mapOf(
                CapabilityBackendId.ANDROID_API to CapabilityAvailability.AVAILABLE,
                CapabilityBackendId.SHIZUKU to CapabilityAvailability.AVAILABLE,
                CapabilityBackendId.ROOT to CapabilityAvailability.AVAILABLE,
            ),
            grantedPrivileges = setOf(PrivilegeLevel.SHIZUKU, PrivilegeLevel.ROOT),
        )

        val resolution = resolver.resolve(
            OperationId("core.operation.set_state"),
            CapabilitySelectionPolicy.PRIVILEGED,
            available,
        )
        assertEquals("core.provider.connectivity.public_api", resolution.selectedProviderId)
        assertEquals(
            listOf("core.provider.connectivity.shizuku", "core.provider.connectivity.root"),
            resolution.fallbackProviderIds,
        )

        // Without public API, Shizuku becomes the selection (fallback works).
        val noPublicApi = available.copy(
            backendAvailability = available.backendAvailability - CapabilityBackendId.ANDROID_API,
        )
        val fallback = resolver.resolve(
            OperationId("core.operation.set_state"),
            CapabilitySelectionPolicy.PRIVILEGED,
            noPublicApi,
        )
        assertEquals("core.provider.connectivity.shizuku", fallback.selectedProviderId)
    }

    @Test
    fun enableSchemaValidatesTypedDrafts() {
        val schema = FamilyPhase19Connectivity.enableSchema()
        val valid = validateNodeValues(
            schema,
            listOf(NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(false))),
        )
        assertTrue(valid.isEmpty())

        val bad = validateNodeValues(
            schema,
            listOf(NodeFieldValue(CanonicalFieldId("enabled"), TextValue("false"))),
        )
        assertTrue(bad.any { it is FieldTypeMismatch })
    }

    @Test
    fun canonicalizationRemainsIdempotent() {
        for (rule in overrides()) {
            val config = when (rule.kind) {
                LegacyNodeKind.ACTION ->
                    if ("SYSTEM_WIFI_CONNECT" == rule.legacyType ||
                        "SYSTEM_WIFI_FORGET" == rule.legacyType
                    ) {
                        listOf(LegacyConfigEntry("ssid", "Net"))
                    } else {
                        listOf(LegacyConfigEntry("enabled", "true"))
                    }
                LegacyNodeKind.TRIGGER -> emptyList()
            }
            val input = LegacyNodeInput(rule.legacyType, rule.kind, config)
            assertEquals(
                "non-idempotent ${rule.legacyType}",
                familyAdapter.canonicalize(input),
                familyAdapter.canonicalize(input),
            )
        }
    }
}
