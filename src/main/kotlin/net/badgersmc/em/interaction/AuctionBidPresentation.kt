package net.badgersmc.em.interaction

import java.time.Duration
import java.time.Instant

/** Pure presentation arithmetic; amounts are full bids in the service's existing units. */
internal object AuctionBidPresentation {
    fun minimum(starting: Long, high: Long?): Long? =
        if (high == Long.MAX_VALUE) null else maxOf(starting, high?.plus(1) ?: starting)

    fun amounts(minimum: Long): List<Long> = listOf(0L, 10L, 100L, 1000L)
        .filter { it <= Long.MAX_VALUE - minimum }.map { minimum + it }

    fun name(value: String?): String = value?.replace(Regex("§[0-9a-fk-orx]", RegexOption.IGNORE_CASE), "")
        ?.filterNot { it.isISOControl() }?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown guild"

    fun time(now: Instant, end: Instant, deferred: Boolean): String {
        if (deferred) return "Starts after the first bid"
        val duration = Duration.between(now, end)
        if (duration.isNegative || duration.isZero) return "Ended"
        return buildList {
            if (duration.toDays() > 0) add("${duration.toDays()}d")
            if (duration.toHours() % 24 > 0) add("${duration.toHours() % 24}h")
            if (duration.toMinutes() % 60 > 0) add("${duration.toMinutes() % 60}m")
            add("${duration.seconds % 60}s")
        }.joinToString(" ")
    }
}
