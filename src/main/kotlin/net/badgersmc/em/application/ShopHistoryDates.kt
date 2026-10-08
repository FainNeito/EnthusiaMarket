package net.badgersmc.em.application

import net.badgersmc.em.domain.shop.ShopHistoryWindow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Calendar boundaries use the same server zone as history timestamps, including DST. */
object ShopHistoryDates {
    fun today(clock: Clock, zone: ZoneId): ShopHistoryWindow {
        val date = LocalDate.now(clock.withZone(zone))
        return window(date, date, zone)
    }

    fun range(from: String, to: String, zone: ZoneId): ShopHistoryWindow {
        require(from.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && to.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        return window(LocalDate.parse(from), LocalDate.parse(to), zone)
    }

    private fun window(from: LocalDate, to: LocalDate, zone: ZoneId): ShopHistoryWindow {
        require(!from.isAfter(to)) { "Start date is after end date" }
        return ShopHistoryWindow(
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
}
