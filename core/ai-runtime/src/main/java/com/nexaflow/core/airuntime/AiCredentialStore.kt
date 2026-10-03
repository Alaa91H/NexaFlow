package com.nexaflow.core.airuntime

/** Runtime-only secret access. Implementations must keep plaintext out of persistence and UI state. */
interface AiCredentialStore {
    suspend fun resolve(reference: AiCredentialReference): String?
    suspend fun store(reference: AiCredentialReference, value: String)
    suspend fun delete(reference: AiCredentialReference)
}

/**
 * Stable opaque references for AI credentials. The reference is safe to persist;
 * the secret value is not.
 */
object AiCredentialReferences {
    val legacySingleProvider =
        AiCredentialReference("ai.provider.openai_compatible.api_key")

    fun forProfile(profileId: String): AiCredentialReference {
        require(profileId.length in 1..MAX_PROFILE_ID_LENGTH)
        require(profileId.matches(PROFILE_ID_PATTERN))
        return AiCredentialReference("ai.provider.profile.$profileId.api_key")
    }

    private const val MAX_PROFILE_ID_LENGTH = 128
    private val PROFILE_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
}
