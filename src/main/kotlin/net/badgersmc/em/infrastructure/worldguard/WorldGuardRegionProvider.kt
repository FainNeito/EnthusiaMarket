package net.badgersmc.em.infrastructure.worldguard

import com.sk89q.worldguard.WorldGuard
import net.badgersmc.em.domain.ports.RegionProvider
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Bukkit

@Component
class WorldGuardRegionProvider : RegionProvider {

    override fun footprint(world: String, id: String): RegionProvider.Footprint? {
        val bukkitWorld = Bukkit.getWorld(world) ?: return null
        val manager = WorldGuard.getInstance().platform.regionContainer.get(
            com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld)) ?: return null
        val region = manager.getRegion(id) ?: return null
        val min = region.minimumPoint
        val max = region.maximumPoint
        val vertices = when (region) {
            is com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion ->
                region.points.map { RegionProvider.Vertex(it.x() + 0.5, it.z() + 0.5) }
            is com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion -> listOf(
                RegionProvider.Vertex(min.x().toDouble(), min.z().toDouble()),
                RegionProvider.Vertex(max.x() + 1.0, min.z().toDouble()),
                RegionProvider.Vertex(max.x() + 1.0, max.z() + 1.0),
                RegionProvider.Vertex(min.x().toDouble(), max.z() + 1.0))
            else -> return null
        }
        return RegionProvider.Footprint(vertices, min.y().toDouble(), max.y() + 1.0)
    }

    override fun listByPrefix(world: String, prefix: String): List<RegionProvider.RegionRef> {
        val bukkitWorld = Bukkit.getWorld(world) ?: return emptyList()
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container.get(
            com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld)
        ) ?: return emptyList()
        return regionManager.regions.keys
            .filter { it.startsWith(prefix) }
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
        return applicable.regions
            .maxByOrNull { it.priority }
            ?.id
    }

    override fun bounds(world: String, id: String): RegionProvider.RegionBounds? {
        val bukkitWorld = Bukkit.getWorld(world) ?: return null
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container.get(
            com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld)
        ) ?: return null
        val region = regionManager.getRegion(id) ?: return null
        val min = region.minimumPoint
        val max = region.maximumPoint
        return RegionProvider.RegionBounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z())
    }
}
