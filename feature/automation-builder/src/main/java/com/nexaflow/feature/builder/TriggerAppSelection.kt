package com.nexaflow.feature.builder

import com.nexaflow.domain.models.TriggerType

/** Applies one selected package using the storage shape owned by the trigger type. */
internal fun TriggerDraft.withPickedPackage(packageName: String): TriggerDraft {
    val selectedPackage = packageName.trim()
    if (selectedPackage.isEmpty()) return this

    return when (type) {
        TriggerType.APPLICATION -> {
            val existingPackages = (config["packages"] ?: config["package"] ?: "")
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
            val packages = (existingPackages + selectedPackage).distinct()
            copy(config = config + ("packages" to packages.joinToString(",")))
        }
        TriggerType.APP_INSTALLED -> copy(config = config + ("package" to selectedPackage))
        else -> this
    }
}
