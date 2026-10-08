package net.badgersmc.em.application

import java.time.Instant
import java.util.UUID
import kotlin.math.sqrt

/** One server-thread direction guide per player; no platform, terrain or repository work. */
class FinderTrailTracker {
    data class Point(val x: Double, val y: Double, val z: Double) {
        fun distance(other: Point): Double = sqrt(
            (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y) + (z - other.z) * (z - other.z))
    }
    data class Trail(val world: String, val target: Point, val expiresAt: Instant)
    private val active = linkedMapOf<UUID, Trail>()
    private var offset = 0

    fun start(player: UUID, trail: Trail, maxActive: Int): Boolean {
        if (player !in active && active.size >= maxActive) return false
        active[player] = trail
        return true
    }

    fun stop(player: UUID): Boolean = active.remove(player) != null
    fun clear() { active.clear() }
    fun count(): Int = active.size

    fun render(
        now: Instant, maxParticles: Int, maxRange: Double,
        position: (UUID) -> Pair<String, Point>?,
    ): Map<UUID, List<Point>> {
        val plans = linkedMapOf<UUID, List<Point>>()
        val players = active.keys.toList()
        if (players.isEmpty()) return plans
        val ordered = order(players)
        offset = (offset + 1) % players.size
        var budget = maxParticles.coerceAtLeast(0)
        for (player in ordered) {
            val trail = active.getValue(player)
            val from = origin(trail, now, maxRange, position(player))
            if (from == null) { stop(player); continue }
            val plan = plan(from, trail.target, budget)
            plans[player] = plan
            budget -= plan.size
        }
        return plans
    }

    private fun order(players: List<UUID>): List<UUID> = players.drop(offset % players.size) + players.take(offset % players.size)

    private fun plan(from: Point, target: Point, budget: Int): List<Point> {
        val distance = from.distance(target)
        val count = minOf(budget, POINTS_PER_PLAYER, distance.toInt())
        return points(from, target, count, distance)
    }

    private fun origin(
        trail: Trail, now: Instant, maxRange: Double,
        location: Pair<String, Point>?,
    ): Point? {
        if (location == null) return null
        if (location.first != trail.world || !now.isBefore(trail.expiresAt)) return null
        val distance = location.second.distance(trail.target)
        return location.second.takeIf { validDistance(distance, maxRange) }
    }

    private fun validDistance(distance: Double, maxRange: Double): Boolean =
        distance.isFinite() && distance > ARRIVAL_RADIUS && distance <= maxRange

    private fun points(from: Point, target: Point, count: Int, distance: Double): List<Point> {
        return (1..count).map { step ->
            val ratio = step / distance
            Point(from.x + (target.x - from.x) * ratio,
                from.y + (target.y - from.y) * ratio, from.z + (target.z - from.z) * ratio)
        }
    }

    companion object {
        private const val POINTS_PER_PLAYER = 12
        private const val ARRIVAL_RADIUS = 3.0
    }
}
