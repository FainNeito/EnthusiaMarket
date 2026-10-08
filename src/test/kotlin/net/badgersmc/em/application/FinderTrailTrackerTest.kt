package net.badgersmc.em.application

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinderTrailTrackerTest {
    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val tracker = FinderTrailTracker()
    private val player = UUID.randomUUID()
    private val target = FinderTrailTracker.Point(100.0, 64.0, 0.0)
    private val trail = FinderTrailTracker.Trail("world", target, now.plusSeconds(60))
    private val from = "world" to FinderTrailTracker.Point(0.0, 64.0, 0.0)

    @Test fun `selection replaces prior target while limiting active players`() {
        assertTrue(tracker.start(player, trail, 1))
        assertFalse(tracker.start(UUID.randomUUID(), trail, 1))
        assertTrue(tracker.start(player, trail.copy(target = target.copy(x = 50.0)), 1))
        assertEquals(1, tracker.count())
        assertEquals(12, tracker.render(now, 200, 256.0) { from }[player]?.size)
    }

    @Test fun `global particle budget rotates fairly between players`() {
        val other = UUID.randomUUID()
        tracker.start(player, trail, 2)
        tracker.start(other, trail, 2)
        val first = tracker.render(now, 5, 256.0) { from }
        val second = tracker.render(now, 5, 256.0) { from }
        assertEquals(5, first.values.sumOf { it.size })
        assertEquals(5, second.values.sumOf { it.size })
        assertEquals(5, first[player]?.size)
        assertEquals(5, second[other]?.size)
    }

    @Test fun `deadline arrival world change missing player and range terminate guides`() {
        listOf<Pair<Instant, Pair<String, FinderTrailTracker.Point>?>>(
            now.plusSeconds(60) to from,
            now to ("world" to target),
            now to ("nether" to from.second),
            now to null,
            now to ("world" to from.second.copy(x = -500.0)),
        ).forEach { (time, location) ->
            tracker.start(player, trail, 1)
            assertTrue(tracker.render(time, 200, 256.0) { location }.isEmpty())
            assertEquals(0, tracker.count())
        }
    }

    @Test fun `zero budget and cancellation do not generate particles`() {
        tracker.start(player, trail, 1)
        assertEquals(0, tracker.render(now, 0, 256.0) { from }.values.sumOf { it.size })
        assertTrue(tracker.stop(player))
        assertFalse(tracker.stop(player))
        tracker.start(player, trail, 1)
        tracker.clear()
        assertEquals(0, tracker.count())
    }
}
