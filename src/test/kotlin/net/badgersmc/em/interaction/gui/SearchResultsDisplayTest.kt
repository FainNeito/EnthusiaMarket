package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.infrastructure.i18n.EnthusiaMarketLang
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchResultsDisplayTest {
    @TempDir lateinit var directory: Path
    private lateinit var player: PlayerMock
    private lateinit var lang: LangService
    private val stalls = mockk<StallRepository>()

    @BeforeTest fun setUp() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        player = server.addPlayer()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        ChestGui(1, "fixture")
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize(any()) } returns ItemStack(Material.REDSTONE)
        every { stalls.findByIds(any()) } returns emptyMap()
        lang = locale()
    }

    @AfterTest fun tearDown() {
        unmockkObject(ItemStackSerializer)
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun `normal BUY never promises in stock or unlimited and explains live checks`() {
        show(shop().copy(direction = SignDirection.BUY, stockCount = 0))
        val meta = player.openInventory.topInventory.getItem(9)!!.itemMeta!!
        assertEquals("Buying — Redstone", plain(meta.displayName()!!))
        val lines = meta.lore()!!.map(::plain)
        assertTrue("Capacity and funds checked when trading" in lines)
        assertTrue("Shop type: Buy" in lines)
        assertFalse(lines.any { it.contains("Unlimited") || it.contains(Int.MAX_VALUE.toString()) })
    }

    @Test fun `finite SELL and admin BUY retain existing stock semantics`() {
        show(shop().copy(stockCount = 7, sellAmount = 2))
        var meta = player.openInventory.topInventory.getItem(9)!!.itemMeta!!
        assertEquals("In Stock — Redstone", plain(meta.displayName()!!))
        assertTrue(meta.lore()!!.map(::plain).contains("Stock: 3 trades"))
        show(shop().copy(direction = SignDirection.BUY, adminShop = true))
        meta = player.openInventory.topInventory.getItem(9)!!.itemMeta!!
        assertEquals("In Stock — Redstone", plain(meta.displayName()!!))
        assertTrue(meta.lore()!!.map(::plain).contains("Stock: Unlimited trades"))
    }

    @Test fun `empty TRADE retains out of stock when explicitly included`() {
        val trade = shop().copy(direction = SignDirection.TRADE, stockCount = 0)
        SearchResultsMenu(listOf(SearchResultsMenu.Result(trade, Material.REDSTONE, false)),
            "redstone", lang, stalls, includeOutOfStock = true).open(player)
        val meta = player.openInventory.topInventory.getItem(9)!!.itemMeta!!
        assertEquals("Out of Stock — Redstone", plain(meta.displayName()!!))
        assertTrue(meta.lore()!!.map(::plain).contains("Stock: 0 trades"))
    }

    @Test fun `new neutral title bypasses legacy selling override and is separately customizable`() {
        Files.writeString(directory.resolve("lang/en_US.yml"),
            "gui:\n  shop:\n    search:\n      title: '<red>Shops selling <query>'\n")
        lang = locale()
        show(shop().copy(direction = SignDirection.BUY))
        assertEquals("Market search: redstone", plain(player.openInventory.title()))
        Files.writeString(directory.resolve("lang/en_US.yml"),
            "gui:\n  shop:\n    search:\n      title: '<red>Shops selling <query>'\n" +
                "      results_title: '<gold>Find <query>'\n")
        lang = locale()
        show(shop().copy(stockCount = 1))
        assertEquals("Find redstone", plain(player.openInventory.title()))
    }

    private fun show(shop: Shop) = SearchResultsMenu(
        listOf(SearchResultsMenu.Result(shop, Material.REDSTONE, false)), "redstone", lang, stalls,
    ).open(player)

    private fun shop() = Shop(1, "stall18", player.uniqueId, "world", -32, 121, -277,
        "world", -32, 121, -278, "redstone", 1, "gold", 11)

    private fun locale(): LangService {
        val host = object : LangHost {
            override val dataFolder = directory.toFile()
            override val resourceClassLoader = javaClass.classLoader
        }
        return LangService(host, Locale("en_US"), EnthusiaMarketLang::class.java)
    }

    private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)
}
