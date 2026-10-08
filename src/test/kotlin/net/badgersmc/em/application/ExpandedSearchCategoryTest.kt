package net.badgersmc.em.application

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.meta.BlockStateMeta
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpandedSearchCategoryTest {
    private val service = ShopSearchService()
    @BeforeEach fun setUp() { MockBukkit.mock() }
    @AfterEach fun tearDown() { MockBukkit.unmock() }

    @Test fun `exact lookup disambiguates stone and never expands prefixes`() {
        assertNotNull(find(Material.STONE, "item:stone"))
        assertNull(find(Material.STONE_BRICKS, "item:stone"))
        assertNull(find(Material.DIAMOND_SWORD, "item:diamond"))
        assertNotNull(find(Material.STONE_BRICKS, "stone_br"))
    }

    @Test fun `new category groups cover representative materials`() {
        mapOf("combat" to Material.SHIELD, "foliage" to Material.OAK_LEAVES,
            "farming" to Material.WHEAT_SEEDS, "brewing" to Material.NETHER_WART,
            "enchanting" to Material.ENCHANTED_BOOK, "storage" to Material.BARREL,
            "transport" to Material.POWERED_RAIL, "lighting" to Material.LANTERN,
            "workstations" to Material.SMITHING_TABLE, "drops" to Material.ROTTEN_FLESH,
            "building" to Material.PRISMARINE, "decoration" to Material.PAINTING,
            "materials" to Material.NETHERITE_INGOT, "ore_blocks" to Material.ANCIENT_DEBRIS,
            "tools" to Material.BRUSH, "weapons" to Material.MACE, "armor" to Material.WOLF_ARMOR,
            "flowers" to Material.POPPY, "wood" to Material.CHERRY_DOOR, "stone" to Material.TUFF_BRICKS,
            "redstone" to Material.LEVER, "food" to Material.BREAD, "ores" to Material.RAW_IRON,
            "potions" to Material.TIPPED_ARROW, "shulker" to Material.BLUE_SHULKER_BOX)
            .forEach { (category, material) ->
                assertNotNull(find(material, category), category)
                assertNotNull(find(material, "category:$category"), category)
            }
    }

    @Test fun `categories exclude unrelated materials and support intentional overlaps`() {
        mapOf("tools" to Material.OAK_PLANKS, "weapons" to Material.ARROW,
            "armor" to Material.SHIELD, "combat" to Material.POTION,
            "foliage" to Material.WHEAT_SEEDS, "flowers" to Material.FLOWER_POT,
            "farming" to Material.DIAMOND, "brewing" to Material.ENCHANTED_BOOK,
            "enchanting" to Material.DIAMOND_SWORD, "storage" to Material.HOPPER,
            "lighting" to Material.REPEATER, "workstations" to Material.OAK_PLANKS,
            "materials" to Material.DIAMOND_PICKAXE, "building" to Material.COPPER_INGOT)
            .forEach { (category, material) -> assertNull(find(material, "category:$category"), category) }
        listOf("tools", "weapons", "combat").forEach { assertNotNull(find(Material.IRON_AXE, "category:$it")) }
        listOf("flowers", "foliage", "decoration").forEach { assertNotNull(find(Material.POPPY, "category:$it")) }
        listOf("lighting", "decoration").forEach { assertNotNull(find(Material.RED_CANDLE, "category:$it")) }
    }

    @Test fun `legacy aliases remain supported and namespaced materials are exact`() {
        mapOf("tool" to Material.DIAMOND_SWORD, "weapon" to Material.TRIDENT,
            "armour" to Material.ELYTRA, "potion" to Material.POTION,
            "ore" to Material.IRON_INGOT, "shulker_box" to Material.RED_SHULKER_BOX,
            "flower" to Material.PEONY, "stones" to Material.COBBLESTONE)
            .forEach { (alias, material) -> assertNotNull(find(material, alias), alias) }
        assertNotNull(find(Material.MUSHROOM_STEM, "wood"))
        assertNotNull(find(Material.STONE, "item:minecraft:stone"))
        assertNull(find(Material.STONE_BRICKS, "minecraft:stone"))
    }

    @Test fun `explicit modes inspect containers respect opt out and preserve actual match`() {
        val box = ItemStack(Material.SHULKER_BOX)
        val meta = box.itemMeta as BlockStateMeta
        val state = meta.blockState as ShulkerBox
        state.inventory.setItem(0, ItemStack(Material.STONE_BRICKS))
        meta.blockState = state
        box.itemMeta = meta
        assertEquals(Material.STONE_BRICKS, service.findMatch(true, box, "category:stone")?.material)
        assertTrue(service.findMatch(true, box, "item:stone_bricks")?.nested == true)
        assertNull(service.findMatch(true, box, "item:stone"))
        assertNull(service.findMatch(false, box, "category:stone"))
    }

    @Test fun `ticker distinguishes exact items from category aggregates`() {
        assertEquals(Material.STONE, service.tickerMaterial("item:stone"))
        assertEquals(Material.DIAMOND, service.tickerMaterial("diamond"))
        assertNull(service.tickerMaterial("stone"))
        assertNull(service.tickerMaterial("category:redstone"))
        assertNull(service.tickerMaterial("item:missing_material"))
    }


    @Test fun `strict category lookup avoids prefix accidents`() {
        assertNotNull(find(Material.OAK_DOOR, "category:wood"))
        assertNull(find(Material.MUSHROOM_STEM, "category:wood"))
        assertNull(find(Material.WOODEN_AXE, "category:wood"))
        assertNotNull(find(Material.REDSTONE_LAMP, "category:redstone"))
        assertNull(find(Material.REDSTONE_ORE, "category:redstone"))
        assertNotNull(find(Material.REDSTONE_ORE, "redstone"))
        assertNotNull(find(Material.IRON_INGOT, "ores"))
        assertNull(find(Material.IRON_INGOT, "ore_blocks"))
    }

    private fun find(material: Material, query: String) = service.findMatch(true, ItemStack(material), query)
}
