package com.nexaflow.feature.builder

import com.nexaflow.domain.models.TriggerType

data class TriggerDraft(
    val type: TriggerType,
    val config: Map<String, String> = emptyMap()
)
