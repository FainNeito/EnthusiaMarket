package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.RegionProvider
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinderOutlineTest {
    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val player = UUID.randomUUID()
    private val target = FinderTrailTracker.Point(4.0, 65.0, 4.0)
    private val footprint = RegionProvider.Footprint(listOf(
        RegionProvider.Vertex(0.0, 0.0), RegionProvider.Vertex(8.0, 0.0),
        RegionProvider.Vertex(8.0, 8.0), RegionProvider.Vertex(0.0, 8.0)), -64.0, 320.0)
    private val options = FinderOutlineOptions(marker = false)

    @Test fun `near destination gains outline and arrival retains it for ten seconds`() {
        val tracker = FinderTrailTracker()
        tracker.start(player, FinderTrailTracker.Trail("world", target, now.plusSeconds(60), footprint), 1)
        val near = tracker.renderFrames(now, 200, 256.0, options) { "world" to target.copy(x = 15.0) }
        assertTrue(near.getValue(player).outline.isNotEmpty())
        val arrived = tracker.renderFrames(now.plusSeconds(59), 200, 256.0, options) { "world" to target }
        assertTrue(arrived.getValue(player).direction.isEmpty())
        assertTrue(arrived.getValue(player).outline.isNotEmpty())
        assertEquals(1, tracker.count())
        assertTrue(tracker.renderFrames(now.plusSeconds(68), 200, 256.0, options) { "world" to target }.isNotEmpty())
        assertTrue(tracker.renderFrames(now.plusSeconds(69), 200, 256.0, options) { "world" to target }.isEmpty())
    }

    @Test fun `outline uses polygon edges and clips tall region around shop height`() {
        val triangle = footprint.copy(vertices = footprint.vertices.take(3))
        val points = FinderOutlinePlanner.plan(triangle, target, options, 200)
        assertTrue(points.isNotEmpty())
        assertTrue(points.all { it.y in 64.5..67.5 })
        assertTrue(points.all { it.z == 0.0 || it.x == 8.0 || it.x == it.z })
        assertTrue(points.none { it.x == 0.0 && it.z == 8.0 })
    }

    @Test fun `planner caps huge geometry and zero budgets without unbounded loops`() {
        val huge = footprint.copy(vertices = footprint.vertices.map { it.copy(x = it.x * 1000000, z = it.z * 1000000) })
        assertEquals(7, FinderOutlinePlanner.plan(huge, target, options, 7).size)
        assertTrue(FinderOutlinePlanner.plan(footprint, target, options, 0).isEmpty())
        assertTrue(FinderOutlinePlanner.plan(footprint.copy(minY = Double.NaN), target, options, 200).isEmpty())
    }

    @Test fun `disabled or absent geometry keeps old arrival and direction behavior`() {
        for ((shape, settings) in listOf(null to options, footprint to options.copy(enabled = false),
                                        footprint to options.copy(arrivalSeconds = 0))) {
            val tracker = FinderTrailTracker()
            tracker.start(player, FinderTrailTracker.Trail("world", target, now.plusSeconds(60), shape), 1)
            assertTrue(tracker.renderFrames(now, 200, 256.0, settings) { "world" to target.copy(x = 50.0) }
                .getValue(player).outline.isEmpty())
            assertTrue(tracker.renderFrames(now, 200, 256.0, settings) { "world" to target }.isEmpty())
        }
    }

    @Test fun `combined budgets rotate and replacement clears arrival state`() {
        val tracker = FinderTrailTracker()
        val other = UUID.randomUUID()
        val trail = FinderTrailTracker.Trail("world", target, now.plusSeconds(60), footprint)
        tracker.start(player, trail, 2)
        tracker.start(other, trail, 2)
        val first = tracker.renderFrames(now, 20, 256.0, options) { "world" to target.copy(x = 15.0) }
        val second = tracker.renderFrames(now, 20, 256.0, options) { "world" to target.copy(x = 15.0) }
        assertEquals(20, first.values.sumOf { it.direction.size + it.outline.size })
        assertTrue(second.getValue(other).outline.isNotEmpty())
        tracker.renderFrames(now, 200, 256.0, options) { "world" to target }
        tracker.start(player, trail.copy(target = target.copy(x = 40.0)), 2)
        assertTrue(tracker.renderFrames(now.plusSeconds(1), 200, 256.0, options) { "world" to target }
            .getValue(player).direction.isNotEmpty())
        tracker.clear()
        assertEquals(0, tracker.count())
    }

    @Test fun `arrival cannot restart indefinitely and invalid sessions terminate it`() {
        for (location in listOf(null, "nether" to target, "world" to target.copy(x = -500.0))) {
            val tracker = FinderTrailTracker()
            tracker.start(player, FinderTrailTracker.Trail("world", target, now.plusSeconds(60), footprint), 1)
            tracker.renderFrames(now, 200, 256.0, options) { "world" to target }
            assertTrue(tracker.renderFrames(now.plusSeconds(1), 200, 256.0, options) { location }.isEmpty())
        }
        val tracker = FinderTrailTracker()
        tracker.start(player, FinderTrailTracker.Trail("world", target, now.plusSeconds(60), footprint), 1)
        tracker.renderFrames(now, 200, 256.0, options) { "world" to target }
        tracker.renderFrames(now.plusSeconds(9), 200, 256.0, options) { "world" to target.copy(x = 15.0) }
        assertTrue(tracker.renderFrames(now.plusSeconds(10), 200, 256.0, options) { "world" to target }.isEmpty())
    }

    @Test fun `invalid config is bounded and marker is optional`() {
        val safe = options.copy(revealDistance = Double.NaN, height = Double.POSITIVE_INFINITY,
            spacing = -1.0, arrivalSeconds = Long.MAX_VALUE, maxPoints = Int.MAX_VALUE).normalized()
        assertEquals(24.0, safe.revealDistance)
        assertEquals(3.0, safe.height)
        assertEquals(0.25, safe.spacing)
        assertEquals(60, safe.arrivalSeconds)
        assertEquals(128, safe.maxPoints)
        val marker = FinderOutlinePlanner.plan(footprint, target, options.copy(marker = true), 4)
        assertTrue(marker.all { it.x == target.x && it.z == target.z })
        assertTrue(FinderOutlinePlanner.plan(footprint, target, options.copy(enabled = false), 100).isEmpty())
    }
}
