package com.nexaflow.domain.models

/**
 * Stable presentation categories inferred from typed action results. UI layers
 * must translate these categories and never claim a Google Play update was
 * installed when the platform exposed no update-discovery capability.
 */
enum class ExecutionResultClassification {
    GOOGLE_PLAY_UPDATES_NOT_EXPOSED,
    MANAGED_GOOGLE_PLAY_POLICY_REQUIRED
}

object ExecutionResultClassifier {

    fun classify(record: ExecutionRecord): ExecutionResultClassification? {
        val updateResult = record.actionResults.firstOrNull {
            it.actionType == ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS.name
        } ?: return null
        return when {
            updateResult.message.contains(MANAGED_POLICY_MARKER) ->
                ExecutionResultClassification.MANAGED_GOOGLE_PLAY_POLICY_REQUIRED
            updateResult.message.contains(DISCOVERY_NOT_EXPOSED_MARKER) ||
                updateResult.message.contains(LEGACY_DISCOVERY_NOT_EXPOSED_MARKER) ->
                ExecutionResultClassification.GOOGLE_PLAY_UPDATES_NOT_EXPOSED
            else -> null
        }
    }

    const val MANAGED_POLICY_MARKER = "GOOGLE_PLAY_MANAGED_POLICY_REQUIRED"
    const val DISCOVERY_NOT_EXPOSED_MARKER = "GOOGLE_PLAY_DISCOVERY_NOT_EXPOSED"
    private const val LEGACY_DISCOVERY_NOT_EXPOSED_MARKER =
        "Google Play update discovery is not exposed"
}
