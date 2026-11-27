package net.badgersmc.em.infrastructure.worldguard

import com.sk89q.worldguard.WorldGuard
import net.badgersmc.em.domain.ports.RegionProvider
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Bukkit

@Component
class WorldGuardRegionProvider(private val config: EnthusiaMarketConfig) : RegionProvider {

    // Key by the immutable outline so WorldGuard redefinitions invalidate the measured area.
    private val floorAreas = java.util.WeakHashMap<List<com.sk89q.worldedit.math.BlockVector2>, Long?>()

    private fun floorArea(points: List<com.sk89q.worldedit.math.BlockVector2>): Long? = synchronized(floorAreas) {
        if (!floorAreas.containsKey(points)) floorAreas[points] = claimedPolygonColumns(points)
        floorAreas[points]
    }

    override fun footprint(world: String, id: String): RegionProvider.Footprint? {
        val bukkitWorld = Bukkit.getWorld(world) ?: return null
        val manager = WorldGuard.getInstance().platform.regionContainer.get(
            com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld)) ?: return null
        val region = manager.getRegion(id) ?: return null
        val min = region.minimumPoint
        val max = region.maximumPoint
        val pieces = airPieces(manager.regions.values, id)
        val vertices = if (pieces.isNotEmpty()) {
            val minX = pieces.minOf { it.minimumPoint.x() }; val maxX = pieces.maxOf { it.maximumPoint.x() }
            val minZ = pieces.minOf { it.minimumPoint.z() }; val maxZ = pieces.maxOf { it.maximumPoint.z() }
            listOf(RegionProvider.Vertex(minX.toDouble(), minZ.toDouble()),
                RegionProvider.Vertex(maxX + 1.0, minZ.toDouble()),
                RegionProvider.Vertex(maxX + 1.0, maxZ + 1.0),
                RegionProvider.Vertex(minX.toDouble(), maxZ + 1.0))
        } else when (region) {
            is com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion ->
                region.points.map { RegionProvider.Vertex(it.x() + 0.5, it.z() + 0.5) }
            is com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion -> listOf(
                RegionProvider.Vertex(min.x().toDouble(), min.z().toDouble()),
                RegionProvider.Vertex(max.x() + 1.0, min.z().toDouble()),
                RegionProvider.Vertex(max.x() + 1.0, max.z() + 1.0),
                RegionProvider.Vertex(min.x().toDouble(), max.z() + 1.0))
            else -> return null
        }
        val bottom = pieces.minOfOrNull { it.minimumPoint.y() } ?: min.y()
        val top = pieces.maxOfOrNull { it.maximumPoint.y() + 1.0 } ?: (max.y() + 1.0)
        return RegionProvider.Footprint(vertices, bottom.toDouble(), top.toDouble())
    }

    override fun listByPrefix(world: String, prefix: String): List<RegionProvider.RegionRef> {
        val bukkitWorld = Bukkit.getWorld(world) ?: return emptyList()
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container.get(
            com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld)
        ) ?: return emptyList()
        return regionManager.regions.keys
            .filter { it.matches(Regex("${Regex.escape(prefix)}[0-9]+")) }
            .map { RegionProvider.RegionRef(world, it) }
    }

    override fun exists(world: String, id: String): Boolean {
        val bukkitWorld = Bukkit.getWorld(world) ?: return false
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container.get(
            com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld)
        ) ?: return false
        return regionManager.getRegion(id) != null
    }

    override fun regionAt(world: String, x: Int, y: Int, z: Int): String? {
        val bukkitWorld = Bukkit.getWorld(world) ?: return null
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container.get(
            com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld)
        ) ?: return null
        val applicable = regionManager.getApplicableRegions(
            com.sk89q.worldedit.math.BlockVector3.at(x, y, z)
        )
        val regions = applicable.regions.filter { region ->
            val logicalId = region.id.substringBefore("__air_")
            val isStallId = logicalId.matches(Regex("${Regex.escape(config.market.regionPrefix)}[0-9]+"))
            isStallId && (region.id.contains("__air_") || airPieces(regionManager.regions.values, region.id).isEmpty())
        }
        return regions.maxByOrNull { it.priority }?.id?.substringBefore("__air_")
    }

    override fun bounds(world: String, id: String): RegionProvider.RegionBounds? {
        val bukkitWorld = Bukkit.getWorld(world) ?: return null
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container.get(
            com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld)
        ) ?: return null
        val region = regionManager.getRegion(id) ?: return null
        val pieces = airPieces(regionManager.regions.values, id)
        if (pieces.isNotEmpty()) {
            val minX = pieces.minOf { it.minimumPoint.x() }; val minY = pieces.minOf { it.minimumPoint.y() }; val minZ = pieces.minOf { it.minimumPoint.z() }
            val maxX = pieces.maxOf { it.maximumPoint.x() }; val maxY = pieces.maxOf { it.maximumPoint.y() }; val maxZ = pieces.maxOf { it.maximumPoint.z() }
            val columns = pieces.flatMap { piece ->
                (piece.minimumPoint.x()..piece.maximumPoint.x()).flatMap { x ->
                    (piece.minimumPoint.z()..piece.maximumPoint.z()).map { z -> x to z }
                }
            }.toSet().size.toLong()
            val volume = pieces.sumOf { piece ->
                (piece.maximumPoint.x().toLong() - piece.minimumPoint.x() + 1) *
                    (piece.maximumPoint.y().toLong() - piece.minimumPoint.y() + 1) *
                    (piece.maximumPoint.z().toLong() - piece.minimumPoint.z() + 1)
            }
            return RegionProvider.RegionBounds(minX, minY, minZ, maxX, maxY, maxZ, columns, volume)
        }
        val min = region.minimumPoint
        val max = region.maximumPoint
        val bounds = RegionProvider.RegionBounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z())
        return when (region) {
            is com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion -> bounds
            is com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion ->
                bounds.copy(claimedColumns = floorArea(region.points))
            else -> bounds.copy(claimedColumns = null)
        }
    }

    private fun airPieces(regions: Collection<com.sk89q.worldguard.protection.regions.ProtectedRegion>, id: String) =
        regions.filterIsInstance<com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion>()
            .filter { it.id.startsWith("${id}__air_") }

}
