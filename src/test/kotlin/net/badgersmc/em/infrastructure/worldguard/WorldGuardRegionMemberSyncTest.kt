package net.badgersmc.em.infrastructure.worldguard

import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.bukkit.Bukkit
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Use real WorldGuard domains to cover legacy UUID, player-name and group grants. */
class WorldGuardRegionMemberSyncTest {
    private val region = ProtectedCuboidRegion("s1", BlockVector3.at(0, 0, 0), BlockVector3.at(10, 10, 10))
    private val sync = object : WorldGuardRegionMemberSync() {
        override fun findRegion(world: String, regionId: String) = region
    }
    private val former = UUID.randomUUID()

    @BeforeTest fun setUp() {
        mockkStatic(Bukkit::class)
        region.owners.addPlayer(former)
        region.owners.addPlayer("formerOwner")
        region.owners.addGroup("oldOwners")
        region.members.addPlayer(former)
        region.members.addPlayer("formerMember")
        region.members.addGroup("oldMembers")
    }

    @AfterTest fun stop() { unmockkStatic(Bukkit::class) }

    @Test fun `owner replacement removes every previous grant`() {
        val current = UUID.randomUUID()
        sync.setOwner("world", "s1", current)
        assertEquals(setOf(current), region.owners.uniqueIds)
        assertEquals(1, region.owners.size())
        assertEquals(0, region.members.size())
    }

    @Test fun `forfeiture and empty guild sync clear all domain entry types`() {
        sync.clearOwnersAndMembers("world", "s1")
        assertEquals(0, region.owners.size())
        assertEquals(0, region.members.size())
        region.owners.addPlayer(former)
        region.members.addPlayer(former)
        sync.syncGuildMembers("world", "s1", emptySet())
        assertEquals(0, region.owners.size())
        assertEquals(0, region.members.size())
    }

    @Test fun `unloaded world is reported rather than counted as success`() {
        every { Bukkit.getWorld("missing") } returns null
        assertFailsWith<IllegalStateException> { WorldGuardRegionMemberSync().setOwner("missing", "s1", former) }
    }

    @Test fun `guild replacement removes owners and keeps only intended UUID members`() {
        val current = UUID.randomUUID()
        sync.syncGuildMembers("world", "s1", setOf(current))
        assertEquals(0, region.owners.size())
        assertEquals(setOf(current), region.members.uniqueIds)
        assertEquals(1, region.members.size())
    }
}
