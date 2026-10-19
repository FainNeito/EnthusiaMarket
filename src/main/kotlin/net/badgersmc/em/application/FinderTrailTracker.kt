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
                     val footprint: RegionProvider.Footprint? = null, val route: List<Point> = emptyList())
    data class RenderSettings(val budget: Int, val range: Double, val outline: FinderOutlineOptions,
                              val points: Int = 12, val pulseSpeed: Double = 0.0,
                              val feedbackWithoutOutline: Boolean = false)
    private data class RenderContext(val now: Instant, val settings: RenderSettings)
    data class Frame(val direction: List<Point>, val outline: List<Point>, val target: Point? = null,
                     val arrived: Boolean = false, val footprint: RegionProvider.Footprint? = null,
                     val waypoint: Point? = null, val remainingDistance: Double? = null)
    private data class Active(val trail: Trail, var arrivedAt: Instant? = null, var routeIndex: Int = 0)
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
        renderFrames(now, RenderSettings(maxParticles, maxRange, FinderOutlineOptions(enabled = false)), position)
            .mapValues { it.value.direction }

    fun renderFrames(now: Instant, settings: RenderSettings,
                     position: (UUID) -> Pair<String, Point>?): Map<UUID, Frame> {
        val plans = linkedMapOf<UUID, Frame>()
        val players = active.keys.toList()
        if (players.isEmpty()) return plans
        val ordered = order(players)
        val context = RenderContext(now, settings.copy(outline = settings.outline.normalized()))
        var remaining = settings.budget.coerceAtLeast(0)
        for (player in ordered) {
            val entry = active.getValue(player)
            val origin = origin(entry, context, position(player))
            if (origin == null) { stop(player); continue }
            val frame = frame(entry, origin, context, remaining)
            plans[player] = frame
            remaining -= frame.direction.size + frame.outline.size
        }
        return plans
    }

    private fun order(players: List<UUID>): List<UUID> {
        val ordered = players.drop(offset % players.size) + players.take(offset % players.size)
        offset = (offset + 1) % players.size
        return ordered
    }

    private fun origin(entry: Active, context: RenderContext,
                       location: Pair<String, Point>?): Point? {
        if (location == null || location.first != entry.trail.world) return null
        val distance = location.second.distance(entry.trail.target)
        if (!distance.isFinite() || distance > context.settings.range) return null
        advanceRoute(entry, location.second)
        val arrivalDistance = if (entry.routeIndex < entry.trail.route.size) maxOf(distance, 4.0) else distance
        return location.second.takeIf { advanceArrival(entry, arrivalDistance, context) }
    }

    private fun advanceRoute(entry: Active, from: Point) {
        while (entry.routeIndex < entry.trail.route.size && from.distance(entry.trail.route[entry.routeIndex]) <= 2.5) {
            entry.routeIndex++
        }
    }

    private fun advanceArrival(entry: Active, distance: Double, context: RenderContext): Boolean {
        val now = context.now
        val options = context.settings.outline
        if (expiredBeforeArrival(entry, now)) return false
        if (distance <= ARRIVAL_RADIUS && entry.arrivedAt == null) {
            if (!canLinger(entry, options) && !context.settings.feedbackWithoutOutline) return false
            entry.arrivedAt = now
        }
        if (context.settings.feedbackWithoutOutline && entry.arrivedAt == now) return true
        return !arrivalEnded(entry, now, options)
    }

    private fun expiredBeforeArrival(entry: Active, now: Instant): Boolean =
        entry.arrivedAt == null && !now.isBefore(entry.trail.expiresAt)

    private fun arrivalEnded(entry: Active, now: Instant, options: FinderOutlineOptions): Boolean {
        val arrival = entry.arrivedAt ?: return false
        return !canLinger(entry, options) || !now.isBefore(arrival.plusSeconds(options.arrivalSeconds))
    }

    private fun canLinger(entry: Active, options: FinderOutlineOptions): Boolean =
        options.enabled && options.arrivalSeconds > 0 &&
            entry.trail.footprint?.let(FinderOutlinePlanner::valid) == true

    private fun frame(entry: Active, from: Point, context: RenderContext, budget: Int): Frame {
        val trail = entry.trail
        val options = context.settings.outline
        val distance = from.distance(trail.target)
        val route = trail.route.drop(entry.routeIndex) + trail.target
        val direction = if (entry.arrivedAt == null) FinderPulsePlanner.plan(from, route,
            minOf(budget, context.settings.points), context.now, context.settings.pulseSpeed) else emptyList()
        val shape = trail.footprint
        val visible = distance <= options.revealDistance && options.enabled
        val outline = if (shape != null && visible) {
            FinderOutlinePlanner.plan(shape, trail.target, options, budget - direction.size)
        } else emptyList()
        val remaining = (listOf(from) + route).zipWithNext().sumOf { (a, b) -> a.distance(b) }
        return Frame(direction, outline, trail.target, entry.arrivedAt != null,
            shape.takeIf { visible }, route.first(), remaining)
    }

    companion object {
        private const val ARRIVAL_RADIUS = 3.0
    }
}
