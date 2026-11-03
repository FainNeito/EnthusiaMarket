package net.badgersmc.em.interaction

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuctionBidPresentationTest {
    @Test fun `minimum and options use full bid and do not overflow`() {
        assertEquals(1L, AuctionBidPresentation.minimum(1, null))
        assertEquals(100L, AuctionBidPresentation.minimum(100, null))
        assertEquals(151L, AuctionBidPresentation.minimum(100, 150))
        assertNull(AuctionBidPresentation.minimum(100, Long.MAX_VALUE))
        assertEquals(listOf(Long.MAX_VALUE), AuctionBidPresentation.amounts(Long.MAX_VALUE))
    }

    @Test fun `time handles deferred expired and readable durations`() {
        val now = Instant.parse("2026-10-29T12:00:00Z")
        assertEquals("Starts after the first bid", AuctionBidPresentation.time(now, Instant.MAX, true))
        assertEquals("Ended", AuctionBidPresentation.time(now, now.minusSeconds(1), false))
        assertEquals("1d 2h 3m 4s", AuctionBidPresentation.time(now, now.plusSeconds(93784), false))
    }

    @Test fun `names remain literal and unresolved guild never becomes personal`() {
        assertEquals("<red>Guild</red>", AuctionBidPresentation.name("§c<red>Guild</red>\n"))
        assertEquals("Unknown guild", AuctionBidPresentation.name(" \u0000 "))
    }
}
