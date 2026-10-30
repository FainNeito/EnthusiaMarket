package net.badgersmc.em.interaction

import net.badgersmc.em.application.AuctionLifecycleService
import net.badgersmc.em.application.AuctionResult
import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.domain.auction.AuctionId
import net.badgersmc.em.domain.auction.AuctionState
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** UI review only. Actual payment, authority and settlement remain in AuctionLifecycleService. */
internal class AuctionBidSession(val actor: UUID, private val id: AuctionId,
    private val service: AuctionLifecycleService) {
    sealed interface Review
    data class Rejected(val reason: String) : Review
    data class Ready(val auction: Auction, val amount: Long, val guildId: String?, val guildName: String?,
        internal val used: AtomicBoolean = AtomicBoolean(false)) : Review

    fun review(amount: Long, guildId: String?): Review {
        val current = service.auctionForBid(id) ?: return Rejected("Auction no longer available.")
        if (current.state != AuctionState.OPEN) return Rejected("Auction is not open.")
        val deferred = current.highBid == null && !current.auctionDuration.isZero
        if (!deferred && !current.endAt.isAfter(Instant.now())) return Rejected("Auction has ended. Reopen the auction list.")
        val minimum = AuctionBidPresentation.minimum(current.startingBid, current.highBid?.amount)
        if (amount <= 0 || minimum == null || amount < minimum) return Rejected("Bid is below the current minimum. Reopen the auction.")
        val guild = if (guildId != null) service.eligibleGuilds(actor).firstOrNull { it.id == guildId } else null
        if (guildId != null && guild == null) return Rejected("Selected guild is unavailable or you no longer have permission. Select a payer again.")
        return Ready(current, amount, guildId, guild?.let { AuctionBidPresentation.name(it.name) })
    }

    private fun changed(before: Ready, after: Ready): Boolean = listOf(
        before.auction.id to after.auction.id,
        before.auction.stallId to after.auction.stallId,
        before.auction.highBid to after.auction.highBid,
        before.auction.startingBid to after.auction.startingBid,
        before.auction.endAt to after.auction.endAt,
        before.auction.auctionDuration to after.auction.auctionDuration,
        before.guildName to after.guildName,
    ).any { (old, current) -> old != current }

    fun confirm(ready: Ready, ip: String): AuctionResult {
        if (!ready.used.compareAndSet(false, true)) return AuctionResult.Failure("This confirmation has already been used.")
        val refreshed = review(ready.amount, ready.guildId)
        if (refreshed is Rejected) return AuctionResult.Failure(refreshed.reason)
        if (refreshed is Ready && changed(ready, refreshed)) {
            return AuctionResult.Failure("Auction changed. Review your bid again.")
        }
        return service.placeBid(id, AuctionLifecycleService.BidRequest(actor, ready.amount, ip, ready.guildId))
    }
}
