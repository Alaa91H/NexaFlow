package com.nexaflow.domain.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KahnTopologicalSortTest {

    @Test
    fun `empty graph yields an empty order`() {
        val order = kahnTopologicalSort(emptyMap())
        assertTrue(order != null && order.isEmpty())
    }

    @Test
    fun `single node yields itself`() {
        assertEquals(listOf("A"), kahnTopologicalSort(mapOf("A" to emptySet())))
    }

    @Test
    fun `linear chain preserves the order`() {
        val edges = mapOf(
            "A" to setOf("B"),
            "B" to setOf("C"),
            "C" to setOf("D"),
            "D" to emptySet(),
        )
        assertEquals(listOf("A", "B", "C", "D"), kahnTopologicalSort(edges))
    }

    @Test
    fun `diamond graph is acyclic and every node appears exactly once`() {
        val edges = mapOf(
            "A" to setOf("B", "C"),
            "B" to setOf("D"),
            "C" to setOf("D"),
            "D" to emptySet(),
        )
        val order = kahnTopologicalSort(edges)
        assertEquals(4, order?.size)
        // A must precede both branches, and both branches must precede D.
        val index = order!!.withIndex().associate { it.value to it.index }
        assertEquals(0, index["A"])
        assertEquals(3, index["D"])
        assert(index["B"]!! < index["D"]!!)
        assert(index["C"]!! < index["D"]!!)
        assert(index["A"]!! < index["B"]!!)
        assert(index["A"]!! < index["C"]!!)
    }

    @Test
    fun `simple cycle returns null`() {
        val edges = mapOf(
            "A" to setOf("B"),
            "B" to setOf("C"),
            "C" to setOf("A"),
        )
        assertNull(kahnTopologicalSort(edges))
    }

    @Test
    fun `self loop returns null`() {
        val edges = mapOf("A" to setOf("A"))
        assertNull(kahnTopologicalSort(edges))
    }

    @Test
    fun `cycle hidden behind a valid prefix returns null`() {
        val edges = mapOf(
            "A" to setOf("B"),
            "B" to setOf("C"),
            "C" to setOf("D"),
            "D" to setOf("B"), // B → C → D → B cycle
        )
        assertNull(kahnTopologicalSort(edges))
    }

    @Test
    fun `disconnected components are both covered`() {
        val edges = mapOf(
            "A" to emptySet(),
            "B" to emptySet(),
            "C" to setOf("D"),
            "D" to emptySet(),
        )
        val order = kahnTopologicalSort(edges)
        assertEquals(4, order?.size)
        assert(order!!.containsAll(listOf("A", "B", "C", "D")))
        assert(order.indexOf("C") < order.indexOf("D"))
    }

    @Test
    fun `multiple entry nodes all appear before their successors`() {
        val edges = mapOf(
            "A" to setOf("C"),
            "B" to setOf("C"),
            "C" to emptySet(),
        )
        val order = kahnTopologicalSort(edges)
        assertEquals(listOf("A", "B", "C"), order)
    }

    @Test
    fun `successor pointing at a node not in the key set is ignored safely`() {
        // Defensive: an edge to an unknown node must not crash or corrupt the count.
        val edges = mapOf(
            "A" to setOf("GHOST"),
            "B" to setOf("A"),
        )
        val order = kahnTopologicalSort(edges)
        assertEquals(listOf("B", "A"), order)
    }
}
