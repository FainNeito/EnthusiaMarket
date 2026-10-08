package net.badgersmc.em.application

import io.mockk.*
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.offer.SellOfferRepository
import net.badgersmc.em.domain.ports.*
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.stall.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildSellbackTest {
    private val actor = UUID.randomUUID()
    private val guildId = UUID.randomUUID().toString()
    private val stalls = mockk<StallRepository>(relaxed = true)
    private val shops = mockk<ShopRepository>(relaxed = true)
    private val offers = mockk<SellOfferRepository>(relaxed = true)
    private val guilds = mockk<GuildProvider>(relaxed = true)
    private val economy = mockk<EconomyProvider>(relaxed = true)
    private val regions = mockk<RegionMemberSync>(relaxed = true)
    private val ip = mockk<IpLimiter>(relaxed = true)
    private val stall = Stall(StallId("s1"), "s1", "world", StallState.OWNED, OwnerRef.guild(guildId),
        Instant.now(), 100, RentTerms.formula(1.0), nextRentAt = Instant.now().plusSeconds(3 * 86400))
    private val service = StallSellbackService(stalls, shops, offers, economy, guilds, EnthusiaMarketConfig(), regions, ipLimiter = ip)

    @BeforeTest fun setup() {
        every { stalls.findById(stall.id) } returns stall
        every { shops.findByStall(any()) } returns emptyList()
        every { offers.findByStall(any()) } returns null
        every { guilds.isMember(actor, guildId) } returns true
        every { guilds.hasShopPermission(actor, guildId, any()) } returns true
        every { guilds.bankDeposit(guildId, any()) } returns true
    }

    @Test fun `guild sellback pays guild rather than acting member`() {
        val quote = assertIs<StallSellbackService.QuoteResult.Ok>(service.quote(stall.id, actor)).quote
        assertTrue(quote.refund > 0)
        assertIs<StallSellbackService.ExecuteResult.Sold>(service.execute(stall.id, actor))
        verify { guilds.bankDeposit(guildId, quote.refund) }
        verify(exactly = 0) { economy.deposit(any(), any()) }
        verify { regions.clearOwnersAndMembers("world", "s1") }
    }

    @Test fun `failed guild refund restores ownership without clearing access`() {
        every { guilds.bankDeposit(guildId, any()) } returns false
        assertIs<StallSellbackService.ExecuteResult.Rejected>(service.execute(stall.id, actor))
        verify { stalls.save(stall) }
        verify(exactly = 0) { regions.clearOwnersAndMembers(any(), any()) }
        verify(exactly = 0) { ip.releaseStallByOwnerId(any()) }
    }

    @Test fun `guild membership alone cannot sell back stall`() {
        every { guilds.hasShopPermission(actor, guildId, any()) } returns false
        assertIs<StallSellbackService.QuoteResult.NotAuthorised>(service.quote(stall.id, actor))
        assertIs<StallSellbackService.ExecuteResult.NotAuthorised>(service.execute(stall.id, actor))
        verify(exactly = 0) { stalls.save(any()) }
    }

    @Test fun `guild sellback rechecks a moderation lock acquired after quote`() {
        rejectsLockedConfirmation(stall)
    }

    @Test fun `personal sellback rechecks a moderation lock acquired after quote`() {
        rejectsLockedConfirmation(stall.copy(owner = OwnerRef.solo(actor)))
    }

    private fun rejectsLockedConfirmation(owned: Stall) {
        val gate = mockk<MarketMutationGate>()
        every { stalls.findById(owned.id) } returns owned
        every { gate.isStallLocked(owned.id.value) } returns false
        val guarded = StallSellbackService(
            stalls, shops, offers, economy, guilds, EnthusiaMarketConfig(), regions,
            ipLimiter = ip, mutationGate = gate,
        )
        assertIs<StallSellbackService.QuoteResult.Ok>(guarded.quote(owned.id, actor))
        every { gate.isStallLocked(owned.id.value) } returns true

        val result = assertIs<StallSellbackService.ExecuteResult.Rejected>(guarded.execute(owned.id, actor))
        assertTrue(result.reason.contains("temporarily unavailable"))
        verify(exactly = 0) { stalls.save(any()) }
        verify(exactly = 0) { guilds.bankDeposit(any(), any()) }
        verify(exactly = 0) { economy.deposit(any(), any()) }
        verify(exactly = 0) { shops.delete(any()) }
        verify(exactly = 0) { offers.delete(any()) }
        verify(exactly = 0) { regions.clearOwnersAndMembers(any(), any()) }
        verify(exactly = 0) { ip.releaseStallByOwnerId(any()) }
    }
}
