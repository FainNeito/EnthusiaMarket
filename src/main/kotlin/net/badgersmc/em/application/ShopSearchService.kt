package net.badgersmc.em.application

import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta

/**
 * Matches the shop's traded item, including supported container contents.
 * Direction filters use the owner's BUY/SELL direction, independently of item matching.
 */
@Service
class ShopSearchService {

    data class Match(val material: Material, val nested: Boolean)

    enum class SearchMode {
        SELL, BUY, ANY;

        fun includes(direction: SignDirection): Boolean = this == ANY || name == direction.name

        fun next(): SearchMode = entries[(ordinal + 1) % entries.size]
    }

    /** Legacy material-only check for a SELL shop; direction-aware menus use [SearchMode.includes]. */
    fun matches(searchEnabled: Boolean, sellMaterial: Material?, query: Material, mode: SearchMode): Boolean {
        if (!searchEnabled) return false
        return sellMaterial == query && mode.includes(SignDirection.SELL)
    }

    /** Finds an exact or prefix material match in the sold item and its supported containers. */
    fun findMatch(searchEnabled: Boolean, soldItem: ItemStack, query: String): Match? {
        if (!searchEnabled) return null
        val parsed = MarketSearchQuery.parse(query) ?: return null
        var visited = 0

        fun search(item: ItemStack, depth: Int): Match? {
            if (visited++ >= MAX_ITEMS_SCANNED) return null
            if (matchesQuery(item.type, parsed)) {
                return Match(item.type, depth > 0)
            }
            if (depth >= MAX_CONTAINER_DEPTH) return null
            return containerContents(item).firstNotNullOfOrNull { search(it, depth + 1) }
        }

        return search(soldItem, 0)
    }

    private fun containerContents(item: ItemStack): List<ItemStack> {
        val meta = item.itemMeta
        val shulker = (meta as? BlockStateMeta)?.blockState as? ShulkerBox
        if (shulker != null) {
            return shulker.inventory.contents.filterNotNull().filterNot { it.type.isAir }
        }
        return (meta as? BundleMeta)?.items?.filterNot { it.type.isAir }.orEmpty()
    }

    fun tickerMaterial(query: String): Material? = MarketSearchQuery.parse(query)?.tickerItem
        ?.let(Material::matchMaterial)?.takeIf { it.isItem }

    private fun matchesQuery(material: Material, query: MarketSearchQuery): Boolean = when (query.mode) {
        MarketSearchQuery.Mode.ITEM -> material.name == query.term
        MarketSearchQuery.Mode.CATEGORY -> SearchCategoryMaterials.matches(requireNotNull(query.category), material)
        MarketSearchQuery.Mode.AUTO -> automaticMatch(material, query)
    }

    private fun automaticMatch(material: Material, query: MarketSearchQuery): Boolean {
        val category = query.category
        if (category in STRICT_LEGACY_CATEGORIES) return SearchCategoryMaterials.matches(requireNotNull(category), material)
        return material.name.startsWith(query.term) || category?.let { SearchCategoryMaterials.matches(it, material) } == true ||
            legacyWood(material.name, category)
    }

    private fun legacyWood(name: String, category: SearchCategory?) = category == SearchCategory.WOOD &&
        LEGACY_WOOD_PARTS.any(name::contains)

    companion object {
        private const val MAX_CONTAINER_DEPTH = 4
        private const val MAX_ITEMS_SCANNED = 1024
        private val STRICT_LEGACY_CATEGORIES = setOf(SearchCategory.STONE, SearchCategory.FLOWERS)
        private val LEGACY_WOOD_PARTS = setOf("WOOD", "LOG", "STEM", "PLANKS")
    }
}