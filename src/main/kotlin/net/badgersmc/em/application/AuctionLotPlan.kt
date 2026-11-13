package net.badgersmc.em.application

import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import java.time.Duration

internal data class AuctionLot(val stallId: StallId, val minimumBid: Long)

internal data class AuctionLotPlan(
    val lots: List<Pair<Stall, AuctionLot>>,
    val round: String,
    val duration: Duration,
    val antiSnipeWindow: Duration,
    val antiSnipeExtension: Duration,
)
