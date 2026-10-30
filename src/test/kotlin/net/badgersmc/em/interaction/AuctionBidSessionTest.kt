package net.badgersmc.em.interaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.application.AuctionLifecycleService
import net.badgersmc.em.application.AuctionResult
import net.badgersmc.em.domain.auction.*
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.stall.StallId
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuctionBidSessionTest {
    private val actor = UUID.randomUUID()
    private val service = mockk<AuctionLifecycleService>()
    private val auction = Auction(AuctionId("auction1"), StallId("stall1"), AuctionState.OPEN,
        Instant.now(), Instant.now().plusSeconds(600), 100, null, Duration.ZERO, Duration.ZERO)
    private val session = AuctionBidSession(actor, auction.id, service)

    private fun seed(guilds: List<GuildProvider.GuildRef> = emptyList()) {
        every { service.auctionForBid(auction.id) } returns auction
        every { service.eligibleGuilds(actor) } returns guilds
    }

    @Test fun `preset and custom review do not dispatch or charge`() {
        seed()
        assertIs<AuctionBidSession.Ready>(session.review(100, null))
        assertIs<AuctionBidSession.Ready>(session.review(137, null))
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    @Test fun `missing selected guild fails closed without personal fallback`() {
        seed()
        assertIs<AuctionBidSession.Rejected>(session.review(100, "deleted-guild"))
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    @Test fun `confirmation rechecks revoked guild permission`() {
        seed(listOf(GuildProvider.GuildRef("g", "Guild")))
        val ready = assertIs<AuctionBidSession.Ready>(session.review(100, "g"))
        every { service.eligibleGuilds(actor) } returns emptyList()
        assertIs<AuctionResult.Failure>(session.confirm(ready, "ip"))
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    @Test fun `confirmation rejects a closed auction`() {
        seed()
        val ready = assertIs<AuctionBidSession.Ready>(session.review(100, null))
        every { service.auctionForBid(auction.id) } returns auction.copy(state = AuctionState.CLOSED)
        assertIs<AuctionResult.Failure>(session.confirm(ready, "ip"))
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    @Test fun `zero max long and new higher bid cannot be confirmed`() {
        seed()
        assertIs<AuctionBidSession.Rejected>(session.review(0, null))
        val ready = assertIs<AuctionBidSession.Ready>(session.review(100, null))
        every { service.auctionForBid(auction.id) } returns auction.copy(highBid = Bid(actor, 100, Instant.now()))
        assertIs<AuctionResult.Failure>(session.confirm(ready, "ip"))
        every { service.auctionForBid(auction.id) } returns auction.copy(highBid = Bid(actor, Long.MAX_VALUE, Instant.now()))
        assertIs<AuctionBidSession.Rejected>(session.review(Long.MAX_VALUE, null))
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    @Test fun `expired live row rejects review but deferred first bid stays available`() {
        seed()
        every { service.auctionForBid(auction.id) } returns auction.copy(endAt = Instant.now().minusSeconds(1))
        assertIs<AuctionBidSession.Rejected>(session.review(100, null))
        every { service.auctionForBid(auction.id) } returns auction.copy(endAt = Instant.MAX, auctionDuration = Duration.ofHours(1))
        assertIs<AuctionBidSession.Ready>(session.review(100, null))
    }

    @Test fun `changed review details require another review even when bid still meets minimum`() {
        seed(listOf(GuildProvider.GuildRef("g", "Guild")))
        val ready = assertIs<AuctionBidSession.Ready>(session.review(500, "g"))
        every { service.auctionForBid(auction.id) } returns auction.copy(highBid = Bid(actor, 200, Instant.now()))
        assertIs<AuctionResult.Failure>(session.confirm(ready, "ip"))
        val renamed = assertIs<AuctionBidSession.Ready>(session.review(500, "g"))
        every { service.eligibleGuilds(actor) } returns listOf(GuildProvider.GuildRef("g", "Renamed"))
        assertIs<AuctionResult.Failure>(session.confirm(renamed, "ip"))
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    @Test fun `confirmed full bid retains guild id and is single use`() {
        seed(listOf(GuildProvider.GuildRef("g", "Guild")))
        val ready = assertIs<AuctionBidSession.Ready>(session.review(150, "g"))
        every { service.placeBid(auction.id, any<AuctionLifecycleService.BidRequest>()) } returns AuctionResult.Success(auction)
        assertIs<AuctionResult.Success>(session.confirm(ready, "ip"))
        assertIs<AuctionResult.Failure>(session.confirm(ready, "ip"))
        verify(exactly = 1) { service.placeBid(auction.id, AuctionLifecycleService.BidRequest(actor, 150, "ip", "g")) }
        assertEquals("Guild", ready.guildName)
    }
}
