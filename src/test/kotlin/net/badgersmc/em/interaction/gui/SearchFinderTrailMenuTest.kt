package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.GuiListener
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.HandlerList
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

class SearchFinderTrailMenuTest {
    private lateinit var player: PlayerMock
    private lateinit var plugin: JavaPlugin
    private val lang = mockk<LangService>()
    private val stalls = mockk<StallRepository>()
    private var selected = 0

    @BeforeTest fun setUp() {
        val server = MockBukkit.mock()
        plugin = MockBukkit.createMockPlugin()
        player = server.addPlayer()
        player.addAttachment(plugin, "enthusiamarket.admin.shop", false)
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize(any()) } returns ItemStack(Material.DIAMOND)
        every { stalls.findByIds(any()) } returns emptyMap()
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        ChestGui(1, "fixture")
        HandlerList.unregisterAll(plugin)
        server.pluginManager.registerEvents(GuiListener(plugin), plugin)
    }

    @AfterTest fun tearDown() {
        unmockkObject(ItemStackSerializer)
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun `ordinary result click starts guide without teleport`() {
        val before = player.location.clone()
        menu().open(player)
        assertTrue(player.simulateInventoryClick(9).isCancelled)
        assertEquals(1, selected)
        assertEquals(before, player.location)
    }

    @Test fun `sort and page controls retain navigation callback`() {
        menu().open(player)
        player.simulateInventoryClick(1)
        player.simulateInventoryClick(51)
        player.simulateInventoryClick(9)
        assertEquals(1, selected)
    }

    @Test fun `staff shift click guides while normal click retains teleport`() {
        player.isOp = true
        player.addAttachment(plugin, "enthusiamarket.admin.shop", true)
        val before = player.location.clone()
        menu().open(player)
        player.simulateInventoryClick(player.openInventory, ClickType.SHIFT_LEFT, 9)
        assertEquals(1, selected)
        assertEquals(before, player.location)
        menu().open(player)
        player.simulateInventoryClick(9)
        assertEquals(1, selected)
        assertTrue(player.location != before)
    }

    private fun menu() = SearchResultsMenu((1L..40L).map { id ->
        SearchResultsMenu.Result(Shop(id, "stall1", UUID.randomUUID(), player.world.name,
            id.toInt() + 20, 65, 1, player.world.name, 1, 64, 1,
            "item", 1, "money", 10, stockCount = 64), Material.DIAMOND, false)
    }, "diamond", lang, stalls, navigate = { _, _ -> selected++ })
}
