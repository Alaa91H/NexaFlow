package com.nexaflow.feature.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.nexaflow.domain.models.RoutineTemplateCatalog

class StarterRoutinePolicyTest {

    @Test
    fun `starter routine is disabled on its first admissible save`() {
        assertFalse(
            resolvedSavedEnabled(
                previousEnabled = null,
                admissible = true,
                startDisabled = true
            )
        )
    }

    @Test
    fun `manual routine keeps its first admissible save enabled`() {
        assertTrue(
            resolvedSavedEnabled(
                previousEnabled = null,
                admissible = true,
                startDisabled = false
            )
        )
    }

    @Test
    fun `existing routine retains its saved toggle regardless of starter flag`() {
        assertTrue(
            resolvedSavedEnabled(
                previousEnabled = true,
                admissible = true,
                startDisabled = true
            )
        )
        assertFalse(
            resolvedSavedEnabled(
                previousEnabled = false,
                admissible = true,
                startDisabled = false
            )
        )
    }

    @Test
    fun `inadmissible routine can never be enabled`() {
        assertFalse(
            resolvedSavedEnabled(
                previousEnabled = true,
                admissible = false,
                startDisabled = false
            )
        )
    }

    @Test
    fun `every bundled starter routine has a localized title resource`() {
        RoutineTemplateCatalog.all.forEach { template ->
            assertTrue(starterRoutineTitleRes(template.id) != R.string.builder_title)
        }
    }

    @Test
    fun `unknown starter routine falls back to the builder title`() {
        assertEquals(R.string.builder_title, starterRoutineTitleRes("unknown"))
    }
}
