package net.badgersmc.em.infrastructure.listeners

import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StallRegionOverlapTest {
    private val existingPiece = ProtectedCuboidRegion(
        "stall4__air_0000", BlockVector3.at(0, 1, 0), BlockVector3.at(1, 3, 1),
    )
    private val candidate = StallRegionBox(1, 2, 1, 2, 2, 1)

    @Test fun `rejects intersecting blocks inside another stall air piece`() {
        assertTrue(StallRegionOverlap.overlaps("stall5", "stall", "market", listOf(candidate), listOf(existingPiece)))
    }

    @Test fun `ignores a logical root marker when its air pieces define the interior`() {
        val root = ProtectedCuboidRegion("stall4", BlockVector3.at(1, 2, 1), BlockVector3.at(1, 2, 1))
        val awayPiece = ProtectedCuboidRegion(
            "stall4__air_0000", BlockVector3.at(10, 1, 10), BlockVector3.at(11, 3, 11),
        )
        assertFalse(StallRegionOverlap.overlaps("stall5", "stall", "market", listOf(candidate), listOf(root, awayPiece)))
    }

    @Test fun `allows adjacent interiors that do not share a block`() {
        val adjacent = StallRegionBox(2, 1, 0, 3, 3, 1)
        assertFalse(StallRegionOverlap.overlaps("stall5", "stall", "market", listOf(adjacent), listOf(existingPiece)))
    }
}
