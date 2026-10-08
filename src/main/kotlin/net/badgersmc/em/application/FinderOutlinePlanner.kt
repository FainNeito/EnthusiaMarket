package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.RegionProvider
import kotlin.math.ceil

data class FinderOutlineOptions(
    val enabled: Boolean = true, val revealDistance: Double = 24.0,
    val arrivalSeconds: Long = 10, val height: Double = 3.0,
    val spacing: Double = 1.0, val maxPoints: Int = 64, val marker: Boolean = true,
) {
    fun normalized() = copy(
        revealDistance = finite(revealDistance, 24.0).coerceIn(3.0, 256.0),
        arrivalSeconds = arrivalSeconds.coerceIn(0, 60),
        height = finite(height, 3.0).coerceIn(0.5, 16.0),
        spacing = finite(spacing, 1.0).coerceIn(0.25, 8.0),
        maxPoints = maxPoints.coerceIn(0, 128),
    )
    private fun finite(value: Double, fallback: Double) = value.takeIf { it.isFinite() } ?: fallback
}

object FinderOutlinePlanner {
    fun valid(footprint: RegionProvider.Footprint): Boolean =
        footprint.vertices.size in 3..256 && footprint.minY.isFinite() && footprint.maxY.isFinite() &&
            footprint.minY < footprint.maxY && footprint.vertices.all { it.x.isFinite() && it.z.isFinite() }

    fun plan(footprint: RegionProvider.Footprint, target: FinderTrailTracker.Point,
             options: FinderOutlineOptions, budget: Int): List<FinderTrailTracker.Point> {
        if (!valid(footprint) || !options.enabled || budget <= 0) return emptyList()
        val settings = options.normalized()
        val limit = minOf(settings.maxPoints, budget)
        val height = minOf(settings.height, footprint.maxY - footprint.minY)
        val bottom = (target.y - 0.5).coerceIn(footprint.minY, footprint.maxY - height)
        return samples(footprint, target, settings, limit, bottom, bottom + height)
    }

    private data class Edge(val from: FinderTrailTracker.Point, val to: FinderTrailTracker.Point) {
        val length: Double get() = from.distance(to)
    }

    private fun samples(shape: RegionProvider.Footprint, target: FinderTrailTracker.Point,
                        options: FinderOutlineOptions, limit: Int, bottom: Double, top: Double): List<FinderTrailTracker.Point> {
        val marker = if (options.marker) (0..3).map { target.copy(y = target.y + 0.5 + it * 0.4) } else emptyList()
        val corners = shape.vertices.flatMap { listOf(
            FinderTrailTracker.Point(it.x, bottom, it.z), FinderTrailTracker.Point(it.x, top, it.z)) }
        val priority = (marker + corners).take(limit)
        val edges = ring(shape, bottom) + ring(shape, top)
        return priority + edgeSamples(edges, options.spacing, limit - priority.size)
    }

    private fun ring(shape: RegionProvider.Footprint, y: Double): List<Edge> = shape.vertices.indices.map { index ->
        val from = shape.vertices[index]
        val to = shape.vertices[(index + 1) % shape.vertices.size]
        Edge(FinderTrailTracker.Point(from.x, y, from.z), FinderTrailTracker.Point(to.x, y, to.z))
    }

    private fun edgeSamples(edges: List<Edge>, spacing: Double, budget: Int): List<FinderTrailTracker.Point> {
        if (budget <= 0) return emptyList()
        val length = edges.sumOf { it.length }
        if (!length.isFinite() || length <= 0) return emptyList()
        val count = ceil(length / spacing).coerceIn(1.0, budget.toDouble()).toInt()
        return (0 until count).map { sampleAt(edges, (it + 0.5) * length / count) }
    }

    private fun sampleAt(edges: List<Edge>, distance: Double): FinderTrailTracker.Point {
        var remaining = distance
        for (edge in edges) {
            val length = edge.length
            if (length > 0 && remaining <= length) {
                val ratio = remaining / length
                return FinderTrailTracker.Point(edge.from.x + (edge.to.x - edge.from.x) * ratio,
                    edge.from.y, edge.from.z + (edge.to.z - edge.from.z) * ratio)
            }
            remaining -= length
        }
        return edges.last().to
    }
}
