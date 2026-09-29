package com.nexaflow.feature.builder

/**
 * Pure projection used by [AppPickerDialog].
 *
 * Keeping filtering/recents outside composition makes the 500+ app path
 * testable and lets Compose memoize one projection per search/filter change
 * instead of rebuilding three lists on unrelated recompositions.
 */
internal data class AppPickerProjection(
    val filteredCount: Int,
    val recentApps: List<InstalledApp>,
    val listApps: List<InstalledApp>,
)

internal fun projectAppPickerApps(
    allApps: List<InstalledApp>,
    query: String,
    showSystem: Boolean,
    searchByPackage: Boolean,
    recentPackages: List<String>,
    recentsLimit: Int = 6,
): AppPickerProjection {
    require(recentsLimit >= 0) { "recentsLimit must be non-negative" }

    val normalizedQuery = query.trim()
    val filtered = allApps.filter { app ->
        (showSystem || !app.isSystemApp) &&
            (
                normalizedQuery.isBlank() ||
                    if (searchByPackage) {
                        app.packageName.contains(normalizedQuery, ignoreCase = true)
                    } else {
                        app.label.contains(normalizedQuery, ignoreCase = true) ||
                            app.packageName.contains(normalizedQuery, ignoreCase = true)
                    }
                )
    }

    val recentApps = if (normalizedQuery.isBlank() && recentsLimit > 0) {
        val byPackage = allApps.associateBy { it.packageName }
        recentPackages.asSequence()
            .mapNotNull(byPackage::get)
            .filter { app -> showSystem || !app.isSystemApp }
            .distinctBy { it.packageName }
            .take(recentsLimit)
            .toList()
    } else {
        emptyList()
    }
    val recentPackagesSet = recentApps.mapTo(hashSetOf()) { it.packageName }
    val listApps = filtered.filterNot { it.packageName in recentPackagesSet }

    return AppPickerProjection(
        filteredCount = filtered.size,
        recentApps = recentApps,
        listApps = listApps,
    )
}
