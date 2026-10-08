package net.badgersmc.em.domain.auction

import java.time.Instant
import java.util.UUID

data class Bid(
    val bidder: UUID,
    val amount: Long,
    val placedAt: Instant,
    /** Null is a personal bid; guild identity is durable escrow/award provenance. */
    val guildId: String? = null,
) {
    init { require(amount > 0) { "Bid amount must be positive" } }
}
