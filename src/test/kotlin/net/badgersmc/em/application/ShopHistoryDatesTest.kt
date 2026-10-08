package net.badgersmc.em.application

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ShopHistoryDatesTest {
    private val zone = ZoneId.of("America/New_York")

    @Test fun `today follows server date rather than UTC date`() {
        val clock = Clock.fixed(Instant.parse("2026-10-08T02:00:00Z"), ZoneOffset.UTC)
        val window = ShopHistoryDates.today(clock, zone)
        assertEquals(Instant.parse("2026-10-07T04:00:00Z").toEpochMilli(), window.fromMs)
        assertEquals(Instant.parse("2026-10-08T04:00:00Z").toEpochMilli(), window.toMs)
    }

    @Test fun `spring and autumn dates span 23 and 25 hours`() {
        val spring = ShopHistoryDates.range("2026-03-08", "2026-03-08", zone)
        val autumn = ShopHistoryDates.range("2026-11-01", "2026-11-01", zone)
        assertEquals(23 * 3_600_000L, spring.toMs - spring.fromMs)
        assertEquals(25 * 3_600_000L, autumn.toMs - autumn.fromMs)
    }

    @Test fun `range includes leap day and entire end date`() {
        val window = ShopHistoryDates.range("2024-02-28", "2024-02-29", ZoneOffset.UTC)
        assertEquals(Instant.parse("2024-02-28T00:00:00Z").toEpochMilli(), window.fromMs)
        assertEquals(Instant.parse("2024-03-01T00:00:00Z").toEpochMilli(), window.toMs)
    }

    @Test fun `invalid impossible and reversed dates are rejected`() {
        for ((from, to) in listOf(
            "2026-02-29" to "2026-03-01", "2026-10-09" to "2026-10-08",
            "10/08/2026" to "2026-10-08", "2026-1-01" to "2026-01-02",
        )) assertFails { ShopHistoryDates.range(from, to, zone) }
    }
}
