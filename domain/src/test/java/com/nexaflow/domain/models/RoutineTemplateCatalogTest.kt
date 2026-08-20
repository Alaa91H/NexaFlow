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
    fun maintenanceTemplates_haveTypedProfilesAndSupportedActions() {
        val app = RoutineTemplateCatalog.find(RoutineTemplateCatalog.DAILY_APP_MAINTENANCE)
        assertEquals(MaintenanceKind.APP, app?.maintenanceProfile?.kind)
        assertEquals("02:00", app?.maintenanceProfile?.window?.startTime)
        assertEquals(ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS, app?.actions?.single()?.type)

        val storage = RoutineTemplateCatalog.find(RoutineTemplateCatalog.WEEKLY_STORAGE_CLEANUP)
        assertEquals(MaintenanceKind.STORAGE, storage?.maintenanceProfile?.kind)
        assertEquals(setOf(6, 7), storage?.maintenanceProfile?.window?.allowedDays)
        assertEquals(ActionType.SYSTEM_OPEN_SETTINGS, storage?.actions?.single()?.type)
        assertEquals("STORAGE", storage?.actions?.single()?.config?.get("page"))

        val automation = RoutineTemplateCatalog.find(RoutineTemplateCatalog.NIGHTLY_AUTOMATION_SYNC)
        assertEquals(MaintenanceKind.AUTOMATION, automation?.maintenanceProfile?.kind)
        assertEquals("05:00", automation?.maintenanceProfile?.window?.endTime)
        assertEquals(ActionType.SYSTEM_SEND_NOTIFICATION, automation?.actions?.single()?.type)
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
