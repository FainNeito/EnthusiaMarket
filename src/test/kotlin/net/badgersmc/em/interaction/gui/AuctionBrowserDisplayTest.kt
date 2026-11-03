package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.badgersmc.em.application.AuctionLifecycleService
import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.domain.auction.AuctionId
import net.badgersmc.em.domain.auction.AuctionRepository
import net.badgersmc.em.domain.auction.AuctionState
import net.badgersmc.em.domain.auction.Bid
import net.badgersmc.em.domain.ports.RegionProvider
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.infrastructure.i18n.EnthusiaMarketLang
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.badgersmc.nexus.scheduler.NexusScheduler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuctionBrowserDisplayTest {
    @TempDir lateinit var directory: Path
    private val auctions = mockk<AuctionRepository>()
    private val stalls = mockk<StallRepository>()
    private val regions = mockk<RegionProvider>()
    private val names = mockk<OfflinePlayerNameCache>()
    private lateinit var lang: LangService

    @BeforeTest fun setUp() {
        MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        ChestGui(1, "fixture")
        val host = object : LangHost {
            override val dataFolder = directory.toFile()
            override val resourceClassLoader = javaClass.classLoader
        }
        lang = LangService(host, Locale("en_US"), EnthusiaMarketLang::class.java)
    }

    @AfterTest fun tearDown() {
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun `open auction renders its dimensions and bidder`() {
        val bidder = UUID.randomUUID()
        val auction = auction(Bid(bidder, 125, Instant.now()), Instant.now().plusSeconds(3600))
        every { names.resolveOffMainThread(bidder) } returns "FainNoir"
        val item = display(auction, RegionProvider.RegionBounds(0, 0, 0, 4, 9, 4))
        assertEquals(Material.CYAN_DYE, item.type)
        val lines = item.itemMeta!!.lore()!!.map(::plain)
        assertTrue(lines.contains("Bounds: 5 × 5 × 10 (W × L × H)"))
        assertTrue(lines.contains("Bounding volume: 250 blocks"))
        assertTrue(lines.contains("Current bid: 125 (FainNoir)"))
        assertTrue(lines.contains("Starting bid: 100"))
    }

    @Test fun `waiting auction renders unavailable size and no bids`() {
        val item = display(auction(null, Instant.MAX), null)
        assertEquals(Material.GRAY_DYE, item.type)
        val lines = item.itemMeta!!.lore()!!.map(::plain)
        assertTrue(lines.contains("Region size unavailable"))
        assertTrue(lines.contains("Current bid: 100 (no bids)"))
        assertTrue(lines.contains("Time left: Waiting for first bid"))
    }

    private fun display(auction: Auction, bounds: RegionProvider.RegionBounds?): ItemStack {
        val stall = mockk<Stall>()
        every { stall.world } returns "world"
        every { stall.regionId } returns "stall100"
        every { auctions.allOpen() } returns listOf(auction)
        every { stalls.findById(auction.stallId) } returns stall
        every { regions.bounds("world", "stall100") } returns bounds
        val menu = AuctionBrowserMenu(auctions, stalls, mockk<AuctionLifecycleService>(),
            mockk<NexusScheduler>(), lang, regions, names)
        val gui = ChestGui(6, "auction fixture")
        AuctionBrowserMenu::class.java.getDeclaredMethod("prefetch").apply { isAccessible = true }.invoke(menu)
        AuctionBrowserMenu::class.java.getDeclaredMethod("render", ChestGui::class.java)
            .apply { isAccessible = true }.invoke(menu, gui)
        val player = MockBukkit.getMock()!!.addPlayer()
        gui.show(player)
        return player.openInventory.topInventory.getItem(0)!!
    }

    private fun auction(bid: Bid?, end: Instant) = Auction(
        AuctionId("auction100"), StallId("100"), AuctionState.OPEN, Instant.now(), end,
        100, bid, Duration.ofSeconds(30), Duration.ofSeconds(30),
    )

    private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)
}
