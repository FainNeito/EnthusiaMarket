package net.badgersmc.em.application

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketSearchQueryTest {
    @Test fun `selectors normalize case spaces and supported namespaces`() {
        assertEquals(MarketSearchQuery("DIAMOND_PICKAXE", MarketSearchQuery.Mode.ITEM),
            MarketSearchQuery.parse(" Item:diamond pickaxe "))
        assertEquals(SearchCategory.WEAPONS, MarketSearchQuery.parse("category:weapon")?.category)
        assertEquals(MarketSearchQuery.Mode.ITEM, MarketSearchQuery.parse("minecraft:stone")?.mode)
    }

    @Test fun `malformed and unknown explicit selectors never fall back to a prefix`() {
        listOf("", "a", "item:", "category:", "category:diamonds", "wrong:stone", "item:stone:extra")
            .forEach { assertNull(MarketSearchQuery.parse(it), it) }
    }

    @Test fun `all catalog aliases resolve and completions share canonical vocabulary`() {
        assertEquals(SearchCategory.entries.map { it.key }, MaterialSuggestions.searchCategories)
        SearchCategory.entries.forEach { category ->
            category.aliases.forEach { alias -> assertEquals(category, MarketSearchQuery.parse("category:$alias")?.category) }
            assertTrue("category:${category.key}" in MaterialSuggestions.matching(emptyList(), "category:"))
        }
    }

    @Test fun `explicit completion keeps item and category candidates separate`() {
        val items = listOf("STONE", "STONE_BRICKS", "DIAMOND")
        assertEquals(listOf("item:STONE", "item:STONE_BRICKS"), MaterialSuggestions.matching(items, "ITEM:sto"))
        assertEquals(listOf("category:stone", "category:storage"), MaterialSuggestions.matching(items, "category:sto"))
        assertEquals(listOf("STONE", "STONE_BRICKS"), MaterialSuggestions.matching(items, "STO").filter { it == it.uppercase() })
    }

    @Test fun `query normalization is independent of server locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("DIAMOND", MarketSearchQuery.parse("item:diamond")?.term)
        } finally { Locale.setDefault(previous) }
    }
}
