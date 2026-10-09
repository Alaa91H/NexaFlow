package com.nexaflow.feature.builder

import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TriggerAppSelectionTest {

    @Test
    fun `application picker adds a package once and preserves other config`() {
        val draft = TriggerDraft(
            TriggerType.APPLICATION,
            mapOf("packages" to "com.example.one", "legacy" to "kept")
        )

        val selected = draft.withPickedPackage("com.example.one")
        val appended = selected.withPickedPackage("com.example.two")

        assertEquals("com.example.one", selected.config["packages"])
        assertEquals("com.example.one,com.example.two", appended.config["packages"])
        assertEquals("kept", appended.config["legacy"])
    }

    @Test
    fun `app installed picker stores one package and preserves event`() {
        val draft = TriggerDraft(
            TriggerType.APP_INSTALLED,
            mapOf("event" to "UPDATED", "package" to "com.example.old")
        )

        val selected = draft.withPickedPackage("com.example.new")

        assertEquals("com.example.new", selected.config["package"])
        assertEquals("UPDATED", selected.config["event"])
        assertEquals(null, selected.config["packages"])
    }

    @Test
    fun `blank package and unrelated trigger do not mutate draft`() {
        val draft = TriggerDraft(TriggerType.DEVICE, mapOf("event" to "SCREEN_OFF"))

        assertSame(draft, draft.withPickedPackage("  "))
        assertSame(draft, draft.withPickedPackage("com.example.app"))
    }
}
