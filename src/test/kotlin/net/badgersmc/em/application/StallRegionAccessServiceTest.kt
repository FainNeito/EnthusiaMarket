package net.badgersmc.em.application

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.MarketMutationGate
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallState
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class StallRegionAccessServiceTest {
    private val regions = mockk<RegionMemberSync>(relaxed = true)
    private val guilds = mockk<GuildProvider>()
    private val gate = mockk<MarketMutationGate> { every { isStallLocked(any()) } returns false }
    private val service = StallRegionAccessService(guilds, regions, gate)
    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private fun stall(state: StallState = StallState.OWNED, ownership: OwnerRef = OwnerRef.solo(owner)) = Stall(
        StallId("s1"), "s1", "world", state, ownership, Instant.EPOCH, 100,
        RentTerms.flat(100), members = setOf(member),
    )

    @Test fun `current solo members are replayed after replacing the owner`() {
        assertTrue(service.sync(stall(StallState.GRACE)))
        io.mockk.verifyOrder {
            regions.setOwner("world", "s1", owner)
            regions.addMember("world", "s1", member)
        }
    }

    @Test fun `vacant and forfeited states clear even legacy owner and member fields`() {
        listOf(StallState.UNOWNED, StallState.EMERGENCY_AUCTIONING).forEach {
            assertTrue(service.sync(stall(it)))
        }
        verify(exactly = 2) { regions.clearOwnersAndMembers("world", "s1") }
        verify(exactly = 0) { regions.setOwner(any(), any(), any()) }
    }

    @Test fun `ownerless system auctions clear access while seller auctions are preserved`() {
        listOf(StallState.AUCTIONING, StallState.RE_AUCTIONING).forEach {
            assertFalse(service.sync(stall(it)))
            assertTrue(service.sync(stall(it, OwnerRef.unowned())))
        }
        verify(exactly = 2) { regions.clearOwnersAndMembers("world", "s1") }
        verify(exactly = 0) { regions.setOwner(any(), any(), any()) }
    }

    @Test fun `moderation holds and reservations do not touch any projection`() {
        assertFalse(service.sync(stall(StallState.MODERATION_HOLD)))
        every { gate.isStallLocked("s1") } returns true
        assertFalse(service.sync(stall()))
        assertFalse(service.sync(stall(StallState.EMERGENCY_AUCTIONING)))
        verify { regions wasNot Called }
        verify { guilds wasNot Called }
    }

    @Test fun `empty guild roster revokes old permissions and nonempty roster comes from provider`() {
        every { guilds.memberIds("guild1") } returnsMany listOf(emptySet(), setOf(member))
        val guildStall = stall(ownership = OwnerRef.guild("guild1"))
        assertTrue(service.sync(guildStall))
        assertTrue(service.sync(guildStall))
        verify(exactly = 1) { regions.syncGuildMembers("world", "s1", emptySet()) }
        verify(exactly = 1) { regions.syncGuildMembers("world", "s1", setOf(member)) }
        verify(exactly = 0) { regions.setOwner(any(), any(), any()) }
    }

    @Test fun `provider failure is not a successful reconciliation`() {
        every { regions.setOwner(any(), any(), any()) } throws IllegalStateException("missing region")
        assertFailsWith<IllegalStateException> { service.sync(stall()) }
        verify(exactly = 0) { regions.addMember(any(), any(), any()) }
    }
}
