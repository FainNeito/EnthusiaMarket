package net.badgersmc.em.application

import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta
import java.util.Locale

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
        if (!searchEnabled || query.length < MIN_QUERY_LENGTH) return null
        val normalized = query.uppercase(Locale.ROOT)
        var visited = 0

        fun search(item: ItemStack, depth: Int): Match? {
            if (visited++ >= MAX_ITEMS_SCANNED) return null
            if (matchesQuery(item.type, normalized)) {
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

    private fun matchesQuery(material: Material, query: String): Boolean =
        material.name == query || material.name.startsWith(query) || CATEGORY_MATCHERS[query]?.invoke(material) == true

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_CONTAINER_DEPTH = 4
        private const val MAX_ITEMS_SCANNED = 1024

        private val CATEGORY_MATCHERS: Map<String, (Material) -> Boolean> = mapOf(
            "SHULKER" to { it == Material.SHULKER_BOX || it.name.endsWith("_SHULKER_BOX") },
            "SHULKER_BOX" to { it == Material.SHULKER_BOX || it.name.endsWith("_SHULKER_BOX") },
            "ARMOR" to { it.name.endsWith("_HELMET") || it.name.endsWith("_CHESTPLATE") ||
                it.name.endsWith("_LEGGINGS") || it.name.endsWith("_BOOTS") || it.name.endsWith("_HORSE_ARMOR") ||
                it.name == "ELYTRA" },
            "ARMOUR" to { it.name.endsWith("_HELMET") || it.name.endsWith("_CHESTPLATE") ||
                it.name.endsWith("_LEGGINGS") || it.name.endsWith("_BOOTS") || it.name.endsWith("_HORSE_ARMOR") ||
                it.name == "ELYTRA" },
            "TOOLS" to { it.name.endsWith("_PICKAXE") || it.name.endsWith("_AXE") ||
                it.name.endsWith("_SHOVEL") || it.name.endsWith("_HOE") || it.name.endsWith("_SWORD") ||
                it.name in TOOL_MATERIALS },
            "TOOL" to { it.name.endsWith("_PICKAXE") || it.name.endsWith("_AXE") ||
                it.name.endsWith("_SHOVEL") || it.name.endsWith("_HOE") || it.name.endsWith("_SWORD") ||
                it.name in TOOL_MATERIALS },
            "WEAPONS" to { it.name.endsWith("_SWORD") || it.name.endsWith("_AXE") || it.name in WEAPON_MATERIALS },
            "WEAPON" to { it.name.endsWith("_SWORD") || it.name.endsWith("_AXE") || it.name in WEAPON_MATERIALS },
            "POTIONS" to { it.name in POTION_MATERIALS },
            "POTION" to { it.name in POTION_MATERIALS },
            "FOOD" to { it.isEdible },
            "WOOD" to { "WOOD" in it.name || "LOG" in it.name || "STEM" in it.name || "PLANKS" in it.name },
            "ORES" to { it.name.endsWith("_ORE") || it.name in ORE_MATERIALS },
            "ORE" to { it.name.endsWith("_ORE") || it.name in ORE_MATERIALS },
            "REDSTONE" to { it.name in REDSTONE_MATERIALS },
        )
        private val TOOL_MATERIALS = setOf("SHEARS", "FISHING_ROD", "FLINT_AND_STEEL", "BRUSH", "SPYGLASS")
        private val WEAPON_MATERIALS = setOf("BOW", "CROSSBOW", "TRIDENT", "MACE", "SPEAR")
        private val POTION_MATERIALS = setOf("POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW")
        private val ORE_MATERIALS = setOf("COAL", "RAW_IRON", "RAW_COPPER", "RAW_GOLD", "IRON_INGOT", "COPPER_INGOT",
            "GOLD_INGOT", "GOLD_NUGGET", "DIAMOND", "EMERALD", "LAPIS_LAZULI", "REDSTONE", "NETHER_QUARTZ")
        private val REDSTONE_MATERIALS = setOf("REDSTONE", "REDSTONE_TORCH", "REPEATER", "COMPARATOR", "OBSERVER",
            "PISTON", "STICKY_PISTON", "DISPENSER", "DROPPER", "HOPPER", "DAYLIGHT_DETECTOR", "LECTERN", "TARGET")
    }
}
