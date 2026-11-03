package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
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
import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.em.infrastructure.i18n.EnthusiaMarketLang
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
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

class OwnedShopsDisplayTest {
    @TempDir lateinit var directory: Path
    private lateinit var player: PlayerMock
    private lateinit var lang: LangService
    private val repository = mockk<ShopRepository>()
    private val rows = mutableListOf<Shop>()

    @BeforeTest fun setUp() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        player = server.addPlayer()
        player.addAttachment(plugin, "enthusiamarket.shop.use", true)
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        ChestGui(1, "fixture")
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize(any()) } answers { ItemStack(Material.REDSTONE) }
        every { repository.findByOwner(player.uniqueId) } answers { rows.toList() }
        lang = locale()
    }

    @AfterTest fun tearDown() {
        unmockkObject(ItemStackSerializer)
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun `real locale renders both styled statuses without component debug text`() {
        for ((frozen, text) in listOf(false to "Active", true to "Frozen")) {
            show(shop().copy(frozen = frozen))
            assertEquals("Status: $text", plain(lore()[2]))
            assertEquals(lang.msg("gui.shop.owned.frozen_lore", "frozen" to
                MiniMessage.miniMessage().serialize(lang.msg(
                    if (frozen) "gui.shop.owned.status_frozen" else "gui.shop.owned.status_active"))), lore()[2])
            assertFalse(plain(lore()[2]).contains("TextComponentImpl"))
        }
    }

    @Test fun `BUY availability explains live checks without promising unlimited trades`() {
        show(shop().copy(direction = SignDirection.BUY, stockCount = 0))
        assertEquals("Capacity and funds checked when trading", plain(lore()[1]))
        assertFalse(lore().any { plain(it).contains(Int.MAX_VALUE.toString()) })
        verify(exactly = 0) { repository.upsert(any()) }
    }

    @Test fun `SELL TRADE and admin entries retain meaningful stock descriptions`() {
        show(shop().copy(stockCount = 7, sellAmount = 2))
        assertEquals("Stock: 3 trades", plain(lore()[1]))
        show(shop().copy(direction = SignDirection.TRADE, stockCount = 0))
        assertEquals("Stock: 0 trades", plain(lore()[1]))
        for (direction in SignDirection.entries) {
            show(shop().copy(direction = direction, adminShop = true))
            assertEquals("Stock: Unlimited", plain(lore()[1]))
        }
    }

    @Test fun `existing locale overrides are preserved alongside new availability defaults`() {
        Files.writeString(directory.resolve("lang/en_US.yml"),
            "gui:\n  shop:\n    owned:\n      frozen_lore: '<gold>State: <frozen>'\n" +
                "      status_active: '<aqua>Open'\n      status_frozen: '<red>Paused'\n")
        lang = locale()
        show(shop().copy(direction = SignDirection.BUY))
        assertEquals("State: Open", plain(lore()[2]))
        assertEquals("Capacity and funds checked when trading", plain(lore()[1]))
        show(shop().copy(frozen = true))
        assertEquals("State: Paused", plain(lore()[2]))
    }

    private fun locale(): LangService {
        val host = object : LangHost {
            override val dataFolder = directory.toFile()
            override val resourceClassLoader = javaClass.classLoader
        }
        return LangService(host, Locale("en_US"), EnthusiaMarketLang::class.java)
    }

    private fun shop() = Shop(1, "18", player.uniqueId, "world", -32, 121, -277,
        "world", -32, 121, -278, "redstone", 1, "gold", 11)

    private fun show(shop: Shop) {
        rows.clear()
        rows += shop
        OwnedShopsMenu(player.uniqueId, repository, ShopManagementService(repository), lang).open(player)
    }

    private fun lore() = player.openInventory.topInventory.getItem(0)!!.itemMeta!!.lore()!!
    private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)
}
