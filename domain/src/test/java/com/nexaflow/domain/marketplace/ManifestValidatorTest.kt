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
        val checksum = WorkflowManifest.calculateChecksum(payload)
        
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
        
        if (result is ManifestValidator.ValidationResult.Invalid) {
            assertTrue(result.reason.contains("tampered"))
        } else {
            fail("Expected Invalid result but got $result")
        }
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
            minNexaFlowVersion = 10,
            payload = payload,
            requiredCapabilities = emptySet(),
            payloadChecksum = checksum
        )
        
        val validator = ManifestValidator()
        val result = validator.validate(manifest, currentAppVersion = 5)
        
        if (result is ManifestValidator.ValidationResult.Invalid) {
            assertTrue(result.reason.contains("too old"))
        } else {
            fail("Expected Invalid result but got $result")
        }
    }
}
