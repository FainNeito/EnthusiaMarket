package net.badgersmc.em.domain.auction

import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.Stall

interface AuctionRepository {
    fun findById(id: AuctionId): Auction?
    fun findOpenByStall(stallId: StallId): Auction?
    fun allOpen(): List<Auction>
    /** Includes expired auctions until settlement resolves their escrow. */
    fun hasOtherOpenGuildBid(guildId: String, auctionId: AuctionId): Boolean
    fun hasGuildOpeningRoundWin(roundId: String, guildId: String): Boolean
    fun findExpired(): List<Auction>
    fun create(auction: Auction)
    /** Opens all lots and transitions their stalls together. Returns the first conflicting stall. */
    fun openImportedLots(lots: List<Pair<Stall, Auction>>): StallId?
    fun save(auction: Auction)
    fun delete(id: AuctionId)
    /** Most recent CLOSED auction for [stallId] by end_at desc, or null. */
    fun findMostRecentClosedByStall(stallId: StallId): Auction?
    /** All auctions for [stallId] regardless of state. */
    fun findByStall(stallId: StallId): List<Auction>
}
