package com.nexaflow.core.airuntime

enum class AiCapabilityEvidenceSource {
    STATIC,
    PROVIDER_METADATA,
    PROBE
}

data class AiCapabilityEvidence(
    val toolCalling: Boolean? = null,
    val structuredOutput: Boolean? = null,
    val streaming: Boolean? = null,
    val vision: Boolean? = null,
    val reasoning: Boolean? = null,
    val contextWindowTokens: Int? = null,
    val maxOutputTokens: Int? = null
) {
    init {
        require(contextWindowTokens == null || contextWindowTokens > 0)
        require(maxOutputTokens == null || maxOutputTokens > 0)
    }

    fun overlay(higherPriority: AiCapabilityEvidence?): AiCapabilityEvidence {
        if (higherPriority == null) return this
        return AiCapabilityEvidence(
            toolCalling = higherPriority.toolCalling ?: toolCalling,
            structuredOutput = higherPriority.structuredOutput ?: structuredOutput,
            streaming = higherPriority.streaming ?: streaming,
            vision = higherPriority.vision ?: vision,
            reasoning = higherPriority.reasoning ?: reasoning,
            contextWindowTokens = higherPriority.contextWindowTokens ?: contextWindowTokens,
            maxOutputTokens = higherPriority.maxOutputTokens ?: maxOutputTokens
        )
    }

    fun isEmpty(): Boolean =
        toolCalling == null &&
            structuredOutput == null &&
            streaming == null &&
            vision == null &&
            reasoning == null &&
            contextWindowTokens == null &&
            maxOutputTokens == null

    fun toCapabilities(local: Boolean): AiProviderCapabilities =
        AiProviderCapabilities(
            toolCalling = toolCalling ?: false,
            structuredOutput = structuredOutput ?: false,
            streaming = streaming ?: false,
            vision = vision ?: false,
            reasoning = reasoning ?: false,
            contextTokens = contextWindowTokens,
            local = local
        )

    companion object {
        /**
         * Existing adapter probes expose booleans rather than tri-state support.
         * Treat only affirmative probe results as evidence so an untested/false
         * field cannot erase stronger static or provider metadata.
         */
        fun fromCapabilities(
            capabilities: AiProviderCapabilities
        ): AiCapabilityEvidence =
            AiCapabilityEvidence(
                toolCalling = capabilities.toolCalling.takeIf { it },
                structuredOutput = capabilities.structuredOutput.takeIf { it },
                streaming = capabilities.streaming.takeIf { it },
                vision = capabilities.vision.takeIf { it },
                reasoning = capabilities.reasoning.takeIf { it },
                contextWindowTokens = capabilities.contextTokens
            )
    }
}

data class AiModelCapabilitySnapshot(
    val descriptor: AiModelDescriptorV2,
    val providerRevision: String,
    val testedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val sources: Set<AiCapabilityEvidenceSource>
) {
    fun isExpired(nowEpochMillis: Long): Boolean =
        nowEpochMillis >= expiresAtEpochMillis
}

class AiModelRegistry(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis
) {
    init {
        require(ttlMillis in 1..MAX_TTL_MILLIS)
        require(maxEntries in 1..MAX_ALLOWED_ENTRIES)
    }

    private data class CacheKey(
        val connectionId: String,
        val modelId: String,
        val providerRevision: String
    )

    private val entries = LinkedHashMap<CacheKey, AiModelCapabilitySnapshot>(
        INITIAL_CAPACITY,
        LOAD_FACTOR,
        true
    )

    @Synchronized
    fun cached(
        connectionId: String,
        modelId: String,
        providerRevision: String
    ): AiModelCapabilitySnapshot? {
        val now = clock()
        purgeExpiredLocked(now)
        return entries[CacheKey(connectionId, modelId, providerRevision)]
    }

    suspend fun resolve(
        connection: AiConnectionProfile,
        discoveredModel: AiDiscoveredModel,
        providerRevision: String,
        staticEvidence: AiCapabilityEvidence = AiCapabilityEvidence(),
        providerMetadataEvidence: AiCapabilityEvidence? = null,
        adapter: AiProviderAdapter? = null,
        probe: Boolean = false
    ): AiModelCapabilitySnapshot {
        require(providerRevision.length in 1..MAX_REVISION_LENGTH)
        require(discoveredModel.id.length in 1..AiModelDescriptorV2.MAX_MODEL_ID_LENGTH)

        cached(
            connectionId = connection.id,
            modelId = discoveredModel.id,
            providerRevision = providerRevision
        )?.let { return it }

        val sources = linkedSetOf<AiCapabilityEvidenceSource>()
        var effective = staticEvidence
        if (!staticEvidence.isEmpty()) {
            sources += AiCapabilityEvidenceSource.STATIC
        }

        val discoveredMetadata = AiCapabilityEvidence(
            contextWindowTokens = discoveredModel.contextTokens
        )
        val metadata = discoveredMetadata.overlay(providerMetadataEvidence)
        if (!metadata.isEmpty()) {
            effective = effective.overlay(metadata)
            sources += AiCapabilityEvidenceSource.PROVIDER_METADATA
        }

        if (probe && adapter != null) {
            val probeInput = descriptor(
                connection = connection,
                modelId = discoveredModel.id,
                evidence = effective
            )
            val result = adapter.discoverCapabilities(probeInput)
            if (result.success) {
                effective = effective.overlay(
                    AiCapabilityEvidence.fromCapabilities(result.capabilities)
                )
                sources += AiCapabilityEvidenceSource.PROBE
            }
        }

        val now = clock()
        val snapshot = AiModelCapabilitySnapshot(
            descriptor = descriptor(
                connection = connection,
                modelId = discoveredModel.id,
                evidence = effective
            ),
            providerRevision = providerRevision,
            testedAtEpochMillis = now,
            expiresAtEpochMillis = safeExpiry(now),
            sources = sources
        )
        remember(snapshot)
        return snapshot
    }

    @Synchronized
    fun invalidateConnection(connectionId: String): Int {
        val before = entries.size
        entries.keys.removeAll { it.connectionId == connectionId }
        return before - entries.size
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int {
        purgeExpiredLocked(clock())
        return entries.size
    }

    @Synchronized
    private fun remember(snapshot: AiModelCapabilitySnapshot) {
        val descriptor = snapshot.descriptor
        entries[
            CacheKey(
                connectionId = descriptor.connectionId,
                modelId = descriptor.id,
                providerRevision = snapshot.providerRevision
            )
        ] = snapshot
        while (entries.size > maxEntries) {
            val eldest = entries.entries.iterator()
            if (!eldest.hasNext()) break
            eldest.next()
            eldest.remove()
        }
    }

    private fun descriptor(
        connection: AiConnectionProfile,
        modelId: String,
        evidence: AiCapabilityEvidence
    ): AiModelDescriptorV2 =
        AiModelDescriptorV2(
            id = modelId,
            connectionId = connection.id,
            providerKind = connection.providerKind,
            dialect = connection.dialect,
            contextWindowTokens = evidence.contextWindowTokens,
            maxOutputTokens = evidence.maxOutputTokens,
            capabilities = evidence.toCapabilities(local = connection.local)
        )

    private fun safeExpiry(now: Long): Long =
        if (Long.MAX_VALUE - now < ttlMillis) Long.MAX_VALUE else now + ttlMillis

    private fun purgeExpiredLocked(now: Long) {
        entries.entries.removeAll { (_, snapshot) -> snapshot.isExpired(now) }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
        const val DEFAULT_TTL_MILLIS = 15 * 60 * 1000L
        const val MAX_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES = 512
        const val MAX_ALLOWED_ENTRIES = 2048
        const val MAX_REVISION_LENGTH = 128
    }
}
