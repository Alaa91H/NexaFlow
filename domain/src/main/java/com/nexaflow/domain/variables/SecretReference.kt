package com.nexaflow.domain.variables

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Opaque pointer to a value held by SecureStorage. Workflow definitions, logs,
 * exports and plugin bundles may carry this reference but never the secret.
 */
@Immutable
@Serializable
data class SecretReference(
    val key: String,
    val purpose: String = "workflow"
) {
    init {
        require(key.matches(KEY_PATTERN)) { "Secret reference key has an invalid format" }
        require(purpose.matches(PURPOSE_PATTERN)) { "Secret reference purpose has an invalid format" }
    }

    companion object {
        private val KEY_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
        private val PURPOSE_PATTERN = Regex("[a-z][a-z0-9_-]{0,31}")
    }
}
