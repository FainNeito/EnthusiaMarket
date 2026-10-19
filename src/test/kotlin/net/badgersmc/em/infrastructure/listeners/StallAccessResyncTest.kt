package net.badgersmc.em.infrastructure.listeners

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.event.world.WorldLoadEvent
import net.badgersmc.em.domain.ports.MarketMutationGate
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Exercise the actual delayed startup hook, not a second implementation of its policy. */
class StallAccessResyncTest {
    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val regions = mockk<RegionMemberSync>(relaxed = true)
    private val stalls = mockk<StallRepository>()
    private val guilds = mockk<GuildProvider>(relaxed = true)

    @BeforeTest fun start() {
        MockBukkit.mock()
        MockBukkit.createMockPlugin("EnthusiaMarket")
    }

    @AfterTest fun stop() { MockBukkit.unmock() }

    private fun stall(state: StallState = StallState.OWNED) = Stall(
        StallId("stall48"), "stall48", "world", state, OwnerRef.solo(owner),
        Instant.EPOCH, 100, RentTerms.flat(100), members = setOf(member),
    )

    private fun restartWith(stall: Stall) {
        every { stalls.all() } returns listOf(stall)
        GuildStallResyncOnStartup(stalls, guilds, regions)
            .onServerLoad(ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP))
        MockBukkit.getMock()!!.scheduler.performTicks(101)
    }

    @Test fun `restart replaces solo access with current owner and legitimate members`() {
        restartWith(stall())
        verify(exactly = 1) { regions.setOwner("world", "stall48", owner) }
        verify(exactly = 1) { regions.addMember("world", "stall48", member) }
        verify(exactly = 0) { stalls.save(any()) }
    }

    @Test fun `restart clears expired ownership instead of restoring former owner`() {
        restartWith(stall(StallState.EMERGENCY_AUCTIONING))
        verify(exactly = 1) { regions.clearOwnersAndMembers("world", "stall48") }
        verify(exactly = 0) { regions.setOwner(any(), any(), any()) }
        verify(exactly = 0) { stalls.save(any()) }
    }

    @Test fun `one failed region does not prevent later stale permissions being cleared`() {
        every { stalls.all() } returns listOf(stall(), stall(StallState.UNOWNED).copy(regionId = "other"))
        every { regions.setOwner(any(), any(), any()) } throws IllegalStateException("missing region")
        GuildStallResyncOnStartup(stalls, guilds, regions)
            .onServerLoad(ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP))
        MockBukkit.getMock()!!.scheduler.performTicks(101)
        verify(exactly = 1) { regions.clearOwnersAndMembers("world", "other") }
    }

    @Test fun `startup preserves moderation reservations and holds`() {
        every { stalls.all() } returns listOf(stall(), stall(StallState.MODERATION_HOLD).copy(id = StallId("held")))
        val gate = mockk<MarketMutationGate> {
            every { isStallLocked(any()) } answers { firstArg<String>() == "stall48" }
        }
        GuildStallResyncOnStartup(stalls, guilds, regions, gate)
            .onServerLoad(ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP))
        MockBukkit.getMock()!!.scheduler.performTicks(101)
        verify { regions wasNot Called }
    }

    @Test fun `later world load retries only that worlds current roster`() {
        val server = MockBukkit.getMock()!!
        val world = server.addSimpleWorld("late")
        every { stalls.all() } returns emptyList()
        val listener = GuildStallResyncOnStartup(stalls, guilds, regions)
        listener.onServerLoad(ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP))
        server.scheduler.performTicks(101)
        every { stalls.all() } returns listOf(stall().copy(world = "late"), stall().copy(regionId = "other"))
        listener.onWorldLoad(WorldLoadEvent(world))
        server.scheduler.performTicks(2)
        verify(exactly = 1) { regions.setOwner("late", "stall48", owner) }
        verify(exactly = 0) { regions.setOwner("world", "other", any()) }
    }
}
