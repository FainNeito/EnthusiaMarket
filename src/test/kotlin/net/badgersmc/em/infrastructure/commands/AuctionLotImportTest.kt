package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.application.AuctionLotPlan
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuctionLotImportTest {
    @Test fun `reads 50 exact prices and rejects malformed or repeated entries`() {
        val csv = (1..50).joinToString("\r\n", "\uFEFFstall_id,min_bid\r\n") { "stall_$it,${it + 9}" }
        val lots = AuctionLotImport.parse(csv)
        assertEquals((10L..59L).toList(), lots.map { it.minimumBid })
        assertEquals("stall_50", lots.last().stallId.value)
        assertFailsWith<IllegalArgumentException> { AuctionLotImport.parse("$csv\nstall_51,60") }
        assertFailsWith<IllegalArgumentException> { AuctionLotImport.parse("stall_id,min_bid\na,10\nA,20") }
        assertFailsWith<IllegalArgumentException> { AuctionLotImport.parse("stall_id,min_bid\na,9223372036854775808") }
    }

    @Test fun `confirmation is actor scoped, single use, expires and is superseded`() {
        var now = Instant.EPOCH
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
            override fun instant(): Instant = now
        }
        val plan = AuctionLotPlan(emptyList(), "opening", Duration.ofHours(1), Duration.ZERO, Duration.ZERO)
        val imports = AuctionLotImport(clock)
        val replaced = imports.remember("staff-1", plan)
        val token = imports.remember("staff-1", plan)
        assertFailsWith<IllegalArgumentException> { imports.consume("staff-1", replaced) }
        assertFailsWith<IllegalArgumentException> { imports.consume("staff-2", token) }
        assertEquals(plan, imports.consume("staff-1", token))
        assertFailsWith<IllegalArgumentException> { imports.consume("staff-1", token) }
        val expiring = imports.remember("staff-1", plan)
        now = now.plusSeconds(300)
        assertFailsWith<IllegalArgumentException> { imports.consume("staff-1", expiring) }
    }
}
