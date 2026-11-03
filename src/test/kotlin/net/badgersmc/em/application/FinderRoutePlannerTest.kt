package net.badgersmc.em.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinderRoutePlannerTest {
    private fun point(x: Double, z: Double) = FinderTrailTracker.Point(x, 64.0, z)
    private val nodes = listOf(
        FinderRoutePlanner.Node("a", point(0.0, 0.0), listOf("b")),
        FinderRoutePlanner.Node("b", point(0.0, 20.0), listOf("c")),
        FinderRoutePlanner.Node("c", point(20.0, 20.0), emptyList()),
    )

    @Test fun `linked walkways route around a building in either direction`() {
        assertEquals(nodes.map { it.point }, FinderRoutePlanner.plan(nodes, point(0.0, -1.0), point(21.0, 20.0), 4.0))
        assertEquals(nodes.reversed().map { it.point }, FinderRoutePlanner.plan(nodes, point(21.0, 20.0), point(0.0, -1.0), 4.0))
    }

    @Test fun `disconnected distant invalid and oversized graphs fail safely`() {
        assertTrue(FinderRoutePlanner.plan(nodes.map { it.copy(links = emptyList()) }, point(0.0, 0.0), point(20.0, 20.0), 4.0).isEmpty())
        assertTrue(FinderRoutePlanner.plan(nodes, point(-100.0, 0.0), point(20.0, 20.0), 4.0).isEmpty())
        assertTrue(FinderRoutePlanner.plan(nodes + nodes, point(0.0, 0.0), point(20.0, 20.0), 4.0).isEmpty())
        assertTrue(FinderRoutePlanner.plan((0..128).map { FinderRoutePlanner.Node("$it", point(it.toDouble(), 0.0), emptyList()) }, point(0.0, 0.0), point(20.0, 0.0), 4.0).isEmpty())
    }
}
