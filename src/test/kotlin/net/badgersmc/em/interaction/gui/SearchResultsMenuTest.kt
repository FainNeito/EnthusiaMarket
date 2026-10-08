package net.badgersmc.em.interaction.gui

import net.badgersmc.em.application.ShopSearchService.SearchMode
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.SignDirection
import org.bukkit.Material
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchResultsMenuTest {
    private fun result(id: Long, direction: SignDirection, stock: Int = 64, cost: Int = 10) =
        SearchResultsMenu.Result(
            Shop(id, "stall1", UUID.randomUUID(), "world", 1, 65, 1, "world", 1, 64, 1,
                "item", 1, "money", cost, direction = direction, stockCount = stock),
            Material.DIAMOND, false,
        )

    @Test fun `SELL filters by owner direction`() {
        val results = listOf(result(1, SignDirection.SELL), result(2, SignDirection.BUY), result(3, SignDirection.TRADE))
        assertEquals(listOf(1L), SearchResultsMenu.filter(results, true, SearchMode.SELL).map { it.shop.id })
    }

    @Test fun `BUY includes empty shops buying supplied items`() {
        val results = listOf(result(1, SignDirection.SELL, 0), result(2, SignDirection.BUY, 0))
        assertEquals(listOf(2L), SearchResultsMenu.filter(results, false, SearchMode.BUY).map { it.shop.id })
    }

    @Test fun `direction composes with existing stock filtering and sorting`() {
        val results = listOf(result(1, SignDirection.SELL, cost = 20), result(2, SignDirection.SELL, 0),
            result(3, SignDirection.SELL, cost = 5), result(4, SignDirection.BUY))
        val visible = SearchResultsMenu.filter(results, false, SearchMode.SELL)
        assertEquals(listOf(3L, 1L), SearchResultsMenu.sort(visible, SearchResultsMenu.Sort.PRICE_LOW).map { it.shop.id })
        assertEquals(listOf(1L, 2L, 3L), SearchResultsMenu.filter(results, true, SearchMode.SELL).map { it.shop.id })
    }

    @Test fun `ANY preserves every existing direction`() {
        val results = listOf(result(1, SignDirection.SELL), result(2, SignDirection.BUY), result(3, SignDirection.TRADE))
        assertEquals(results, SearchResultsMenu.filter(results, true))
    }
}
