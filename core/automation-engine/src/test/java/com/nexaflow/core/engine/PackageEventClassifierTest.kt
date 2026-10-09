package com.nexaflow.core.engine

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class PackageEventClassifierTest {

    @Test
    fun `package broadcasts classify install removal and replacement exactly once`() {
        val cases = listOf(
            Case(Intent.ACTION_PACKAGE_ADDED, replacing = false, PackageTriggerEvent.INSTALLED),
            Case(Intent.ACTION_PACKAGE_ADDED, replacing = true, PackageTriggerEvent.UPDATED),
            Case(Intent.ACTION_PACKAGE_REMOVED, replacing = false, PackageTriggerEvent.REMOVED),
            Case(Intent.ACTION_PACKAGE_REMOVED, replacing = true, expected = null),
            Case("com.nexaflow.UNRELATED", replacing = false, expected = null),
            Case(null, replacing = false, expected = null)
        )

        cases.forEach { case ->
            assertEquals(
                "action=${case.action} replacing=${case.replacing}",
                case.expected,
                PackageEventClassifier.classify(case.action, case.replacing)
            )
        }
    }

    private data class Case(
        val action: String?,
        val replacing: Boolean,
        val expected: PackageTriggerEvent? = null
    )
}
