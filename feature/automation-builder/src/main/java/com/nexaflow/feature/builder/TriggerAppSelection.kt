package com.nexaflow.feature.builder

import com.nexaflow.domain.models.TriggerType

/**
 * Trigger kinds that store the applications they watch.
 *
 * Every one of them is picked from the installed-app sheet; the *storage shape*
 * differs (a single `package` key or a comma-joined `packages` list), which is
 * what [withPickedPackages] owns.
 */
internal val appCapableTriggerTypes: Set<TriggerType> = setOf(
    TriggerType.APPLICATION,
    TriggerType.NOTIFICATION,
    TriggerType.APP_INSTALLED,
)

/** Packages already stored on this draft, whatever key its type uses. */
internal fun TriggerDraft.pickedPackages(): List<String> =
    (config["packages"] ?: config["package"] ?: "")
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)

/** Applies one selected package using the storage shape owned by the trigger type. */
internal fun TriggerDraft.withPickedPackage(packageName: String): TriggerDraft =
    withPickedPackages(listOf(packageName))

/**
 * Applies a set of selected packages using the storage shape owned by the
 * trigger type. Multi-app triggers merge (never replace), so opening the picker
 * again and adding one app keeps the previous selection.
 */
internal fun TriggerDraft.withPickedPackages(packageNames: List<String>): TriggerDraft {
    val selected = packageNames.map(String::trim).filter(String::isNotEmpty)
    if (selected.isEmpty()) return this

    return when (type) {
        TriggerType.APPLICATION,
        TriggerType.NOTIFICATION -> {
            val packages = (pickedPackages() + selected).distinct()
            copy(config = config + ("packages" to packages.joinToString(",")))
        }
        TriggerType.APP_INSTALLED -> copy(config = config + ("package" to selected.first()))
        else -> this
    }
}
