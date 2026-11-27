package net.badgersmc.em.infrastructure.listeners

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector2
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.managers.RegionManager
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.RegionProvisioner
import org.bukkit.Bukkit

/** Builds a WorldGuard parent stall plus disjoint access cuboids matching its usable air volume. */
internal class StallRegionWorldGuardWriter(
    private val config: EnthusiaMarketConfig,
    private val provisioner: RegionProvisioner,
) {
    fun validate(draft: StallRegionDraft): String? {
        return validateDraft(draft) ?: validateAirBoundary(draft)
    }

    private fun validateAirBoundary(draft: StallRegionDraft): String? {
        val manager = manager(draft) ?: return managerError(draft)
        val parent = manager.getRegion(config.market.parentRegion) ?: return "stall_region.parent_missing"
        val footprint = createFootprint(draft) ?: return "stall_region.invalid_polygon"
        if (!withinScanLimit(draft)) return "stall_region.volume_limit"
        val blocks = scanAirVolume(draft, footprint)
        if (blocks.isEmpty()) return "stall_region.no_air"
        if (blocks.size > MAX_BLOCKS) return "stall_region.volume_limit"
        val boxes = StallAirVolumeGeometry.cuboids(blocks)
        if (boxes.isEmpty() || boxes.size > MAX_CUBOIDS) return "stall_region.shape_limit"
        if (!allBoxesInsideParent(boxes, parent)) return "stall_region.outside_parent"
        draft.airVolume = boxes
        return null
    }

    private fun validateDraft(draft: StallRegionDraft): String? {
        if (draft.points.size < MIN_POINTS || !StallPolygonGeometry.valid(draft.points)) return "stall_region.need_polygon"
        if (!withinAreaLimit(draft)) return "stall_region.area_limit"
        val world = Bukkit.getWorld(draft.world) ?: return "stall_region.world_missing"
        val floor = draft.floorY ?: return "stall_region.mark_floor_first"
        val top = draft.topY ?: return "stall_region.height_required"
        if (config.market.stallPriority == Int.MAX_VALUE) return "stall_region.priority_limit"
        return if (top <= floor || top >= world.maxHeight || floor < world.minHeight) "stall_region.bad_height" else null
    }

    private fun withinAreaLimit(draft: StallRegionDraft): Boolean {
        val width = draft.points.maxOf(StallRegionPoint::x).toLong() - draft.points.minOf(StallRegionPoint::x) + 1
        val depth = draft.points.maxOf(StallRegionPoint::z).toLong() - draft.points.minOf(StallRegionPoint::z) + 1
        return width * depth <= MAX_AREA
    }

    private fun withinScanLimit(draft: StallRegionDraft): Boolean {
        val width = draft.points.maxOf(StallRegionPoint::x).toLong() - draft.points.minOf(StallRegionPoint::x) + 1
        val depth = draft.points.maxOf(StallRegionPoint::z).toLong() - draft.points.minOf(StallRegionPoint::z) + 1
        val height = (requireNotNull(draft.topY) - requireNotNull(draft.floorY)).toLong()
        return width * depth * height <= MAX_BLOCKS
    }

    private fun scanAirVolume(draft: StallRegionDraft, polygon: ProtectedPolygonalRegion): List<StallAirBlock> {
        val world = Bukkit.getWorld(draft.world) ?: return emptyList()
        val floor = requireNotNull(draft.floorY)
        val top = requireNotNull(draft.topY)
        val minX = draft.points.minOf(StallRegionPoint::x)
        val maxX = draft.points.maxOf(StallRegionPoint::x)
        val minZ = draft.points.minOf(StallRegionPoint::z)
        val maxZ = draft.points.maxOf(StallRegionPoint::z)
        val result = ArrayList<StallAirBlock>()
        for (x in minX..maxX) for (z in minZ..maxZ) {
            if (polygon.contains(BlockVector3.at(x, floor + 1, z))) appendColumn(draft, x, z, result)
            if (result.size > MAX_BLOCKS) return result
        }
        return result
    }

    private fun appendColumn(draft: StallRegionDraft, x: Int, z: Int, target: MutableList<StallAirBlock>) {
        val world = Bukkit.getWorld(draft.world) ?: return
        val floor = requireNotNull(draft.floorY)
        val top = requireNotNull(draft.topY)
        val ceiling = (floor + 1..top).lastOrNull { y -> !world.getBlockAt(x, y, z).type.isAir } ?: return
        for (y in floor + 1 until ceiling) {
            if (world.getBlockAt(x, y, z).type.isAir) target += StallAirBlock(x, y, z)
        }
    }

    private fun allBoxesInsideParent(boxes: List<StallRegionBox>, parent: ProtectedRegion): Boolean =
        boxes.all { box ->
            (box.minX..box.maxX).all { x -> (box.minY..box.maxY).all { y ->
                (box.minZ..box.maxZ).all { z -> parent.contains(BlockVector3.at(x, y, z)) }
            } }
        }

    fun save(draft: StallRegionDraft): String {
        val planResult = createSavePlan(draft)
        planResult.error?.let { return it }
        val plan = planResult.plan ?: return "stall_region.save_failed"
        try {
            persist(plan, draft)
            return "stall_region.saved"
        } catch (_: Exception) {
            rollback(plan)
            return "stall_region.save_failed"
        }
    }

    private data class SavePlan(
        val manager: RegionManager,
        val oldRegions: List<ProtectedRegion>,
        val root: ProtectedCuboidRegion,
        val pieces: List<ProtectedCuboidRegion>,
    )
    private data class SavePlanResult(val plan: SavePlan? = null, val error: String? = null)

    private fun createSavePlan(draft: StallRegionDraft): SavePlanResult {
        val manager = manager(draft) ?: return SavePlanResult(error = managerError(draft))
        val parent = manager.getRegion(config.market.parentRegion) ?: return SavePlanResult(error = "stall_region.parent_missing")
        val rootPoint = draft.airVolume.firstOrNull()?.let { BlockVector3.at(it.minX, it.minY, it.minZ) }
            ?: return SavePlanResult(error = "stall_region.no_air")
        val oldRoot = manager.getRegion(draft.id)
        val oldPieces = manager.regions.values.filter { it.id.startsWith(piecePrefix(draft.id)) }
        val root = ProtectedCuboidRegion(draft.id, rootPoint, rootPoint)
        oldRoot?.let(root::copyFrom)
        if (oldRoot == null && runCatching { root.parent = parent }.isFailure) {
            return SavePlanResult(error = "stall_region.parent_invalid")
        }
        root.priority = config.market.stallPriority
        val pieces = draft.airVolume.mapIndexed { index, box -> createPiece(draft.id, index, box, parent, root) }
        if (pieces.any { it == null }) return SavePlanResult(error = "stall_region.parent_invalid")
        val oldRegions = listOfNotNull(oldRoot) + oldPieces
        return SavePlanResult(SavePlan(manager, oldRegions, root, pieces.filterNotNull()))
    }

    private fun createPiece(
        stallId: String,
        index: Int,
        box: StallRegionBox,
        parent: ProtectedRegion,
        root: ProtectedRegion,
    ): ProtectedCuboidRegion? {
        val piece = ProtectedCuboidRegion(pieceId(stallId, index),
            BlockVector3.at(box.minX, box.minY, box.minZ), BlockVector3.at(box.maxX, box.maxY, box.maxZ))
        piece.copyFrom(root)
        piece.priority = config.market.stallPriority + 1
        return piece.takeIf { runCatching { it.parent = parent }.isSuccess }
    }

    private fun persist(plan: SavePlan, draft: StallRegionDraft) {
        plan.oldRegions.forEach { plan.manager.removeRegion(it.id) }
        plan.manager.addRegion(plan.root)
        plan.pieces.forEach(plan.manager::addRegion)
        check(provisioner.provisionGroup(
            draft.world,
            draft.id,
            plan.pieces.map(ProtectedRegion::getId),
            config.market.stallPriority,
        ))
    }

    private fun rollback(plan: SavePlan) {
        (plan.oldRegions.map(ProtectedRegion::getId) + listOf(plan.root.id) + plan.pieces.map(ProtectedRegion::getId))
            .distinct().forEach(plan.manager::removeRegion)
        plan.oldRegions.forEach(plan.manager::addRegion)
        runCatching { plan.manager.save() }
    }

    private fun createFootprint(draft: StallRegionDraft): ProtectedPolygonalRegion? {
        val floor = draft.floorY ?: return null
        val top = draft.topY ?: return null
        return runCatching {
            ProtectedPolygonalRegion(draft.id, draft.points.map { BlockVector2.at(it.x, it.z) }, floor + 1, top)
        }.getOrNull()
    }

    private fun manager(draft: StallRegionDraft): RegionManager? {
        val world = Bukkit.getWorld(draft.world) ?: return null
        return WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
    }

    private fun managerError(draft: StallRegionDraft): String =
        if (Bukkit.getWorld(draft.world) == null) "stall_region.world_missing" else "stall_region.manager_missing"

    companion object {
        private const val MIN_POINTS = 3
        private const val MAX_BLOCKS = 2_000_000
        private const val MAX_CUBOIDS = 2_000
        private const val MAX_AREA = 16_384L
        private fun piecePrefix(id: String) = "${id}__air_"
        private fun pieceId(id: String, index: Int) = "${piecePrefix(id)}${index.toString().padStart(4, '0')}"
    }
}
