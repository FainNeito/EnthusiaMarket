package net.badgersmc.em.application

import java.util.Locale

/** Shared vocabulary for matching, completion and player-facing category names. */
enum class SearchCategory(vararg val aliases: String) {
    TOOLS("TOOL"), COMBAT, WEAPONS("WEAPON"), ARMOR("ARMOUR"),
    FOLIAGE, FLOWERS("FLOWER"), WOOD, STONE("STONES"), REDSTONE,
    BUILDING("BUILDING_BLOCKS"), DECORATION("DECORATIONS"), FOOD,
    FARMING, ORES("ORE"), ORE_BLOCKS("ORE_BLOCK"), MATERIALS("MATERIAL"),
    BREWING, POTIONS("POTION"), ENCHANTING, STORAGE, SHULKER("SHULKER_BOX"),
    TRANSPORT("TRANSPORTATION"), LIGHTING("LIGHTS"), WORKSTATIONS("WORKSTATION"), DROPS("DROP");

    val key: String get() = name.lowercase(Locale.ROOT)

    companion object {
        private val lookup = entries.flatMap { category ->
            (listOf(category.name) + category.aliases).map { it to category }
        }.toMap()

        fun resolve(name: String): SearchCategory? = lookup[name.uppercase(Locale.ROOT)]
    }
}
