package net.badgersmc.em.infrastructure.worldguard

import com.sk89q.worldedit.math.BlockVector2
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldGuardRegionMetadataCopyTest {
    @Test
    fun `copying root metadata into air component retains its shape and access`() {
        val owner = UUID.randomUUID()
        val root = ProtectedPolygonalRegion("stall4", listOf(
            BlockVector2.at(0, 0), BlockVector2.at(4, 0), BlockVector2.at(4, 4), BlockVector2.at(0, 4),
        ), 1, 8)
        root.owners.addPlayer(owner)
        root.members.addPlayer("trustedPlayer")
        root.setFlag(Flags.BUILD, StateFlag.State.DENY)
        val piece = ProtectedCuboidRegion("stall4__air_0000", BlockVector3.at(1, 2, 1), BlockVector3.at(2, 3, 2))

        piece.copyFrom(root)

        assertEquals(BlockVector3.at(1, 2, 1), piece.minimumPoint)
        assertEquals(BlockVector3.at(2, 3, 2), piece.maximumPoint)
        assertEquals(setOf(owner), piece.owners.uniqueIds)
        assertEquals(setOf("trustedplayer"), piece.members.players)
        assertEquals(StateFlag.State.DENY, piece.getFlag(Flags.BUILD))
    }
}
