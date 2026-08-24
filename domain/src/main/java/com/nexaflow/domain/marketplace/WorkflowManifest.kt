package com.nexaflow.domain.marketplace

import com.nexaflow.domain.capability.CapabilityId
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.Base64

/**
 * A sealed envelope for exporting and importing NexaFlow workflows safely.
 *
 * It contains the raw workflow payload (usually JSON), metadata about what
 * capabilities it requires, and a cryptographic checksum to detect tampering.
 */
@Serializable
data class WorkflowManifest(
    val id: String,
    val name: String,
    val author: String,
    val version: Int,
    val minNexaFlowVersion: Int,
    
    /** The serialized workflow data (JSON format of WorkflowNode tree). */
    val payload: String,
    
    /** 
     * Capabilities requested by this workflow. The importer will warn the user
     * if these capabilities require high privileges (e.g., Root or Shizuku).
     */
    val requiredCapabilities: Set<CapabilityId>,
    
    /** SHA-256 hash of the payload to ensure integrity. */
    val payloadChecksum: String,
    
    /** Optional cryptographic signature if signed by a trusted developer. */
    val signature: String? = null
) {
    /** Validates that the checksum matches the actual payload. */
    fun verifyChecksum(): Boolean {
        return calculateChecksum(payload) == payloadChecksum
    }

    companion object {
        fun calculateChecksum(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(hashBytes)
        }
    }
}

/**
 * Validates manifests before importing them into the runtime.
 */
class ManifestValidator {
    
    sealed interface ValidationResult {
        object Valid : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }

    fun validate(manifest: WorkflowManifest, currentAppVersion: Int): ValidationResult {
        if (currentAppVersion < manifest.minNexaFlowVersion) {
            return ValidationResult.Invalid("NexaFlow version $currentAppVersion is too old. Requires ${manifest.minNexaFlowVersion}")
        }
        
        if (!manifest.verifyChecksum()) {
            return ValidationResult.Invalid("Payload checksum mismatch. The workflow file may be corrupted or tampered with.")
        }
        
        if (manifest.payload.isBlank()) {
            return ValidationResult.Invalid("Payload is empty.")
        }
        
        return ValidationResult.Valid
    }
}
