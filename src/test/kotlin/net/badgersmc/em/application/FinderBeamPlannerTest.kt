package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.RegionProvider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FinderBeamPlannerTest {
    private val target = FinderTrailTracker.Point(5.5, 65.5, 5.5)
    private val shape = RegionProvider.Footprint(listOf(RegionProvider.Vertex(0.0, 0.0),
        RegionProvider.Vertex(10.0, 0.0), RegionProvider.Vertex(10.0, 10.0), RegionProvider.Vertex(0.0, 10.0)), 64.0, 80.0)

    @Test fun `complete twelve edge boundary and separate exact shop box`() {
        val beams = FinderBeamPlanner.plan(shape, target, FinderOutlineOptions())
        assertEquals(24, beams.size)
        assertEquals(8, beams.count { it.from.y != it.to.y })
        assertEquals(12, FinderBeamPlanner.plan(shape, target, FinderOutlineOptions(marker = false)).size)
        assertEquals(12, FinderBeamPlanner.plan(null, target, FinderOutlineOptions()).size)
        assertTrue(FinderBeamPlanner.plan(shape, target, FinderOutlineOptions(enabled = false)).isEmpty())
    }

    @Test fun `pulse follows turns and advances over time without exceeding count`() {
        val origin = FinderTrailTracker.Point(0.0, 64.0, 0.0)
        val route = listOf(origin.copy(x = 3.0), origin.copy(x = 3.0, z = 20.0))
        val now = Instant.parse("2026-10-18T00:00:00Z")
        val first = FinderPulsePlanner.plan(origin, route, FinderPulsePlanner.Settings(12, now, 2.0))
        val next = FinderPulsePlanner.plan(origin, route, FinderPulsePlanner.Settings(12, now.plusMillis(500), 2.0))
        assertEquals(12, first.size)
        assertNotEquals(first, next)
        assertTrue(first.intersect(next.toSet()).isEmpty(), "Default cadence must visibly move the pulse, not redraw the same lattice")
        assertTrue(first.all { it.z == 0.0 || it.x == 3.0 })
        assertTrue(FinderPulsePlanner.plan(origin, route, FinderPulsePlanner.Settings(0, now, 2.0)).isEmpty())
    }
}
