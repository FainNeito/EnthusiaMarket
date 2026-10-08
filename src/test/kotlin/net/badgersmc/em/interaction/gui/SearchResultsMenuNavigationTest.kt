package net.badgersmc.em.interaction.gui

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
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchResultsMenuNavigationTest {
    private lateinit var player: PlayerMock
    private val lang = mockk<LangService>()
    private val stalls = mockk<StallRepository>()

    @BeforeTest fun setUp() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        player = server.addPlayer()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        com.github.stefvanschie.inventoryframework.gui.type.ChestGui(1, "fixture")
        org.bukkit.event.HandlerList.unregisterAll(plugin)
        server.pluginManager.registerEvents(com.github.stefvanschie.inventoryframework.gui.GuiListener(plugin), plugin)
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize(any()) } returns ItemStack(Material.DIAMOND)
        every { stalls.findByIds(any()) } returns emptyMap()
        every { lang.msg(any(), *anyVararg()) } answers {
            Component.text(firstArg<String>() + " " + secondArg<Array<Pair<String, Any>>>().joinToString())
        }
    }

    @AfterTest fun tearDown() {
        unmockkObject(ItemStackSerializer)
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun `actual controls retain direction through sort stock and page then reset on change`() {
        val results = (1L..40L).map { result(it, SignDirection.SELL) } +
            result(41, SignDirection.SELL, 0) + result(42, SignDirection.BUY, 0) +
            result(43, SignDirection.BUY, 0) + result(44, SignDirection.TRADE)
        SearchResultsMenu(results, "diamond", lang, stalls, page = 2).open(player)
        assertTrue(pageLabel().contains("(page, 2)"))

        click(4) // ANY -> SELL, reset to first page
        assertPage(1, 40)
        assertTrue(directionLabel().contains("direction_sell"))
        click(1) // sorting
        assertPage(1, 40)
        assertTrue(directionLabel().contains("direction_sell"))
        click(3) // include out of stock
        assertPage(1, 41)
        click(51) // next page
        assertPage(2, 41)
        assertTrue(directionLabel().contains("direction_sell"))
        assertEquals(5, resultCount())

        click(4) // SELL -> BUY; empty BUY shops remain listed
        assertPage(1, 2)
        assertTrue(directionLabel().contains("direction_buy"))
        assertEquals(2, resultCount())
        click(4) // BUY -> ANY; retained out-of-stock toggle includes all 44
        assertPage(1, 44)
        assertTrue(directionLabel().contains("direction_any"))
    }

    private fun click(slot: Int) {
        assertTrue(player.simulateInventoryClick(slot).isCancelled)
    }

    private fun assertPage(page: Int, count: Int) {
        assertTrue(pageLabel().contains("(page, $page)"), pageLabel())
        assertTrue(pageLabel().contains("(count, $count)"), pageLabel())
    }

    private fun pageLabel() = PlainTextComponentSerializer.plainText().serialize(
        player.openInventory.topInventory.getItem(49)!!.itemMeta!!.displayName()!!,
    )

    private fun directionLabel() = player.openInventory.topInventory.getItem(4)!!.itemMeta!!.lore()!!
        .joinToString { PlainTextComponentSerializer.plainText().serialize(it) }

    private fun resultCount() = (9..44).count { player.openInventory.topInventory.getItem(it)?.type == Material.DIAMOND }

    private fun result(id: Long, direction: SignDirection, stock: Int = 64) = SearchResultsMenu.Result(
        Shop(id, "stall1", UUID.randomUUID(), "world", id.toInt(), 65, 1, "world", 1, 64, 1,
            "item", 1, "money", 10, direction = direction, stockCount = stock), Material.DIAMOND, false,
    )
}
