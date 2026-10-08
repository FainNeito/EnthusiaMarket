package net.badgersmc.em.domain.shop

import java.util.UUID

/** Average price stats for a sell item over a time window. */
data class PriceStats(val avgPrice: Double, val sampleCount: Int)

/** One consistent unread-sale snapshot; ids above [lastId] must stay unread. */
data class PendingShopSales(val count: Int, val lastId: Long)

/** Aggregated price-change data for the search results ticker icon. */
data class PriceTicker(
    val avgPrice: Double,
    val sampleCount: Int,
    val change24h: Double?,
    val change7d: Double?,
    val change30d: Double?,
)

interface ShopTransactionRepository {
    fun record(tx: ShopTransaction): ShopTransaction
    /** Atomically records a durable receipt and history; replay must survive history pruning. */
    fun recordOnce(recordingId: UUID, tx: ShopTransaction) {
        throw UnsupportedOperationException("Replay-safe history is not supported by this repository")
    }
    /** Newest-first, paged. */
    fun findByOwner(owner: UUID, limit: Int, offset: Int): List<ShopTransaction>
    /** Transactions where player was owner OR buyer (for members). */
    fun findByOwnerOrBuyer(player: UUID, limit: Int, offset: Int): List<ShopTransaction>
    fun countUnnotified(owner: UUID): Int
    fun markNotified(owner: UUID)
    fun pendingSales(owner: UUID): PendingShopSales =
        throw UnsupportedOperationException("Unread sale snapshots are not supported by this repository")
    fun markNotifiedThrough(owner: UUID, lastId: Long) {
        throw UnsupportedOperationException("Bounded sale acknowledgement is not supported by this repository")
    }
    /** Delete rows older than [beforeMs]; returns rows removed. */
    fun prune(beforeMs: Long): Int
    /** Average sell price for [item] between [fromMs] (inclusive) and [toMs] (exclusive). */
    fun avgPriceInWindow(item: String, fromMs: Long, toMs: Long): PriceStats?
}
