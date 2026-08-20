package com.nexaflow.core.execution.capability

import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.ExecutionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccessibilityCapabilityBackendTest {

    @Test
    fun backendRequiresExplicitAccessibilityPolicyBeforeAnyServiceCheck() = runBlocking {
        val backend = backend(consent = true)

        val availability = backend.availability(clickRequest())

        assertEquals(CapabilityAvailability.PERMISSION_REQUIRED, availability.availability)
        assertTrue(availability.reason.orEmpty().contains("explicitly selected"))
    }

    @Test
    fun backendRequiresDisclosureEvenWhenAccessibilityWasSelected() = runBlocking {
        val backend = backend(consent = false)

        val availability = backend.availability(clickRequest(explicit = true))

        assertEquals(CapabilityAvailability.PERMISSION_REQUIRED, availability.availability)
        assertTrue(availability.reason.orEmpty().contains("disclosure"))
    }

    @Test
    fun disconnectedServiceReturnsStructuredUnavailableResult() = runBlocking {
        val backend = backend(consent = true)

        val result = backend.execute(clickRequest(explicit = true))

        assertFalse(result.isSuccess)
        assertEquals(CapabilityErrorCode.ACCESSIBILITY_UNAVAILABLE, result.errorCode)
    }

    @Test
    fun operationMapperAcceptsOnlyBoundedSelectorShape() {
        val operation = AccessibilityOperation.from(clickRequest(explicit = true))

        assertEquals(
            AccessibilityOperation.Click(
                "com.example.target",
                AccessibilitySelector(AccessibilitySelectorType.VIEW_ID, "com.example.target:id/approve")
            ),
            operation
        )
    }

    @Test
    fun catalogContainsNoGenericCommandParameter() {
        val parameterNames = AccessibilityCapabilityCatalog.descriptors()
            .flatMap { descriptor -> descriptor.parameters.map { it.name } }

        assertFalse("command" in parameterNames)
        assertTrue(CapabilityId.ACCESSIBILITY_GESTURE in AccessibilityCapabilityCatalog.descriptors().map { it.id })
    }

    private fun backend(consent: Boolean): AccessibilityCapabilityBackend = AccessibilityCapabilityBackend(
        context = ApplicationProvider.getApplicationContext(),
        bridge = AccessibilityInteractionBridge(),
        consentGranted = { consent }
    )

    private fun clickRequest(explicit: Boolean = false): CapabilityRequest = CapabilityRequest(
        capability = CapabilityId.ACCESSIBILITY_CLICK,
        parameters = mapOf(
            "packageName" to "com.example.target",
            "selectorType" to "VIEW_ID",
            "selector" to "com.example.target:id/approve"
        ),
        policy = if (explicit) {
            ExecutionPolicy(
                allowedBackends = listOf(CapabilityBackendId.ACCESSIBILITY),
                preferredBackends = listOf(CapabilityBackendId.ACCESSIBILITY),
                allowPrivilegedBackends = true
            )
        } else {
            ExecutionPolicy()
        }
    )
}
