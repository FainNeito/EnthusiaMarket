package net.badgersmc.em.infrastructure.scheduler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.application.AuctionLifecycleService
import net.badgersmc.em.application.MaintenanceFreezeService
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.domain.auction.AuctionId
import net.badgersmc.em.domain.auction.AuctionRepository
import net.badgersmc.em.domain.auction.AuctionState
import net.badgersmc.em.domain.ports.EconomyProvider
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.vault.VaultHealth
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AuctionWorkloadProfileTest {
    @Test fun `scheduler performs discovery and reminders on caller thread`() {
        val server = MockBukkit.mock()
        try {
            val f = AuctionFixture()
            val plugin = MockBukkit.createMockPlugin()
            val health = mockk<VaultHealth> { every { isAvailable } returns true }
            val freeze = mockk<MaintenanceFreezeService> { every { isFrozen() } returns false }
            val caller = Thread.currentThread()
            val reads = mutableListOf<Thread>()
            every { f.auctions.findExpired() } answers { reads.add(Thread.currentThread()); Thread.sleep(20); emptyList() }
            every { f.auctions.allOpen() } answers { reads.add(Thread.currentThread()); Thread.sleep(20); emptyList() }
            AuctionScheduler(plugin, f.lifecycle, f.auctions, health, mockk(relaxed = true), freeze).start()
            val start = System.nanoTime()
            server.scheduler.performTicks(400)
            assertEquals(listOf(caller, caller), reads)
            println("auction-profile mode=empty-tick injected-read-ms=40 " +
                "elapsed-ms=${(System.nanoTime() - start) / 1_000_000.0} thread=caller")
            verify(exactly = 0) { f.economy.withdraw(any(), any()) }
        } finally { MockBukkit.unmock() }
    }

    @Test fun `no-bid batch exposes synchronous per-auction repository work`() {
        val f = AuctionFixture()
        val expired = (1..100).map { f.expiredAuction(it) }
        val caller = Thread.currentThread()
        val threads = mutableListOf<Thread>()
        every { f.auctions.findExpired() } returns expired
        every { f.stalls.findById(any()) } answers { threads.add(Thread.currentThread()); Thread.sleep(1); null }
        val start = System.nanoTime()
        val report = f.lifecycle.settleExpired()
        assertEquals(100, report.settled)
        assertEquals(0, report.errors)
        assertEquals(List(100) { caller }, threads)
        verify(exactly = 100) { f.auctions.save(any()) }
        verify(exactly = 0) { f.economy.withdraw(any(), any()) }
        println("auction-profile mode=no-bid auctions=100 stall-reads=100 closes=100 " +
            "injected-read-ms=100 elapsed-ms=${(System.nanoTime() - start) / 1_000_000.0}")
    }
}

private class AuctionFixture {
    val auctions = mockk<AuctionRepository>(relaxed = true)
    val stalls = mockk<StallRepository>(relaxed = true)
    val economy = mockk<EconomyProvider>(relaxed = true)
    val lifecycle = AuctionLifecycleService(
        auctionRepository = auctions, stallRepository = stalls, economy = economy,
        config = EnthusiaMarketConfig(), limits = mockk(relaxed = true), sellOffers = mockk(relaxed = true),
        shops = mockk(relaxed = true), regionMembers = mockk(relaxed = true),
        ownership = mockk(relaxed = true), ipLimiter = mockk(relaxed = true), lang = mockk(relaxed = true),
    )

    fun expiredAuction(index: Int) = Auction(
        AuctionId("synthetic-$index"), StallId("synthetic-$index"), AuctionState.OPEN,
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"),
        100L, null, Duration.ofSeconds(30), Duration.ofSeconds(30),
    )
}
