package com.nexaflow.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearAutomationDtoSerializationTest {

    private val repository = WearSyncRepository()

    @Test
    fun `handleIncomingPayload parses full dto correctly`() {
        val json = """
            [{"id":"a1","name":"Night Mode","icon":"Bedtime","iconColor":-16777216,
              "enabled":true,"lastRunAt":1700000000000,"lastRunSuccess":true,
              "lastRunMessage":"All actions succeeded"}]
        """.trimIndent()

        repository.handleIncomingPayload(json)

        val automations = repository.automations.value.orEmpty()
        assertEquals(1, automations.size)
        val dto = automations[0]
        assertEquals("a1", dto.id)
        assertEquals("Night Mode", dto.name)
        assertEquals("Bedtime", dto.icon)
        assertEquals(-16777216L, dto.iconColor)
        assertTrue(dto.enabled)
        assertEquals(1700000000000L, dto.lastRunAt)
        assertEquals(true, dto.lastRunSuccess)
        assertEquals("All actions succeeded", dto.lastRunMessage)
    }

    @Test
    fun `handleIncomingPayload parses dto with null optional fields`() {
        val json = """
            [{"id":"b2","name":"Alarm","icon":"Alarm","iconColor":0,"enabled":false}]
        """.trimIndent()

        repository.handleIncomingPayload(json)

        val dto = repository.automations.value!![0]
        assertEquals("b2", dto.id)
        assertNull(dto.lastRunAt)
        assertNull(dto.lastRunSuccess)
        assertNull(dto.lastRunMessage)
    }

    @Test
    fun `handleIncomingPayload ignores unknown keys for forward compatibility`() {
        val json = """
            [{"id":"c3","name":"Wi-Fi","icon":"Wifi","iconColor":0,
              "enabled":true,"unknownFutureField":"value","anotherNewField":42}]
        """.trimIndent()

        repository.handleIncomingPayload(json)

        assertEquals(1, repository.automations.value!!.size)
        assertEquals("c3", repository.automations.value!![0].id)
    }

    @Test
    fun `handleIncomingPayload returns empty list for malformed JSON`() {
        repository.handleIncomingPayload("{not valid json}")

        assertTrue(repository.automations.value!!.isEmpty())
    }

    @Test
    fun `handleIncomingPayload returns empty list for empty array`() {
        repository.handleIncomingPayload("[]")

        assertTrue(repository.automations.value!!.isEmpty())
    }

    @Test
    fun `handleIncomingPayload replaces previous state on each call`() {
        val firstPayload = """[{"id":"x","name":"First","icon":"Icon","iconColor":0,"enabled":true}]"""
        val secondPayload = """[{"id":"y","name":"Second","icon":"Icon","iconColor":0,"enabled":false}]"""

        repository.handleIncomingPayload(firstPayload)
        assertEquals(1, repository.automations.value!!.size)
        assertEquals("x", repository.automations.value!![0].id)

        repository.handleIncomingPayload(secondPayload)
        assertEquals(1, repository.automations.value!!.size)
        assertEquals("y", repository.automations.value!![0].id)
    }

    @Test
    fun `handleIncomingPayload parses multiple dtos`() {
        val json = """
            [
              {"id":"1","name":"A","icon":"I","iconColor":0,"enabled":true},
              {"id":"2","name":"B","icon":"I","iconColor":0,"enabled":false},
              {"id":"3","name":"C","icon":"I","iconColor":0,"enabled":true}
            ]
        """.trimIndent()

        repository.handleIncomingPayload(json)

        assertEquals(3, repository.automations.value!!.size)
        assertEquals("1", repository.automations.value!![0].id)
        assertEquals("2", repository.automations.value!![1].id)
        assertEquals("3", repository.automations.value!![2].id)
    }

    @Test
    fun `each accepted payload advances the monotonic update sequence`() {
        assertEquals(0L, repository.updateSequence.value)

        repository.handleIncomingPayload("[]")
        val first = repository.updateSequence.value
        repository.handleIncomingPayload("[]")
        val second = repository.updateSequence.value

        assertTrue(first > 0L)
        assertTrue(second > first)
    }
}
