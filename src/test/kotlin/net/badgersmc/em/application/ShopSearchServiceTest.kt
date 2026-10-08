package net.badgersmc.em.application

import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta
import org.mockbukkit.mockbukkit.MockBukkit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.Locale

class ShopSearchServiceTest {

    private val svc = ShopSearchService()

    @Test fun `generic shulker queries find all colors`() {
        val materials = Material.entries.filter { !it.isLegacy &&
            (it == Material.SHULKER_BOX || it.name.endsWith("_SHULKER_BOX")) }
        assertEquals(17, materials.size)
        for (material in materials) {
            assertTrue(svc.findMatch(true, ItemStack(material), "shulker")?.material == material, material.name)
            assertTrue(svc.findMatch(true, ItemStack(material), "shulker_box")?.material == material, material.name)
        }
    }

    @Test fun `case insensitive material search is independent of host locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(Material.DIAMOND, svc.findMatch(true, ItemStack(Material.DIAMOND), "diamond")?.material)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `color specific shulker queries exclude other colors`() {
        assertTrue(svc.findMatch(true, ItemStack(Material.RED_SHULKER_BOX), "red_shulker_box") != null)
        assertTrue(svc.findMatch(true, ItemStack(Material.BLUE_SHULKER_BOX), "red_shulker_box") == null)
        assertTrue(svc.findMatch(true, ItemStack(Material.SHULKER_BOX), "red_shulker_box") == null)
    }

    @Test fun `generic shulker queries find colored boxes nested in bundles`() {
        val bundle = ItemStack(Material.BUNDLE)
        val meta = bundle.itemMeta as BundleMeta
        meta.addItem(ItemStack(Material.BLUE_SHULKER_BOX))
        bundle.itemMeta = meta
        val match = svc.findMatch(true, bundle, "shulker_box")
        assertTrue(match?.material == Material.BLUE_SHULKER_BOX)
        assertTrue(match?.nested == true)
        assertTrue(svc.findMatch(false, bundle, "shulker_box") == null)
    }

    @BeforeEach fun setUp() { MockBukkit.mock() }

    @AfterEach fun tearDown() { MockBukkit.unmock() }

    @Test fun `matches sell material when searchEnabled`() {
        assertTrue(svc.matches(true, Material.DIAMOND, Material.DIAMOND, ShopSearchService.SearchMode.SELL))
        assertTrue(svc.matches(true, Material.DIAMOND, Material.DIAMOND, ShopSearchService.SearchMode.ANY))
    }

    @Test fun `does not match a different material`() {
        assertFalse(svc.matches(true, Material.IRON_INGOT, Material.DIAMOND, ShopSearchService.SearchMode.SELL))
    }

    @Test fun `does not match when search disabled`() {
        assertFalse(svc.matches(false, Material.DIAMOND, Material.DIAMOND, ShopSearchService.SearchMode.SELL))
    }

    @Test fun `legacy material matcher assumes a SELL shop`() {
        assertFalse(svc.matches(true, Material.DIAMOND, Material.DIAMOND, ShopSearchService.SearchMode.BUY))
    }

    @Test fun `null sell material does not match`() {
        assertFalse(svc.matches(true, null, Material.DIAMOND, ShopSearchService.SearchMode.SELL))
    }

    @Test fun `finds an item nested inside a shulker box`() {
        val shulker = ItemStack(Material.SHULKER_BOX)
        val meta = shulker.itemMeta as BlockStateMeta
        val state = meta.blockState as ShulkerBox
        state.inventory.setItem(0, ItemStack(Material.GUNPOWDER, 64))
        meta.blockState = state
        shulker.itemMeta = meta

        val match = svc.findMatch(true, shulker, "gunpowder")

        assertTrue(match?.nested == true)
        assertTrue(match?.material == Material.GUNPOWDER)
    }

    @Test fun `supports material prefixes in nested containers`() {
        val shulker = ItemStack(Material.SHULKER_BOX)
        val meta = shulker.itemMeta as BlockStateMeta
        val state = meta.blockState as ShulkerBox
        state.inventory.setItem(0, ItemStack(Material.GUNPOWDER))
        meta.blockState = state
        shulker.itemMeta = meta

        assertTrue(svc.findMatch(true, shulker, "gunp")?.nested == true)
    }

    @Test fun `finds an item nested inside a bundle`() {
        val bundle = ItemStack(Material.BUNDLE)
        val meta = bundle.itemMeta as BundleMeta
        meta.addItem(ItemStack(Material.DIAMOND))
        bundle.itemMeta = meta

        val match = svc.findMatch(true, bundle, "diamond")

        assertTrue(match?.nested == true)
        assertTrue(match?.material == Material.DIAMOND)
    }

    @Test fun `does not inspect containers when search is disabled`() {
        assertTrue(svc.findMatch(false, ItemStack(Material.GUNPOWDER), "gunpowder") == null)
    }

    @Test fun `matches material search categories`() {
        assertTrue(svc.findMatch(true, ItemStack(Material.DIAMOND_CHESTPLATE), "armor") != null)
        assertTrue(svc.findMatch(true, ItemStack(Material.NETHERITE_PICKAXE), "tool") != null)
        assertTrue(svc.findMatch(true, ItemStack(Material.DIAMOND_SWORD), "tool") != null)
        assertTrue(svc.findMatch(true, ItemStack(Material.MACE), "weapon") != null)
        assertTrue(svc.findMatch(true, ItemStack(Material.SPLASH_POTION), "potions") != null)
    }
}
