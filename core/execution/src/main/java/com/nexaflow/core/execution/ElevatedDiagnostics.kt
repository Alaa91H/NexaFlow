package com.nexaflow.core.execution

import com.nexaflow.domain.models.Action

/**
 * Builds diagnostic lines for elevated-action failures.
 *
 * P0.1 trust boundary: config VALUES must never reach a log sink — they can
 * carry shell commands, tokens, or package data. Only the action type and the
 * config KEY NAMES are included; the redacting log store handles credential
 * shapes everywhere else. Keeping the format in one function lets tests pin
 * the real production output.
 */
internal object ElevatedDiagnostics {
    fun failureLine(action: Action, resultMessage: String): String =
        "elevated action ${action.type} failed: $resultMessage | action=${action.type} " +
            "configKeys=${action.config.keys.joinToString(",")}"
}
