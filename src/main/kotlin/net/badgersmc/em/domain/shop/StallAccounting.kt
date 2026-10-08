package net.badgersmc.em.domain.shop

import java.math.BigInteger
import java.util.UUID

data class StockLot(val id: UUID, val contributor: UUID?, val quantity: Int)
data class SaleAllocation(val lotId: UUID, val contributor: UUID?, val quantity: Int, val grossRevenue: Long)

/** Deterministic FIFO allocation conserves units and the full integer payment. */
object StallSaleAllocation {
    fun allocate(lots: List<StockLot>, quantity: Int, grossRevenue: Long): List<SaleAllocation> {
        require(quantity > 0 && grossRevenue >= 0)
        require(lots.all { it.quantity > 0 })
        require(lots.sumOf { it.quantity.toLong() } >= quantity)
        var consumed = 0
        return lots.mapNotNull { lot ->
            val used = minOf(lot.quantity, quantity - consumed)
            if (used == 0) null else {
                val start = consumed; consumed += used
                fun share(count: Int) = BigInteger.valueOf(grossRevenue).multiply(BigInteger.valueOf(count.toLong()))
                    .divide(BigInteger.valueOf(quantity.toLong())).longValueExact()
                SaleAllocation(lot.id, lot.contributor, used, share(consumed) - share(start))
            }
        }
    }
}

/** before/after quantities describe the captured inventory, not a later live lookup. */
data class StallAccountingObservation(
    val recordingId: UUID,
    val guildId: String,
    val stallId: String,
    val shopId: Long,
    val itemKey: String,
    val itemName: String,
    val contributor: UUID?,
    val before: Int,
    val after: Int,
    val saleQuantity: Int = 0,
    val grossRevenue: Long = 0,
    val createdAt: Long,
)

data class ContributorSales(val contributor: UUID?, val itemName: String, val stocked: Long, val quantity: Long, val grossRevenue: Long)
interface StallAccountingRepository {
    fun apply(observation: StallAccountingObservation)
    fun report(guildId: String, stallId: String, window: ShopHistoryWindow): List<ContributorSales>
}
