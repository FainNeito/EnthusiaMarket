package net.badgersmc.em.application

import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShopSearchCategoryTest {
    private val service = ShopSearchService()

    @BeforeEach fun setUp() { MockBukkit.mock() }
    @AfterEach fun tearDown() { MockBukkit.unmock() }

    @Test fun `stone category includes building families and variants`() {
        listOf(Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
            Material.POLISHED_GRANITE, Material.DIORITE_STAIRS, Material.ANDESITE_WALL,
            Material.DEEPSLATE_TILE_SLAB, Material.CHISELED_TUFF_BRICKS,
            Material.POLISHED_BLACKSTONE_BUTTON, Material.SMOOTH_BASALT,
            Material.CALCITE, Material.DRIPSTONE_BLOCK, Material.END_STONE_BRICKS,
            Material.INFESTED_STONE_BRICKS).forEach { material ->
            assertNotNull(service.findMatch(true, ItemStack(material), "stone"), material.name)
            assertNotNull(service.findMatch(true, ItemStack(material), "STONES"), material.name)
        }
    }

    @Test fun `stone category excludes tools ores and unrelated materials`() {
        listOf(Material.STONE_SWORD, Material.STONE_PICKAXE, Material.STONECUTTER,
            Material.REDSTONE, Material.GLOWSTONE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.COAL_ORE, Material.OAK_PLANKS).forEach { material ->
            assertNull(service.findMatch(true, ItemStack(material), "stone"), material.name)
        }
        assertNotNull(service.findMatch(true, ItemStack(Material.STONE_SWORD), "stone_sword"))
    }

    @Test fun `flower categories include flowers and exclude ingredients`() {
        listOf(Material.DANDELION, Material.BLUE_ORCHID, Material.PEONY,
            Material.TORCHFLOWER, Material.PITCHER_PLANT, Material.PINK_PETALS,
            Material.OPEN_EYEBLOSSOM, Material.CLOSED_EYEBLOSSOM,
            Material.WILDFLOWERS, Material.CACTUS_FLOWER, Material.SPORE_BLOSSOM).forEach { material ->
            assertNotNull(service.findMatch(true, ItemStack(material), "flower"), material.name)
            assertNotNull(service.findMatch(true, ItemStack(material), "Flowers"), material.name)
        }
        listOf(Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD, Material.FLOWER_POT,
            Material.RED_DYE, Material.OAK_LEAVES, Material.SHORT_GRASS).forEach { material ->
            assertNull(service.findMatch(true, ItemStack(material), "flower"), material.name)
        }
    }

    @Test fun `categories retain nested matching and opt out`() {
        val box = ItemStack(Material.SHULKER_BOX)
        val meta = box.itemMeta as BlockStateMeta
        val state = meta.blockState as ShulkerBox
        state.inventory.setItem(0, ItemStack(Material.PEONY))
        meta.blockState = state
        box.itemMeta = meta
        assertTrue(service.findMatch(true, box, "flowers")?.nested == true)
        assertNull(service.findMatch(false, box, "flowers"))
        assertNotNull(service.findMatch(true, ItemStack(Material.STONE_BRICKS), "stone_br"))
    }
}
