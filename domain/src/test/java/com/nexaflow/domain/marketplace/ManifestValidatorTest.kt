package com.nexaflow.domain.marketplace

import com.nexaflow.domain.capability.CapabilityId
import org.junit.Assert.*
import org.junit.Test

class ManifestValidatorTest {

    @Test
    fun `valid manifest passes`() {
        val payload = "{\"nodes\": []}"
        val checksum = WorkflowManifest.calculateChecksum(payload)
        val manifest = WorkflowManifest(
            id = "w1",
            name = "Test Workflow",
            author = "Dev",
            version = 1,
            minNexaFlowVersion = 1,
            payload = payload,
            requiredCapabilities = emptySet(),
            payloadChecksum = checksum
        )
        
        val validator = ManifestValidator()
        val result = validator.validate(manifest, currentAppVersion = 2)
        
        assertEquals(ManifestValidator.ValidationResult.Valid, result)
    }

    @Test
    fun `rejects tampered payload`() {
        val payload = "{\"nodes\": []}"
        // Checksum calculated on original payload
        val checksum = WorkflowManifest.calculateChecksum(payload)
        
        // Manifest created with tampered payload but original checksum
        val manifest = WorkflowManifest(
            id = "w1",
            name = "Test Workflow",
            author = "Dev",
            version = 1,
            minNexaFlowVersion = 1,
            payload = "{\"nodes\": [{\"hacked\": true}]}",
            requiredCapabilities = emptySet(),
            payloadChecksum = checksum
        )
        
        val validator = ManifestValidator()
        val result = validator.validate(manifest, currentAppVersion = 2)
        
        assertTrue(result is ManifestValidator.ValidationResult.Invalid)
        val invalid = result as ManifestValidator.ValidationResult.Invalid
        assertTrue(invalid.reason.contains("tampered"))
    }

    @Test
    fun `rejects incompatible version`() {
        val payload = "{\"nodes\": []}"
        val checksum = WorkflowManifest.calculateChecksum(payload)
        val manifest = WorkflowManifest(
            id = "w1",
            name = "Test Workflow",
            author = "Dev",
            version = 1,
            minNexaFlowVersion = 10, // Requires version 10
            payload = payload,
            requiredCapabilities = emptySet(),
            payloadChecksum = checksum
        )
        
        val validator = ManifestValidator()
        val result = validator.validate(manifest, currentAppVersion = 5) // App is version 5
        
        assertTrue(result is ManifestValidator.ValidationResult.Invalid)
        val invalid = result as ManifestValidator.ValidationResult.Invalid
        assertTrue(invalid.reason.contains("too old"))
    }
}
