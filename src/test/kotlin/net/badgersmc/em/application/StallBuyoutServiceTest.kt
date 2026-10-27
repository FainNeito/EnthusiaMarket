package net.badgersmc.em.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import net.badgersmc.em.application.IpLimiter
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.EconomyProvider
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.MarketMutationGate
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.ports.SchematicService
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StallBuyoutServiceTest {

    private val player = UUID.randomUUID()
    private val stallId = StallId("stall_01")

    private fun unownedStall() = Stall(
        id = stallId, regionId = "stall_01", world = "world",
        state = StallState.UNOWNED, owner = OwnerRef.unowned(),
        ownerSince = null, winningBid = 0L, rentTerms = RentTerms.formula(1.0),
    )

    private data class ServiceWithMocks(
        val service: StallBuyoutService,
        val economy: EconomyProvider,
    )

    private fun buildService(
        stall: Stall = unownedStall(),
        claimDecision: LimitResolutionService.ClaimDecision = LimitResolutionService.ClaimDecision.Allowed,
        ownedByPlayer: List<Stall> = emptyList(),
        economyWithdrawOk: Boolean = true,
        mutationGate: MarketMutationGate = MarketMutationGate.Open,
        schematics: SchematicService = SchematicService.Disabled,
        config: EnthusiaMarketConfig = EnthusiaMarketConfig().apply { this.schematics.enabled = false },
        guildProvider: GuildProvider = mockk(relaxed = true),
    ): ServiceWithMocks {
        val stalls = mockk<StallRepository>(relaxUnitFun = true)
        every { stalls.findById(stallId) } returns stall
        every { stalls.all() } returns ownedByPlayer

        val auctions = mockk<net.badgersmc.em.domain.auction.AuctionRepository>(relaxed = true)
        every { auctions.findOpenByStall(stallId) } returns null

        val economy = mockk<EconomyProvider>()
        every { economy.withdraw(any(), any()) } returns economyWithdrawOk

        val limits = mockk<LimitResolutionService>(relaxed = true)
        every { limits.canClaim(any(), any(), any(), any()) } returns claimDecision

        val ownership = mockk<StallOwnershipCounter>(relaxed = true)
        every { ownership.counts(player) } returns StallOwnershipCounter.OwnedCounts(
            total = ownedByPlayer.size,
            byKind = ownedByPlayer.groupingBy { it.kind }.eachCount(),
        )

        val svc = StallBuyoutService(
            stalls = stalls,
            offers = mockk(relaxed = true),
            auctions = auctions,
            economy = economy,
            config = config,
            guildProvider = guildProvider,
            regionMembers = mockk(relaxed = true),
            limits = limits,
            ownership = ownership,
            ipLimiter = mockk<IpLimiter>(relaxed = true).also { every { it.acquireStall(any(), any()) } returns IpLimiter.Attempt(true, null) },
            mutationGate = mutationGate,
            schematics = schematics,
        )
        return ServiceWithMocks(svc, economy)
    }

    @Test
    fun `rebuying a vacant expired stall does not inherit former delegated access`() {
        val formerMember = UUID.randomUUID()
        val dirtyVacant = unownedStall().copy(members = setOf(formerMember))
        val (service, _) = buildService(stall = dirtyVacant)
        val result = assertIs<StallBuyoutService.Result.Purchased>(
            service.buy(stallId, player, 100L, "1.2.3.4"),
        )
        assertTrue(result.stall.members.isEmpty())
        kotlin.test.assertFalse(result.stall.canManage(formerMember, mockk(relaxed = true)))
        assertEquals(OwnerRef.solo(player), result.stall.owner)
    }

    @Test
    fun `snapshot failure rejects direct purchase before payment`() {
        val snapshot = mockk<SchematicService>()
        every { snapshot.capture(stallId.value, "world", "stall_01") } returns
            SchematicService.Result.Failure(IllegalStateException("capture failed"))
        val (service, economy) = buildService(schematics = snapshot, config = EnthusiaMarketConfig())

        assertIs<StallBuyoutService.Result.Rejected>(service.buy(stallId, player, 100L, "1.2.3.4"))
        verify(exactly = 1) { snapshot.capture(stallId.value, "world", "stall_01") }
        verify(exactly = 0) { economy.withdraw(any(), any()) }
    }

    @Test
    fun `direct purchase captures before charging`() {
        val snapshot = mockk<SchematicService>()
        every { snapshot.capture(stallId.value, "world", "stall_01") } returns SchematicService.Result.Success
        val (service, economy) = buildService(schematics = snapshot, config = EnthusiaMarketConfig())

        assertIs<StallBuyoutService.Result.Purchased>(service.buy(stallId, player, 100L, "1.2.3.4"))
        verifyOrder {
            snapshot.capture(stallId.value, "world", "stall_01")
            economy.withdraw(player, 100L)
        }
    }

    @Test
    fun `guild purchase snapshot failure never charges the actor`() {
        val snapshot = mockk<SchematicService>()
        every { snapshot.capture(stallId.value, "world", "stall_01") } returns
            SchematicService.Result.Failure(IllegalStateException("capture failed"))
        val guilds = mockk<GuildProvider>(relaxed = true)
        every { guilds.guildOf(player) } returns GuildProvider.GuildRef("guild1", "Test Guild")
        every { guilds.hasShopPermission(player, "guild1", GuildProvider.GuildPermission.MANAGE_SHOPS) } returns true
        val (service, economy) = buildService(schematics = snapshot, guildProvider = guilds, config = EnthusiaMarketConfig())

        assertIs<StallBuyoutService.Result.Rejected>(service.buyForGuild(stallId, player, 100L, "1.2.3.4"))
        verify(exactly = 0) { economy.withdraw(any(), any()) }
    }

    @Test
    fun `disabled snapshots are not called for direct purchase`() {
        val snapshot = mockk<SchematicService>()
        val config = EnthusiaMarketConfig().apply { this.schematics.enabled = false }
        val (service, economy) = buildService(schematics = snapshot, config = config)

        assertIs<StallBuyoutService.Result.Purchased>(service.buy(stallId, player, 100L, "1.2.3.4"))
        verify(exactly = 0) { snapshot.capture(any(), any(), any()) }
        verify(exactly = 1) { economy.withdraw(player, 100L) }
    }

    @Test
    fun `unavailable snapshot provider rejects when snapshots enabled`() {
        val (service, economy) = buildService(config = EnthusiaMarketConfig())

        assertIs<StallBuyoutService.Result.Rejected>(service.buy(stallId, player, 100L, "1.2.3.4"))
        verify(exactly = 0) { economy.withdraw(any(), any()) }
    }

    @Test
    fun `buy rejects when player at total cap and never charges`() {
        val (svc, economy) = buildService(
            claimDecision = LimitResolutionService.ClaimDecision.Rejected.TotalCapReached(3),
        )
        val result = svc.buy(stallId, player, 100L, "1.2.3.4")
        assertIs<StallBuyoutService.Result.Rejected>(result)
        assertTrue(result.reason.contains("limit reached", ignoreCase = true))
        verify(exactly = 0) { economy.withdraw(any(), any()) }
    }

    @Test
    fun `buy rejects when player at kind cap and never charges`() {
        val (svc, economy) = buildService(
            claimDecision = LimitResolutionService.ClaimDecision.Rejected.KindCapReached("default", 2),
        )
        val result = svc.buy(stallId, player, 100L, "1.2.3.4")
        assertIs<StallBuyoutService.Result.Rejected>(result)
        assertTrue(result.reason.contains("default", ignoreCase = true))
        verify(exactly = 0) { economy.withdraw(any(), any()) }
    }

    @Test
    fun `buy proceeds when under cap`() {
        val (svc, economy) = buildService(
            claimDecision = LimitResolutionService.ClaimDecision.Allowed,
            economyWithdrawOk = true,
        )
        val result = assertIs<StallBuyoutService.Result.Purchased>(svc.buy(stallId, player, 100L, "1.2.3.4"))
        assertNotNull(result.stall.nextRentAt)
        verify { economy.withdraw(player, 100L) }
    }

    @Test
    fun `buy rejects a moderated stall before charging`() {
        val gate = object : MarketMutationGate {
            override fun isStallLocked(stallId: String): Boolean = true
        }
        val (service, economy) = buildService(mutationGate = gate)

        val result = service.buy(stallId, player, 100L, "1.2.3.4")

        assertIs<StallBuyoutService.Result.Rejected>(result)
        verify(exactly = 0) { economy.withdraw(any(), any()) }
    }

    @Test
    fun `guild buy skips limit gate`() {
        val guildProvider = mockk<GuildProvider>(relaxed = true)
        every { guildProvider.guildOf(player) } returns mockk(relaxed = true) { every { id } returns "guild1" }
        every { guildProvider.hasShopPermission(any(), any(), any()) } returns true

        val stalls = mockk<StallRepository>(relaxUnitFun = true)
        every { stalls.findById(stallId) } returns unownedStall()
        every { stalls.all() } returns emptyList()

        val auctions = mockk<net.badgersmc.em.domain.auction.AuctionRepository>(relaxed = true)
        every { auctions.findOpenByStall(stallId) } returns null

        val economy = mockk<EconomyProvider>()
        every { economy.withdraw(any(), any()) } returns true

        val limits = mockk<LimitResolutionService>(relaxed = true)
        every { limits.canClaim(any(), any(), any(), any()) } returns
            LimitResolutionService.ClaimDecision.Rejected.TotalCapReached(0)

        val ownership = mockk<StallOwnershipCounter>(relaxed = true)
        every { ownership.counts(player) } returns StallOwnershipCounter.OwnedCounts(0, emptyMap())

        val svc = StallBuyoutService(
            stalls = stalls, offers = mockk(relaxed = true), auctions = auctions,
            economy = economy, config = EnthusiaMarketConfig().apply { schematics.enabled = false },
            guildProvider = guildProvider, regionMembers = mockk(relaxed = true),
            limits = limits, ownership = ownership,
            ipLimiter = mockk<IpLimiter>(relaxed = true).also { every { it.acquireStall(any(), any()) } returns IpLimiter.Attempt(true, null) },
        )

        // claimDecision is TotalCapReached(0), but the guild buy (owner.type == GUILD) skips the
        // SOLO-only limit gate entirely — with the happy-path mocks it proceeds to Purchased.
        val result = svc.buyForGuild(stallId, player, 100L, "1.2.3.4")
        assertIs<StallBuyoutService.Result.Purchased>(result)
    }
}
