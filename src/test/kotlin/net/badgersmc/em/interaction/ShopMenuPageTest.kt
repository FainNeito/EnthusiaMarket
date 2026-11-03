package net.badgersmc.em.interaction

import kotlin.test.Test
import kotlin.test.assertEquals

class ShopMenuPageTest {
    @Test fun `boundaries expose every stable ID exactly once`() {
        for (count in listOf(0, 45, 46, 90, 91)) {
            val rows = (1L..count.toLong()).toList().reversed()
            val first = ShopMenuPage.of(rows, 0) { it }
            val expectedPages = maxOf(1, (count + 44) / 45)
            assertEquals(expectedPages, first.pages)
            val seen = (0 until first.pages).flatMap { ShopMenuPage.of(rows, it) { id -> id }.items }
            assertEquals(rows.sorted(), seen)
            assertEquals(0, ShopMenuPage.of(rows, -1) { it }.index)
            assertEquals(expectedPages - 1, ShopMenuPage.of(rows, Int.MAX_VALUE) { it }.index)
        }
    }

    @Test fun `deletion and permission filtering clamp to last remaining page`() {
        val rows = (1L..91L).toList()
        assertEquals(listOf(91L), ShopMenuPage.of(rows, 2) { it }.items)
        val smaller = ShopMenuPage.of(rows.dropLast(1), 2) { it }
        assertEquals(1, smaller.index)
        assertEquals((46L..90L).toList(), smaller.items)
        assertEquals(0, ShopMenuPage.of(emptyList<Long>(), smaller.index) { it }.index)
    }
}
