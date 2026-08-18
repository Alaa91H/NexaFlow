package com.nexaflow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTemplateCatalogTest {

    @Test
    fun everyTemplate_hasAtLeastOneTriggerAndAction() {
        assertTrue(RoutineTemplateCatalog.all.isNotEmpty())
        RoutineTemplateCatalog.all.forEach { template ->
            assertTrue(template.triggers.isNotEmpty())
            assertTrue(template.actions.isNotEmpty())
        }
    }

    @Test
    fun knownIds_resolveToTheExpectedTemplates() {
        assertEquals(
            RoutineTemplateCatalog.SLEEP,
            RoutineTemplateCatalog.find(RoutineTemplateCatalog.SLEEP)?.id
        )
        assertEquals(
            RoutineTemplateCatalog.LOW_BATTERY,
            RoutineTemplateCatalog.find(RoutineTemplateCatalog.LOW_BATTERY)?.id
        )
        assertEquals(
            RoutineTemplateCatalog.CHARGING,
            RoutineTemplateCatalog.find(RoutineTemplateCatalog.CHARGING)?.id
        )
    }

    @Test
    fun unknownOrMissingTemplateIds_doNotCreateAPresetDraft() {
        assertNull(RoutineTemplateCatalog.find(null))
        assertNull(RoutineTemplateCatalog.find("not-a-template"))
    }

    @Test
    fun templateIds_areUnique() {
        val ids = RoutineTemplateCatalog.all.map(RoutineTemplate::id)
        assertEquals(ids.size, ids.toSet().size)
        assertNotNull(RoutineTemplateCatalog.find(ids.first()))
    }
}
