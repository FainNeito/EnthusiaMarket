package net.badgersmc.em.application

import java.time.Instant

object FinderPulsePlanner {
    fun plan(from: FinderTrailTracker.Point, route: List<FinderTrailTracker.Point>, count: Int,
             now: Instant, speed: Double): List<FinderTrailTracker.Point> {
        val segments = (listOf(from) + route).zipWithNext()
        val length = segments.sumOf { (a, b) -> a.distance(b) }
        val velocity = speed.takeIf { it.isFinite() }?.coerceIn(0.0, 8.0) ?: 2.0
        val phase = (Math.floorMod(now.toEpochMilli(), 120_000L) / 1000.0 * velocity) % 6.0
        return distances(count.coerceIn(0, 24), phase, velocity > 0).takeWhile { it <= minOf(length, 24.0) }
            .map { sample(segments, it) }
    }

    private fun distances(count: Int, phase: Double, animated: Boolean): List<Double> {
        if (!animated) return (1..count).map(Int::toDouble)
        val tail = when { count > 12 -> 6; count > 6 -> 3; else -> 2 }
        return (0 until count).map { 1.0 + (it / tail) * 6.0 + (it % tail) * 0.15 + phase }
    }

    private fun sample(segments: List<Pair<FinderTrailTracker.Point, FinderTrailTracker.Point>>,
                       distance: Double): FinderTrailTracker.Point {
        var remaining = distance
        for ((from, to) in segments) {
            val length = from.distance(to)
            if (length > 0 && remaining <= length) {
                val ratio = remaining / length
                return FinderTrailTracker.Point(from.x + (to.x - from.x) * ratio,
                    from.y + (to.y - from.y) * ratio, from.z + (to.z - from.z) * ratio)
            }
            remaining -= length
        }
        return segments.last().second
    }
}
