package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.RegionProvider
import java.time.Instant
import java.util.UUID
import kotlin.math.sqrt

/** One server-thread destination per player; no terrain, repository or platform work. */
class FinderTrailTracker {
    data class Point(val x: Double, val y: Double, val z: Double) {
        fun distance(other: Point): Double = sqrt(
            (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y) + (z - other.z) * (z - other.z))
    }
    data class Trail(val world: String, val target: Point, val expiresAt: Instant,
                     val footprint: RegionProvider.Footprint? = null)
    data class Frame(val direction: List<Point>, val outline: List<Point>)
    private data class Active(val trail: Trail, var arrivedAt: Instant? = null)
    private val active = linkedMapOf<UUID, Active>()
    private var offset = 0

    fun start(player: UUID, trail: Trail, maxActive: Int): Boolean {
        if (player !in active && active.size >= maxActive) return false
        active[player] = Active(trail)
        return true
    }
    fun stop(player: UUID): Boolean = active.remove(player) != null
    fun clear() { active.clear() }
    fun count(): Int = active.size

    /** Existing direction-only callers retain immediate arrival termination. */
    fun render(now: Instant, maxParticles: Int, maxRange: Double,
               position: (UUID) -> Pair<String, Point>?): Map<UUID, List<Point>> =
        renderFrames(now, maxParticles, maxRange, FinderOutlineOptions(enabled = false), position)
            .mapValues { it.value.direction }

    fun renderFrames(now: Instant, budget: Int, range: Double, options: FinderOutlineOptions,
                     position: (UUID) -> Pair<String, Point>?): Map<UUID, Frame> {
        val plans = linkedMapOf<UUID, Frame>()
        val players = active.keys.toList()
        if (players.isEmpty()) return plans
        val ordered = players.drop(offset % players.size) + players.take(offset % players.size)
        offset = (offset + 1) % players.size
        var remaining = budget.coerceAtLeast(0)
        for (player in ordered) {
            val entry = active.getValue(player)
            val origin = origin(entry, now, range, options.normalized(), position(player))
            if (origin == null) { stop(player); continue }
            val frame = frame(entry, origin, options.normalized(), remaining)
            plans[player] = frame
            remaining -= frame.direction.size + frame.outline.size
        }
        return plans
    }

    private fun origin(entry: Active, now: Instant, range: Double, options: FinderOutlineOptions,
                       location: Pair<String, Point>?): Point? {
        if (location == null || location.first != entry.trail.world) return null
        val distance = location.second.distance(entry.trail.target)
        if (!distance.isFinite() || distance > range) return null
        if (entry.arrivedAt == null && !now.isBefore(entry.trail.expiresAt)) return null
        if (distance <= ARRIVAL_RADIUS && entry.arrivedAt == null) {
            if (!canLinger(entry, options)) return null
            entry.arrivedAt = now
        }
        val arrival = entry.arrivedAt
        if (arrival != null && (!canLinger(entry, options) || !now.isBefore(arrival.plusSeconds(options.arrivalSeconds)))) return null
        return location.second
    }

    private fun canLinger(entry: Active, options: FinderOutlineOptions): Boolean =
        options.enabled && options.arrivalSeconds > 0 &&
            entry.trail.footprint?.let(FinderOutlinePlanner::valid) == true

    private fun frame(entry: Active, from: Point, options: FinderOutlineOptions, budget: Int): Frame {
        val trail = entry.trail
        val distance = from.distance(trail.target)
        val direction = if (entry.arrivedAt == null) direction(from, trail.target, budget, distance) else emptyList()
        val shape = trail.footprint
        val outline = if (shape != null && distance <= options.revealDistance) {
            FinderOutlinePlanner.plan(shape, trail.target, options, budget - direction.size)
        } else emptyList()
        return Frame(direction, outline)
    }

    private fun direction(from: Point, target: Point, budget: Int, distance: Double): List<Point> {
        val count = minOf(budget, POINTS_PER_PLAYER, distance.toInt())
        return (1..count).map { step ->
            val ratio = step / distance
            Point(from.x + (target.x - from.x) * ratio, from.y + (target.y - from.y) * ratio,
                  from.z + (target.z - from.z) * ratio)
        }
    }
    companion object {
        private const val POINTS_PER_PLAYER = 12
        private const val ARRIVAL_RADIUS = 3.0
    }
}
