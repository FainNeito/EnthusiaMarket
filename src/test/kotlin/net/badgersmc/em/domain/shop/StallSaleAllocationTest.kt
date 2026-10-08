package net.badgersmc.em.domain.shop

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

internal class StallSaleAllocationTest {
    @Test fun fifoAndIntegerRevenueConservation() {
        val first = StockLot(UUID.randomUUID(), UUID.randomUUID(), 2)
        val second = StockLot(UUID.randomUUID(), UUID.randomUUID(), 5)
        val sale = StallSaleAllocation.allocate(listOf(first, second), 3, 10)
        assertEquals(listOf(2, 1), sale.map { it.quantity })
        assertEquals(listOf(first.contributor, second.contributor), sale.map { it.contributor })
        assertEquals(10, sale.sumOf { it.grossRevenue })
    }
    @Test fun unknownStockAndOverflowSafePricing() {
        val lots = listOf(StockLot(UUID.randomUUID(), null, 2), StockLot(UUID.randomUUID(), UUID.randomUUID(), 2))
        val sale = StallSaleAllocation.allocate(lots, 3, Long.MAX_VALUE)
        assertNull(sale.first().contributor)
        assertEquals(Long.MAX_VALUE, sale.sumOf { it.grossRevenue })
        assertFailsWith<IllegalArgumentException> { StallSaleAllocation.allocate(lots, 5, 1) }
    }
}
