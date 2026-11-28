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
import net.badgersmc.em.domain.stall.StallRepository
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
        val footprints = draft.layers.map { layer -> createFootprint(draft.id, layer, requireNotNull(draft.topY)) }
        if (footprints.any { it == null }) return "stall_region.invalid_polygon"
        if (!withinScanLimit(draft)) return "stall_region.volume_limit"
        return saveValidatedAirVolume(draft, footprints.filterNotNull(), parent, manager)
    }

    private fun saveValidatedAirVolume(
        draft: StallRegionDraft,
        footprints: List<ProtectedPolygonalRegion>,
        parent: ProtectedRegion,
        manager: RegionManager,
    ): String? {
        val blocks = scanAirVolume(draft, footprints)
        if (blocks.isEmpty()) return "stall_region.no_air"
        if (blocks.size > MAX_BLOCKS) return "stall_region.volume_limit"
        val boxes = StallAirVolumeGeometry.cuboids(blocks)
        if (boxes.isEmpty() || boxes.size > MAX_CUBOIDS) return "stall_region.shape_limit"
        if (!allBoxesInsideParent(boxes, parent)) return "stall_region.outside_parent"
        if (overlapsAnotherStall(draft, boxes, manager)) return "stall_region.overlap"
        draft.airVolume = boxes
        return null
    }

    private fun overlapsAnotherStall(draft: StallRegionDraft, boxes: List<StallRegionBox>, manager: RegionManager): Boolean {
        return StallRegionOverlap.overlaps(
            draft.id, config.market.regionPrefix, config.market.parentRegion, boxes, manager.regions.values,
        )
    }

    /** Remove only a canonical stall root and its exact air-piece children. */
    fun delete(id: String, deleteMarketRecord: () -> StallRepository.DeleteResult): String {
        val world = Bukkit.getWorld(config.market.world) ?: return "stall_region.world_missing"
        val manager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
            ?: return "stall_region.manager_missing"
        val exactChildren = manager.regions.values.filter { it.id.startsWith("${id}__air_") }
        val previous = listOfNotNull(manager.getRegion(id)) + exactChildren
        previous.forEach { manager.removeRegion(it.id) }
        try {
            manager.save()
        } catch (_: Exception) {
            previous.forEach(manager::addRegion)
            return if (runCatching { manager.save() }.isSuccess) "stall_region.delete_failed"
            else "stall_region.delete_restore_failed"
        }
        val outcome = try { deleteMarketRecord() } catch (_: Exception) { StallRepository.DeleteResult.REFERENCED }
        if (outcome != StallRepository.DeleteResult.DELETED) {
            previous.forEach(manager::addRegion)
            if (runCatching { manager.save() }.isFailure) return "stall_region.delete_restore_failed"
        }
        return when (outcome) {
            StallRepository.DeleteResult.DELETED -> "stall_region.deleted"
            StallRepository.DeleteResult.NOT_FOUND -> "stall_region.market_missing"
            StallRepository.DeleteResult.NOT_UNOWNED -> "stall_region.not_unowned"
            StallRepository.DeleteResult.REFERENCED -> "stall_region.in_use"
        }
    }

    private fun validateDraft(draft: StallRegionDraft): String? {
        if (draft.layers.isEmpty() || draft.layers.any { !StallRegionDraftValidation.validLayer(it) }) return "stall_region.need_polygon"
        if (!withinAreaLimit(draft)) return "stall_region.area_limit"
        val world = Bukkit.getWorld(draft.world) ?: return "stall_region.world_missing"
        if (draft.layers.any { it.floorY == null }) return "stall_region.mark_floor_first"
        val top = draft.topY ?: return "stall_region.height_required"
        if (config.market.stallPriority == Int.MAX_VALUE) return "stall_region.priority_limit"
        return if (draft.layers.any { top <= requireNotNull(it.floorY) || requireNotNull(it.floorY) < world.minHeight } || top >= world.maxHeight) "stall_region.bad_height" else null
    }

    private fun withinAreaLimit(draft: StallRegionDraft): Boolean {
        return draft.layers.all { layer ->
            val width = layer.points.maxOf(StallRegionPoint::x).toLong() - layer.points.minOf(StallRegionPoint::x) + 1
            val depth = layer.points.maxOf(StallRegionPoint::z).toLong() - layer.points.minOf(StallRegionPoint::z) + 1
            width * depth <= MAX_AREA
        }
    }

    private fun withinScanLimit(draft: StallRegionDraft): Boolean {
        val top = requireNotNull(draft.topY).toLong()
        val estimated = draft.layers.sumOf { layer ->
            val width = layer.points.maxOf(StallRegionPoint::x).toLong() - layer.points.minOf(StallRegionPoint::x) + 1
            val depth = layer.points.maxOf(StallRegionPoint::z).toLong() - layer.points.minOf(StallRegionPoint::z) + 1
            width * depth * (top - requireNotNull(layer.floorY))
        }
        return estimated <= MAX_BLOCKS
    }

    private fun scanAirVolume(draft: StallRegionDraft, polygons: List<ProtectedPolygonalRegion>): List<StallAirBlock> {
        val world = Bukkit.getWorld(draft.world) ?: return emptyList()
        val top = requireNotNull(draft.topY)
        return StallAirVolumeScanner.scan(
            draft.layers, top, MAX_BLOCKS,
            contains = { layer, x, z ->
                val index = draft.layers.indexOf(layer)
                polygons[index].contains(BlockVector3.at(x, requireNotNull(layer.floorY) + 1, z))
            },
            isAir = { x, y, z -> world.getBlockAt(x, y, z).type.isAir },
        )
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

    private fun createFootprint(id: String, layer: StallRegionLayer, top: Int): ProtectedPolygonalRegion? {
        val floor = layer.floorY ?: return null
        return runCatching {
            ProtectedPolygonalRegion(id, layer.points.map { BlockVector2.at(it.x, it.z) }, floor + 1, top)
        }.getOrNull()
    }

    private fun manager(draft: StallRegionDraft): RegionManager? {
        val world = Bukkit.getWorld(draft.world) ?: return null
        return WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
    }

    private fun managerError(draft: StallRegionDraft): String =
        if (Bukkit.getWorld(draft.world) == null) "stall_region.world_missing" else "stall_region.manager_missing"

    companion object {
        private const val MAX_BLOCKS = 2_000_000
        private const val MAX_CUBOIDS = 2_000
        private const val MAX_AREA = 16_384L
        private fun piecePrefix(id: String) = "${id}__air_"
        private fun pieceId(id: String, index: Int) = "${piecePrefix(id)}${index.toString().padStart(4, '0')}"
    }
}

internal object StallRegionOverlap {
    fun overlaps(
        editedStallId: String,
        prefix: String,
        parentRegionId: String,
        boxes: List<StallRegionBox>,
        regions: Collection<ProtectedRegion>,
    ): Boolean {
        val stallPattern = Regex("${Regex.escape(prefix)}[0-9]+(?:__air_[0-9]+)?")
        val matching = regions.filter { it.id != parentRegionId && stallPattern.matches(it.id) }
        val rootsWithPieces = matching.filter { it.id.contains("__air_") }.map { it.id.substringBefore("__air_") }.toSet()
        val occupied = matching.filter { region ->
            val rootId = region.id.substringBefore("__air_")
            rootId != editedStallId && (region.id.contains("__air_") || rootId !in rootsWithPieces)
        }
        return boxes.any { box -> occupied.any { region -> intersects(box, region) } }
    }

    internal fun intersects(box: StallRegionBox, region: ProtectedRegion): Boolean {
        val minX = maxOf(box.minX, region.minimumPoint.x()); val maxX = minOf(box.maxX, region.maximumPoint.x())
        val minY = maxOf(box.minY, region.minimumPoint.y()); val maxY = minOf(box.maxY, region.maximumPoint.y())
        val minZ = maxOf(box.minZ, region.minimumPoint.z()); val maxZ = minOf(box.maxZ, region.maximumPoint.z())
        if (minX > maxX || minY > maxY || minZ > maxZ) return false
        return (minX..maxX).any { x -> (minY..maxY).any { y ->
            (minZ..maxZ).any { z -> region.contains(BlockVector3.at(x, y, z)) }
        } }
    }
}
