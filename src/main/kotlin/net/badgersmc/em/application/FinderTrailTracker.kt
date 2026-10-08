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
        val ordered = players.drop(offset % players.size) + players.take(offset % players.size)
        offset = (offset + 1) % players.size
        var budget = maxParticles.coerceAtLeast(0)
        for (player in ordered) {
            val trail = active.getValue(player)
            val from = origin(player, trail, now, maxRange, position(player)) ?: continue
            val distance = from.distance(trail.target)
            val count = minOf(budget, POINTS_PER_PLAYER, distance.toInt())
            plans[player] = points(from, trail.target, count, distance)
            budget -= count
        }
        return plans
    }

    private fun points(from: Point, target: Point, count: Int, distance: Double): List<Point> = (1..count).map { step ->
        val ratio = step / distance
        Point(from.x + (target.x - from.x) * ratio,
            from.y + (target.y - from.y) * ratio, from.z + (target.z - from.z) * ratio)
    }

    private fun origin(
        player: UUID, trail: Trail, now: Instant, maxRange: Double,
        location: Pair<String, Point>?,
    ): Point? {
        if (location == null || location.first != trail.world || !now.isBefore(trail.expiresAt)) {
            stop(player)
            return null
        }
        val distance = location.second.distance(trail.target)
        if (!distance.isFinite() || distance <= ARRIVAL_RADIUS || distance > maxRange) {
            stop(player)
            return null
        }
        return location.second
    }

    companion object {
        private const val POINTS_PER_PLAYER = 12
        private const val ARRIVAL_RADIUS = 3.0
    }
}
