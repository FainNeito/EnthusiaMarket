package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.GuiListener
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.em.application.ContainerTradeResult
import net.badgersmc.em.application.ContainerTradeService
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.entity.PlayerMock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShopMenuInteractionTest {
    private lateinit var player: PlayerMock
    private val lang = mockk<LangService>()
    private val trades = mockk<ContainerTradeService>(relaxed = true)
    private val repository = mockk<ShopRepository>(relaxed = true)
    private lateinit var current: Shop

    @BeforeTest fun setUp() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        player = server.addPlayer()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        // IF's one-time static registration outlives a MockBukkit server instance.
        // Use its real public listener on each fresh server, with no duplicate handlers.
        ChestGui(1, "fixture")
        HandlerList.unregisterAll(plugin)
        server.pluginManager.registerEvents(GuiListener(plugin), plugin)
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize(any()) } answers {
            ItemStack(if (firstArg<String>() == "money") Material.RAW_GOLD else Material.DIAMOND)
        }
        every { lang.msg(any(), *anyVararg()) } answers {
            Component.text(firstArg<String>() + " " + secondArg<Array<Pair<String, Any>>>().joinToString())
        }
        every { lang.raw(any()) } answers { firstArg() }
        every { trades.balanceOf(any()) } returns 1_000L
        every { trades.executeSellBatch(any(), any(), any()) } returns ContainerTradeResult.Success("done")
        current = shop()
        every { repository.findById(1) } answers { current }
        every { repository.findByOwner(any()) } answers { listOf(current) }
        every { repository.upsert(any()) } answers { current = firstArg(); current }
    }

    @AfterTest fun tearDown() {
        unmockkObject(ItemStackSerializer)
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun `money purchase fits three rows and bulk selection does not trade`() {
        PurchaseMenu(current, trades, lang).open(player)
        assertEquals(27, player.openInventory.topInventory.size)
        assertEquals(Material.DIAMOND, item(11)?.type)
        assertEquals(Material.RAW_GOLD, item(15)?.type)
        assertEquals(Material.CHEST, item(21)?.type)
        assertEquals(Material.LIME_CONCRETE, item(23)?.type)
        click(21)
        click(11) // eight trades
        assertTrue(label(23).contains("(amount, 64)"), label(23))
        assertTrue(label(23).contains("(cost, 800)"), label(23))
        verify(exactly = 0) { trades.executeSellBatch(any(), any(), any()) }
        click(23)
        verify(exactly = 1) { trades.executeSellBatch(current, player.uniqueId, 8) }
    }

    @Test fun `bulk presets custom and maximum occupy separate slots`() {
        PurchaseBulkMenu(current, trades, lang).open(player)
        assertEquals(Material.RED_CONCRETE, item(13)?.type) // 32 trades exceeds balance
        assertTrue(label(13).contains("(trades, 32)"), label(13))
        assertEquals(Material.CYAN_CONCRETE, item(15)?.type)
        assertTrue(label(15).contains("bulk_custom"))
        assertTrue(label(16).contains("bulk_max"))
        click(13)
        assertEquals(27, player.openInventory.topInventory.size)
        verify(exactly = 0) { trades.executeSellBatch(any(), any(), any()) }
    }

    @Test fun `frozen purchase explains rejection and never calls trade`() {
        current = current.copy(frozen = true)
        PurchaseMenu(current, trades, lang).open(player)
        assertEquals(Material.RED_CONCRETE, item(23)?.type)
        assertTrue(lore(23).contains("unavailable_frozen"), lore(23))
        click(23)
        verify(exactly = 0) { trades.executeSellBatch(any(), any(), any()) }
    }

    @Test fun `frozen summary has no available trades`() {
        assertEquals(0, PurchaseMenu.summary(current.copy(frozen = true), player, trades).maxTrades)
    }

    @Test fun `sell summary reports money received and matching items given`() {
        player.inventory.addItem(ItemStack(Material.DIAMOND, 16))
        val summary = PurchaseMenu.summary(current.copy(direction = SignDirection.BUY), player, trades)
        assertEquals("200 currency", summary.receivedFor(2))
        assertEquals("16 items", summary.paymentFor(2))
        assertEquals(2, summary.maxTrades)
    }

    @Test fun `confirmed deletion cannot execute twice through the same callback`() {
        ShopEditMenu(current, repository, ShopManagementService(repository), lang).open(player)
        click(26)
        val gui = player.openInventory.topInventory.holder as Gui
        val confirm = gui.items.single { it.item.type == Material.RED_CONCRETE }
        val event = player.simulateInventoryClick(15)
        assertTrue(event.isCancelled)
        confirm.callAction(event)
        verify(exactly = 1) { repository.delete(1) }
    }

    @Test fun `current administrator can confirm another owners shop deletion`() {
        current = current.copy(owner = java.util.UUID.randomUUID())
        player.isOp = true
        player.addAttachment(MockBukkit.getMock()!!.pluginManager.plugins.first(), "enthusiamarket.admin.shop", true)
        ShopEditMenu(current, repository, ShopManagementService(repository), lang).open(player)
        click(26)
        click(15)
        verify(exactly = 1) { repository.delete(1) }
    }

    @Test fun `editor redraw closes after authority is revoked`() {
        ShopEditMenu(current, repository, ShopManagementService(repository), lang).open(player)
        current = current.copy(owner = java.util.UUID.randomUUID())
        click(2)
        assertNull(player.openInventory.topInventory)
        verify(exactly = 0) { repository.upsert(any()) }
    }

    @Test fun `sell menu swaps receive give and rejects missing player items`() {
        current = current.copy(direction = SignDirection.BUY)
        PurchaseMenu(current, trades, lang).open(player)
        assertEquals(Material.RAW_GOLD, item(11)?.type)
        assertEquals(Material.DIAMOND, item(15)?.type)
        assertEquals(Material.RED_CONCRETE, item(23)?.type)
        assertTrue(lore(23).contains("unavailable_items"), lore(23))
        click(23)
        verify(exactly = 0) { trades.executeBuyBatch(any(), any(), any()) }
    }

    @Test fun `edit deletion requires confirmation and cancellation preserves draft`() {
        val management = ShopManagementService(repository)
        ShopEditMenu(current, repository, management, lang).open(player)
        click(2) // quantity +1, draft only
        click(26)
        verify(exactly = 0) { repository.delete(any()) }
        assertTrue(player.openInventory.title().toString().contains("delete_confirm"))
        assertTrue(lore(13).contains("(x, 1)"), lore(13))
        click(11) // keep shop
        assertTrue(label(11).contains("(amount, 9)"), label(11))
        assertEquals(8, current.sellAmount)
    }

    @Test fun `delete confirmation rechecks owner after transfer`() {
        ShopEditMenu(current, repository, ShopManagementService(repository), lang).open(player)
        click(26)
        current = current.copy(owner = java.util.UUID.randomUUID())
        click(15)
        verify(exactly = 0) { repository.delete(any()) }
    }

    @Test fun `save rechecks current ownership and never overwrites a transferred shop`() {
        ShopEditMenu(current, repository, ShopManagementService(repository), lang).open(player)
        click(2)
        current = current.copy(owner = java.util.UUID.randomUUID())
        click(8)
        verify(exactly = 0) { repository.upsert(any()) }
        assertEquals(8, current.sellAmount)
    }

    @Test fun `back exposes draft decision and discard does not persist`() {
        ShopEditMenu(current, repository, ShopManagementService(repository), lang).open(player)
        click(4)
        click(18)
        assertTrue(player.openInventory.title().toString().contains("unsaved"))
        click(13) // discard
        verify(exactly = 0) { repository.upsert(any()) }
        assertEquals(100, current.costAmount)
    }

    @Test fun `save applies quantity and price through existing service`() {
        ShopEditMenu(current, repository, ShopManagementService(repository), lang).open(player)
        click(2)
        click(4)
        click(8)
        assertEquals(9, current.sellAmount)
        assertEquals(110, current.costAmount)
        verify(exactly = 1) { repository.upsert(any()) }
    }

    @Test fun `trade placement remains empty and does not lose legacy layout`() {
        current = current.copy(direction = SignDirection.TRADE)
        PurchaseMenu(current, trades, lang).open(player)
        assertEquals(45, player.openInventory.topInventory.size)
        assertNull(item(15))
        assertNotNull(item(31))
    }

    private fun shop() = Shop(1, "stall1", player.uniqueId, "world", 1, 64, 3,
        "world", 4, 64, 6, "diamond", 8, "money", 100, stockCount = 256)
    private fun click(index: Int) { assertTrue(player.simulateInventoryClick(index).isCancelled) }
    private fun item(index: Int) = player.openInventory.topInventory.getItem(index)
    private fun label(index: Int) = item(index)?.itemMeta?.displayName()?.let {
        PlainTextComponentSerializer.plainText().serialize(it)
    }.orEmpty()
    private fun lore(index: Int) = item(index)?.itemMeta?.lore()?.joinToString {
        PlainTextComponentSerializer.plainText().serialize(it)
    }.orEmpty()
}
