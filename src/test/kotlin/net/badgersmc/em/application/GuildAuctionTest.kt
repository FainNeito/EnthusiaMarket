package net.badgersmc.em.application

import io.mockk.*
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.auction.*
import net.badgersmc.em.domain.offer.SellOfferRepository
import net.badgersmc.em.domain.ports.*
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.stall.*
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildAuctionTest {
    private val actor = UUID.randomUUID()
    private val secondActor = UUID.randomUUID()
    private val guildId = UUID.randomUUID().toString()
    private val auctions = mockk<AuctionRepository>(relaxed = true)
    private val stalls = mockk<StallRepository>(relaxed = true)
    private val guilds = mockk<GuildProvider>(relaxed = true)
    private val economy = mockk<EconomyProvider>(relaxed = true)
    private val regions = mockk<RegionMemberSync>(relaxed = true)
    private val limits = mockk<LimitResolutionService>()
    private val now = Instant.now()
    private var auction = Auction(AuctionId("a1"), StallId("s1"), AuctionState.OPEN, now, now.plusSeconds(3600), 100, null, Duration.ZERO, Duration.ZERO)
    private val stall = Stall(StallId("s1"), "s1", "world", StallState.AUCTIONING, OwnerRef.unowned(), null, 0, RentTerms.formula(1.0))
    private lateinit var service: AuctionLifecycleService

    @BeforeTest fun setup() {
        every { auctions.findById(auction.id) } answers { auction }
        every { auctions.save(any()) } answers { auction = firstArg() }
        every { auctions.findExpired() } answers { if (auction.state == AuctionState.OPEN) listOf(auction) else emptyList() }
        every { stalls.findById(stall.id) } returns stall
        every { stalls.all() } returns listOf(stall)
        every { guilds.guildById(guildId) } returns GuildProvider.GuildRef(guildId, "Guild")
        every { guilds.isMember(any(), guildId) } returns true
        every { guilds.hasShopPermission(any(), guildId, any()) } returns true
        every { guilds.bankWithdraw(guildId, any()) } returns true
        every { guilds.bankDeposit(guildId, any()) } returns true
        every { guilds.memberIds(guildId) } returns setOf(actor, secondActor)
        setupEconomyAndService()
    }

    private fun setupEconomyAndService() {
        every { economy.withdraw(any(), any()) } returns true
        every { economy.deposit(any(), any()) } returns true
        every { limits.canClaim(any(), any(), any(), any()) } returns LimitResolutionService.ClaimDecision.Allowed
        val ip = mockk<IpLimiter>(relaxed = true)
        every { ip.acquireAuction(any(), any()) } returns IpLimiter.Attempt(true, null)
        val offers = mockk<SellOfferRepository>(relaxed = true)
        every { offers.findByStall(any()) } returns null
        val shops = mockk<ShopRepository>(relaxed = true)
        every { shops.findByStall(any()) } returns emptyList()
        service = AuctionLifecycleService(auctions, stalls, economy, EnthusiaMarketConfig(), limits, offers,
            shops, regions, StallOwnershipCounter(stalls), ip, lang = mockk(relaxed = true), guildProvider = guilds)
    }

    @Test fun `guild escrow and award never use personal funds`() {
        assertIs<AuctionResult.Success>(service.placeBid(auction.id, AuctionLifecycleService.BidRequest(actor, 100, "ip", guildId)))
        assertEquals(guildId, auction.highBid?.guildId)
        service.settleExpired()
        verify { guilds.bankWithdraw(guildId, 100) }
        verify(exactly = 0) { economy.withdraw(any(), any()) }
        verify { stalls.save(match { it.owner == OwnerRef.guild(guildId) && it.state == StallState.OWNED }) }
        verify { regions.syncGuildMembers("world", "s1", setOf(actor, secondActor)) }
    }

    @Test fun `different member rebidding for same guild charges delta`() {
        service.placeBid(auction.id, AuctionLifecycleService.BidRequest(actor, 100, "ip", guildId))
        service.placeBid(auction.id, AuctionLifecycleService.BidRequest(secondActor, 120, "ip", guildId))
        verify { guilds.bankWithdraw(guildId, 20) }
        verify(exactly = 0) { guilds.bankDeposit(any(), any()) }
    }

    @Test fun `switching same actor from guild to personal refunds guild`() {
        service.placeBid(auction.id, AuctionLifecycleService.BidRequest(actor, 100, "ip", guildId))
        service.placeBid(auction.id, actor, 120, "ip")
        verify { economy.withdraw(actor, 120) }
        verify { guilds.bankDeposit(guildId, 100) }
    }

    @Test fun `failed auction save refunds the original guild payer`() {
        every { auctions.save(any()) } throws IllegalStateException("disk")
        assertIs<AuctionResult.Failure>(service.placeBid(auction.id, AuctionLifecycleService.BidRequest(actor, 100, "ip", guildId)))
        verify { guilds.bankDeposit(guildId, 100) }
        verify(exactly = 0) { economy.deposit(actor, any()) }
    }

    @Test fun `revoked guild authority closes and refunds without award`() {
        service.placeBid(auction.id, AuctionLifecycleService.BidRequest(actor, 100, "ip", guildId))
        every { guilds.isMember(actor, guildId) } returns false
        service.settleExpired()
        assertEquals(AuctionState.CLOSED, auction.state)
        verify { guilds.bankDeposit(guildId, 100) }
        verify(exactly = 0) { stalls.save(match { it.owner.type == OwnerType.GUILD }) }
        service.settleExpired()
        verify(exactly = 1) { guilds.bankDeposit(guildId, 100) }
    }

    @Test fun `unauthorised guild bids never move money`() {
        every { guilds.hasShopPermission(actor, guildId, any()) } returns false
        assertIs<AuctionResult.Failure>(service.placeBid(auction.id, AuctionLifecycleService.BidRequest(actor, 100, "ip", guildId)))
        verify(exactly = 0) { guilds.bankWithdraw(any(), any()) }
    }
}
