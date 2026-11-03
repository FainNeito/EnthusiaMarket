package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.RegionProvider

/** Full edges rather than particle samples. Refuses partial boundaries under the entity cap. */
object FinderBeamPlanner {
    data class Beam(val from: FinderTrailTracker.Point, val to: FinderTrailTracker.Point)

    fun plan(shape: RegionProvider.Footprint?, target: FinderTrailTracker.Point,
             options: FinderOutlineOptions): List<Beam> {
        val settings = options.normalized()
        if (!settings.enabled) return emptyList()
        val marker = if (settings.marker) marker(target) else emptyList()
        if (shape == null || !FinderOutlinePlanner.valid(shape)) return marker
        val height = minOf(settings.height, shape.maxY - shape.minY)
        val bottom = (target.y - 0.5).coerceIn(shape.minY, shape.maxY - height)
        val low = shape.vertices.map { FinderTrailTracker.Point(it.x, bottom, it.z) }
        val high = low.map { it.copy(y = bottom + height) }
        return marker + ring(low) + ring(high) + low.indices.map { Beam(low[it], high[it]) }
    }

    private fun marker(target: FinderTrailTracker.Point): List<Beam> {
        val low = listOf(-0.6 to -0.6, 0.6 to -0.6, 0.6 to 0.6, -0.6 to 0.6).map {
            target.copy(x = target.x + it.first, y = target.y - 0.6, z = target.z + it.second)
        }
        val high = low.map { it.copy(y = target.y + 0.6) }
        return ring(low) + ring(high) + low.indices.map { Beam(low[it], high[it]) }
    }

    private fun ring(points: List<FinderTrailTracker.Point>): List<Beam> = points.indices.map {
        Beam(points[it], points[(it + 1) % points.size])
    }
}
