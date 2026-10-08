package net.badgersmc.em.interaction.gui

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SearchResultsStockFeedbackTest {
    private lateinit var player: PlayerMock
    private val lang = mockk<LangService>()
    private val stalls = mockk<StallRepository>()

    @BeforeTest fun setUp() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        player = server.addPlayer()
        player.openInventory(org.bukkit.Bukkit.createInventory(null, 9, "Previous inventory"))
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize(any()) } returns ItemStack(Material.DIAMOND)
        every { stalls.findByIds(any()) } returns emptyMap()
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }

    @AfterTest fun tearDown() {
        unmockkObject(ItemStackSerializer)
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun `matching empty shops report not in stock without opening a menu`() {
        assertRejected(listOf(result(stock = 0)), "shop.cmd.search.out_of_stock")
    }

    @Test fun `stock below one complete trade does not open a menu`() {
        assertRejected(listOf(result(stock = 63, amount = 64)), "shop.cmd.search.out_of_stock")
    }

    @Test fun `no matches retain no results feedback without opening a menu`() {
        assertRejected(emptyList(), "shop.cmd.search.none")
    }

    @Test fun `out of stock feedback preserves an already open inventory`() {
        player.openInventory(org.bukkit.Bukkit.createInventory(null, 9, "Existing menu"))
        assertRejected(listOf(result(stock = 0)), "shop.cmd.search.out_of_stock")
    }

    @Test fun `mixed search opens only stocked results`() {
        SearchResultsMenu(listOf(result(stock = 0), result(stock = 64)), "diamond", lang, stalls).open(player)
        assertEquals(54, player.openInventory.topInventory.size)
        assertEquals(1, (9..44).count { player.openInventory.topInventory.getItem(it)?.type == Material.DIAMOND })
    }

    @Test fun `explicit stock display still opens empty shops`() {
        SearchResultsMenu(listOf(result(stock = 0)), "diamond", lang, stalls, includeOutOfStock = true).open(player)
        assertEquals(Material.DIAMOND, player.openInventory.topInventory.getItem(9)?.type)
    }

    @Test fun `empty BUY and admin shops retain their availability semantics`() {
        val results = listOf(result(stock = 0, direction = SignDirection.BUY), result(stock = 0, admin = true))
        SearchResultsMenu(results, "diamond", lang, stalls).open(player)
        assertEquals(2, (9..44).count { player.openInventory.topInventory.getItem(it)?.type == Material.DIAMOND })
        verify(exactly = 0) { lang.msg("shop.cmd.search.out_of_stock", *anyVararg()) }
    }

    private fun assertRejected(results: List<SearchResultsMenu.Result>, message: String) {
        val original = player.openInventory.topInventory
        SearchResultsMenu(results, "diamond", lang, stalls).open(player)
        assertSame(original, player.openInventory.topInventory)
        verify(exactly = 1) { lang.msg(message, "query" to "diamond") }
        verify(exactly = 0) { stalls.findByIds(any()) }
        assertTrue(player.nextMessage()!!.contains(message))
    }

    private fun result(stock: Int, amount: Int = 1, direction: SignDirection = SignDirection.SELL,
        admin: Boolean = false) = SearchResultsMenu.Result(
        Shop(1L, "stall1", UUID.randomUUID(), "world", 1, 65, 1, "world", 1, 64, 1,
            "item", amount, "money", 10, direction = direction, stockCount = stock, adminShop = admin),
        Material.DIAMOND, false,
    )
}
