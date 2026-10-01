package com.nexaflow.core.airuntime

/**
 * Product/provider identity is deliberately separate from the wire protocol.
 * Multiple providers can speak the same dialect and gateways can expose more
 * than one dialect without becoming a new protocol implementation.
 */
enum class AiProviderKind {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    OPENCODE,
    OPENAI_COMPATIBLE,
    LOCAL,
    CUSTOM
}

/** Concrete API dialect spoken on the wire. */
enum class AiApiDialect {
    OPENAI_CHAT_COMPLETIONS,
    OPENAI_RESPONSES,
    ANTHROPIC_MESSAGES,
    GEMINI_GENERATE_CONTENT
}

/**
 * Source-compatible bridge for the pre-V2 name. Remove only after all
 * persistence/UI/runtime callers have migrated to [AiApiDialect].
 */
typealias AiProviderProtocol = AiApiDialect

enum class AiAuthScheme {
    BEARER_TOKEN,
    X_API_KEY,
    GOOGLE_API_KEY,
    CUSTOM_HEADER,
    NONE
}

/** Opaque secret handle. It never contains the secret value. */
@JvmInline
value class AiCredentialReference(val value: String) {
    init {
        require(value.length in 1..MAX_REFERENCE_LENGTH)
        require(value.all { it.isLetterOrDigit() || it in "._:-/" })
    }

    companion object {
        private const val MAX_REFERENCE_LENGTH = 256
    }
}

/**
 * A concrete network/local connection. Credentials are referenced, never
 * embedded. Model selection is intentionally not part of this type.
 */
data class AiConnectionProfile(
    val id: String,
    val providerKind: AiProviderKind,
    val dialect: AiApiDialect,
    val baseUrl: String,
    val local: Boolean,
    val credentialRef: AiCredentialReference? = null,
    val enabled: Boolean = true
) {
    init {
        require(id.length in 1..MAX_ID_LENGTH)
        require(baseUrl.length in 8..MAX_BASE_URL_LENGTH)
    }

    companion object {
        const val MAX_ID_LENGTH = 128
        const val MAX_BASE_URL_LENGTH = 2048
    }
}

/** Model identity/capability metadata independent from connection secrets. */
data class AiModelDescriptorV2(
    val id: String,
    val connectionId: String,
    val providerKind: AiProviderKind,
    val dialect: AiApiDialect,
    val contextWindowTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val capabilities: AiProviderCapabilities = AiProviderCapabilities()
) {
    init {
        require(id.length in 1..MAX_MODEL_ID_LENGTH)
        require(connectionId.length in 1..AiConnectionProfile.MAX_ID_LENGTH)
        require(contextWindowTokens == null || contextWindowTokens > 0)
        require(maxOutputTokens == null || maxOutputTokens > 0)
    }

    companion object {
        const val MAX_MODEL_ID_LENGTH = 256
    }
}

/**
 * Stable provider metadata. Endpoints and credentials are connection concerns,
 * while concrete model capabilities belong to [AiModelDescriptorV2].
 */
data class AiProviderDefinition(
    val id: String,
    val displayName: String,
    val kind: AiProviderKind,
    val supportedDialects: Set<AiApiDialect>,
    val defaultDialect: AiApiDialect,
    val authSchemes: Set<AiAuthScheme>,
    val supportsCustomEndpoint: Boolean
) {
    init {
        require(id.length in 1..128)
        require(displayName.isNotBlank())
        require(supportedDialects.isNotEmpty())
        require(defaultDialect in supportedDialects)
        require(authSchemes.isNotEmpty())
    }
}
