package com.nexaflow.domain.export

import com.nexaflow.domain.models.Automation

/**
 * Serializes one automation into the shareable `.nexaflow` JSON document.
 *
 * The dashboard shares a single task, but the serializer lives in the data
 * layer because it owns the backup schema. This port keeps the feature modules
 * from depending on `:data` (NFA-07) without duplicating the format.
 */
fun interface AutomationExporter {
    fun exportSingle(automation: Automation): String
}
