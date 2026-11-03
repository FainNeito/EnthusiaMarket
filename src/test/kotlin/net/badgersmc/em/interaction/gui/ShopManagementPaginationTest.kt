package net.badgersmc.em.interaction.gui

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.interaction.Menu
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.plugin.java.JavaPlugin
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShopManagementPaginationTest {
    private lateinit var player: PlayerMock
    private lateinit var permission: PermissionAttachment
    private lateinit var management: ShopManagementService
    private val repository = mockk<ShopRepository>()
    private val lang = mockk<LangService>()
    private val rows = linkedMapOf<Long, Shop>()
    private val target = UUID.randomUUID()

    @BeforeTest fun setUp() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        player = server.addPlayer()
        permission = player.addAttachment(plugin, "enthusiamarket.shop.use", true)!!
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        com.github.stefvanschie.inventoryframework.gui.type.ChestGui(1, "fixture")
        org.bukkit.event.HandlerList.unregisterAll(plugin)
        server.pluginManager.registerEvents(com.github.stefvanschie.inventoryframework.gui.GuiListener(plugin), plugin)
        configureFixtures()
        management = ShopManagementService(repository)
    }

    private fun configureFixtures() {
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize(any()) } answers { ItemStack(Material.DIAMOND) }
        every { lang.msg(any(), *anyVararg()) } answers {
            Component.text(firstArg<String>() + " " + secondArg<Array<Pair<String, Any>>>().joinToString())
        }
        every { lang.msg("gui.shop.trust.selected", *anyVararg()) } returns Component.text("Selected")
        every { lang.msg("gui.shop.trust.not_selected", *anyVararg()) } returns Component.text("Not selected")
        every { repository.findByOwner(any()) } answers {
            rows.values.filter { it.owner == firstArg<UUID>() }.reversed()
        }
        every { repository.findById(any()) } answers { rows[firstArg<Long>()] }
        every { repository.delete(any()) } answers { rows.remove(firstArg<Long>()); Unit }
        every { repository.upsert(any()) } answers {
            firstArg<Shop>().also { rows[it.id] = it }
        }
    }

    @AfterTest fun tearDown() {
        unmockkObject(ItemStackSerializer)
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun `all Java menus expose boundary pages in stable order`() {
        for (count in listOf(0, 45, 46, 90, 91)) {
            for (menu in listOf<Menu>(owned(), delete(), trust())) {
                seed(count)
                menu.open(player)
                val seen = mutableListOf<Int>()
                val pages = maxOf(1, (count + 44) / 45)
                repeat(pages) { index ->
                    assertTrue(label(49).contains("(page, ${index + 1})"))
                    assertTrue(label(49).contains("(pages, $pages)"))
                    assertEquals(index > 0, item(45)?.type == Material.ARROW)
                    assertEquals(index + 1 < pages, item(51)?.type == Material.ARROW)
                    for (slot in 0..44) {
                        if (item(slot)?.type == Material.DIAMOND) {
                            val id = Regex("\\(x, (\\d+)\\)").find(label(slot))!!.groupValues[1].toInt()
                            seen.add(id)
                        }
                    }
                    if (index + 1 < pages) click(51)
                }
                assertEquals((1..count).toList(), seen)
                player.closeInventory()
            }
        }
    }

    @Test fun `bulk selection survives round trip and confirmation spans pages`() {
        seed(91)
        trust().open(player)
        assertTrue(label(0).contains("(sel, Not selected)"))
        click(0)
        assertTrue(label(0).contains("(sel, Selected)"))
        assertTrue(rows.getValue(1L).trusted.isEmpty())
        click(0)
        assertTrue(label(0).contains("(sel, Not selected)"))
        verify(exactly = 0) { repository.upsert(any()) }
        click(0)
        click(51)
        click(0)
        click(51)
        click(0)
        click(45)
        click(45)
        assertTrue(label(0).contains("(sel, Selected)"))
        click(53)
        assertEquals(setOf(1L, 46L, 91L), rows.values.filter { target in it.trusted }.map { it.id }.toSet())
    }

    @Test fun `delete last page entry clamps to previous page`() {
        seed(46)
        delete().open(player)
        click(51)
        click(0)
        click(0)
        assertFalse(rows.containsKey(46L))
        assertTrue(label(49).contains("(page, 1)"))
        assertNull(item(51))
        assertEquals(45, (0..44).count { item(it)?.type == Material.DIAMOND })
    }

    @Test fun `refresh drops revoked bulk IDs but preserves eligible selection on other pages`() {
        seed(46)
        trust().open(player)
        click(0)
        rows[1L] = rows.getValue(1L).copy(owner = UUID.randomUUID())
        click(51) // refreshed list has 45 rows and clamps back to page 1
        click(44) // ID 46
        click(53)
        assertTrue(rows.getValue(1L).trusted.isEmpty())
        assertEquals(setOf(target), rows.getValue(46L).trusted)
    }

    @Test fun `confirmation rechecks ownership even without a page refresh`() {
        seed(1)
        trust().open(player)
        click(0)
        rows[1L] = rows.getValue(1L).copy(owner = UUID.randomUUID())
        click(53)
        assertTrue(rows.getValue(1L).trusted.isEmpty())
        verify(exactly = 0) { repository.upsert(any()) }
    }

    @Test fun `stale edit entry resolves captured ID rather than shifted row`() {
        seed(46)
        owned().open(player)
        click(51)
        rows.remove(1L)
        rows.remove(46L)
        click(0)
        verify(exactly = 1) { repository.findById(46L) }
        assertTrue(label(49).contains("(page, 1)"))
    }

    @Test fun `stale armed deletion does not delete transferred row`() {
        seed(1)
        delete().open(player)
        click(0)
        rows[1L] = rows.getValue(1L).copy(owner = UUID.randomUUID())
        click(0)
        verify(exactly = 0) { repository.delete(any()) }
    }

    @Test fun `use permission revocation blocks all Java actions including navigation`() {
        seed(46)
        val actions = listOf(owned() to 0, owned() to 51, delete() to 0, trust() to 0, trust() to 53)
        for ((menu, slot) in actions) {
            permission.setPermission("enthusiamarket.shop.use", true)
            menu.open(player)
            permission.setPermission("enthusiamarket.shop.use", false)
            click(slot)
            assertEquals(org.bukkit.event.inventory.InventoryType.CRAFTING, player.openInventory.type)
        }
        verify(exactly = 0) { repository.delete(any()) }
        verify(exactly = 0) { repository.upsert(any()) }
    }

    @Test fun `failed deletion reports failure without claiming success`() {
        seed(1)
        val refusing = mockk<ShopManagementService>()
        every { refusing.shopsOwnedBy(player.uniqueId) } answers { rows.values.toList() }
        every { refusing.canDelete(any(), player.uniqueId) } returns true
        every { refusing.delete(player.uniqueId, 1L) } returns false
        DeleteShopsMenu(player.uniqueId, refusing, lang).open(player)
        click(0)
        click(0)
        verify(exactly = 1) { lang.msg("shop.delete.failed") }
        verify(exactly = 0) { lang.msg("shop.delete.done") }
        assertTrue(rows.containsKey(1L))
    }

    @Test fun `owner-scoped menus reject a different player`() {
        seed(1)
        val other = UUID.randomUUID()
        for (menu in listOf<Menu>(OwnedShopsMenu(other, repository, management, lang),
            DeleteShopsMenu(other, management, lang), BulkTrustMenu(other, target, "Target", management, lang))) {
            menu.open(player)
            assertEquals(org.bukkit.event.inventory.InventoryType.CRAFTING, player.openInventory.type)
        }
        verify(exactly = 0) { repository.findByOwner(other) }
    }

    @Test fun `owned callback refreshes when current shop permission is lost`() {
        seed(1)
        owned().open(player)
        rows[1L] = rows.getValue(1L).copy(owner = UUID.randomUUID())
        click(0)
        assertTrue(label(49).contains("(count, 0)"))
        verify(exactly = 0) { repository.upsert(any()) }
    }

    private fun owned() = OwnedShopsMenu(player.uniqueId, repository, management, lang)
    private fun delete() = DeleteShopsMenu(player.uniqueId, management, lang)
    private fun trust() = BulkTrustMenu(player.uniqueId, target, "Target", management, lang)
    private fun click(slot: Int) { assertTrue(player.simulateInventoryClick(slot).isCancelled) }
    private fun item(slot: Int) = player.openInventory.topInventory.getItem(slot)
    private fun label(slot: Int) = PlainTextComponentSerializer.plainText().serialize(
        assertNotNull(item(slot), "Expected control or shop in slot $slot").itemMeta!!.displayName()!!)
    private fun seed(count: Int) {
        rows.clear()
        for (id in 1..count) rows[id.toLong()] = Shop(id.toLong(), "stall$id", player.uniqueId,
            "world", id, 65, 1, "world", id, 64, 1, "item", 1, "money", 10)
    }
}
