package net.badgersmc.em.infrastructure.website

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StallWeekDisplayTest {
    @Test
    fun `only the last closed New York week can display an official winner`() {
        val clock = Clock.fixed(Instant.parse("2026-11-09T04:59:59Z"), ZoneOffset.UTC)
        var body = """{"week":"2026-10-26","coverageComplete":false,"winner":{"name":"stall18"}}"""
        val display = StallWeekDisplay(clock) { body }
        assertEquals("Results unavailable", display.line())
        display.refresh()
        assertEquals("Awaiting verified sales", display.line())
        body = """{"week":"2026-10-26","coverageComplete":true,"winner":{"name":"stall18"}}"""
        display.refresh()
        assertEquals("Stall 18", display.line())
        body = """{"week":"2026-11-02","coverageComplete":true,"winner":{"name":"stall19"}}"""
        display.refresh()
        assertEquals("Results unavailable", display.line())
    }

    @Test
    fun `a complete week can be empty and a refresh failure clears the winner`() {
        val clock = Clock.fixed(Instant.parse("2026-11-09T05:00:00Z"), ZoneOffset.UTC)
        var body: String? = """{"week":"2026-11-02","coverageComplete":true,"winner":null}"""
        val display = StallWeekDisplay(clock) { checkNotNull(body) }
        display.refresh()
        assertEquals("No qualifying sales", display.line())
        body = null
        display.refresh()
        assertEquals("Results unavailable", display.line())
    }
}
