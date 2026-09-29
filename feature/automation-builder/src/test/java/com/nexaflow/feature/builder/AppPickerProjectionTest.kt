package com.nexaflow.feature.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerProjectionTest {

    private fun apps(count: Int): List<InstalledApp> = (0 until count).map { index ->
        InstalledApp(
            label = "App %04d".format(index),
            packageName = "com.example.app$index",
            isSystemApp = index % 5 == 0,
        )
    }

    @Test
    fun thousandAppProjectionPreservesAllItemsWithoutSearch() {
        val all = apps(1_000)
        val projection = projectAppPickerApps(
            allApps = all,
            query = "",
            showSystem = true,
            searchByPackage = false,
            recentPackages = emptyList(),
        )

        assertEquals(1_000, projection.filteredCount)
        assertEquals(1_000, projection.listApps.size)
        assertTrue(projection.recentApps.isEmpty())
        // Projection reuses immutable metadata objects; it does not duplicate
        // app payloads or eagerly materialize icon bitmaps.
        assertSame(all[500], projection.listApps[500])
    }

    @Test
    fun recentsStayBoundedAndAreRemovedFromMainListAtLargeScale() {
        val all = apps(800)
        val recents = (799 downTo 780).map { "com.example.app$it" }

        val projection = projectAppPickerApps(
            allApps = all,
            query = "",
            showSystem = true,
            searchByPackage = false,
            recentPackages = recents,
        )

        assertEquals(6, projection.recentApps.size)
        assertEquals(
            recents.take(6),
            projection.recentApps.map { it.packageName },
        )
        assertEquals(794, projection.listApps.size)
        val mainPackages = projection.listApps.mapTo(hashSetOf()) { it.packageName }
        assertTrue(projection.recentApps.none { it.packageName in mainPackages })
    }

    @Test
    fun packageSearchOverThousandAppsIsExactAndDoesNotLeakSystemFilter() {
        val all = apps(1_000)

        val projection = projectAppPickerApps(
            allApps = all,
            query = "com.example.app777",
            showSystem = false,
            searchByPackage = true,
            recentPackages = emptyList(),
        )

        assertEquals(1, projection.filteredCount)
        assertEquals("com.example.app777", projection.listApps.single().packageName)
        assertFalse(projection.listApps.single().isSystemApp)
    }

    @Test
    fun querySuppressesRecentsAndKeepsStableSourceOrder() {
        val all = apps(650)
        val projection = projectAppPickerApps(
            allApps = all,
            query = "App 01",
            showSystem = true,
            searchByPackage = false,
            recentPackages = listOf("com.example.app100", "com.example.app101"),
        )

        assertTrue(projection.recentApps.isEmpty())
        assertTrue(projection.listApps.isNotEmpty())
        assertEquals(
            projection.listApps.sortedBy { all.indexOf(it) },
            projection.listApps,
        )
    }
}
