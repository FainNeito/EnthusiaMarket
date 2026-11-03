package net.badgersmc.em.interaction.gui

import io.mockk.*
import io.papermc.paper.event.player.AsyncChatEvent
import net.badgersmc.em.application.AuctionLifecycleService
import net.badgersmc.em.application.AuctionResult
import net.badgersmc.em.domain.auction.*
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.infrastructure.listeners.AuctionChatListener
import net.badgersmc.em.interaction.AuctionBidUi
import net.badgersmc.em.interaction.MenuFactory
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.scheduler.NexusScheduler
import net.kyori.adventure.text.Component
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.plugin.java.JavaPlugin
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.time.Duration
import java.time.Instant
import kotlin.test.*

class AuctionBidMenuReviewTest {
    private lateinit var player: PlayerMock
    private lateinit var permission: PermissionAttachment
    private val service = mockk<AuctionLifecycleService>()
    private val lang = mockk<LangService>()
    private val scheduler = mockk<NexusScheduler>()
    private val factory = mockk<MenuFactory>()
    private val queued = mutableListOf<() -> Unit>()
    private val auction = Auction(AuctionId("a1"), StallId("stall1"), AuctionState.OPEN,
        Instant.now(), Instant.now().plusSeconds(600), 100, null, Duration.ZERO, Duration.ZERO)

    @BeforeTest fun setup() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        player = server.addPlayer()
        permission = player.addAttachment(plugin, AuctionBidMenu.BID_PERMISSION, true)!!
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        com.github.stefvanschie.inventoryframework.gui.type.ChestGui(1, "fixture")
        org.bukkit.event.HandlerList.unregisterAll(plugin)
        server.pluginManager.registerEvents(com.github.stefvanschie.inventoryframework.gui.GuiListener(plugin), plugin)
        configureFixtures()
    }

    private fun configureFixtures() {
        every { lang.raw(any()) } answers { firstArg<String>() }
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        every { service.auctionForBid(auction.id) } returns auction
        every { service.eligibleGuilds(player.uniqueId) } returns listOf(GuildProvider.GuildRef("g", "Known guild"))
        every { service.placeBid(auction.id, any<AuctionLifecycleService.BidRequest>()) } returns AuctionResult.Success(auction)
        every { factory.shouldUseBedrockMenus(player) } returns false
        every { scheduler.runOnMain(any()) } answers { queued.add(firstArg()); AutoCloseable { } }
        AuctionBidUi.configure(scheduler, factory)
    }

    @AfterTest fun cleanup() {
        AuctionBidUi.reset()
        AuctionBidMenu.clearPending()
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun `preset selection reviews before a single confirmed guild dispatch`() {
        AuctionBidMenu(auction, service, lang).open(player)
        click(10) // explicit guild picker
        click(0) // named guild
        click(12) // review minimum full bid
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
        click(9) // confirm
        verify(exactly = 1) { service.placeBid(auction.id,
            match<AuctionLifecycleService.BidRequest> { it.amount == 100L && it.guildId == "g" && it.actor == player.uniqueId }) }
    }

    @Test fun `custom chat is claimed cancelled and deferred before the same review`() {
        AuctionBidMenu(auction, service, lang).open(player)
        click(11)
        clearMocks(service, answers = false)
        val event = chat("137")
        AuctionChatListener(lang, service).onChat(event)
        verify { event.isCancelled = true }
        assertEquals(1, queued.size)
        assertEquals(org.bukkit.event.inventory.InventoryType.CRAFTING, player.openInventory.type)
        verify(exactly = 0) { service.auctionForBid(any()) }
        assertFalse(AuctionBidMenu.pendingCustomBids.containsKey(player.uniqueId))
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
        queued.single().invoke()
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
        click(9)
        verify(exactly = 1) { service.placeBid(auction.id, match<AuctionLifecycleService.BidRequest> { it.amount == 137L }) }
    }

    @Test fun `new menu invalidates queued old chat confirmation`() {
        val menu = AuctionBidMenu(auction, service, lang)
        menu.open(player); click(11)
        AuctionChatListener(lang, service).onChat(chat("137"))
        menu.open(player)
        clearMocks(service, answers = false)
        queued.single().invoke()
        verify(exactly = 0) { service.auctionForBid(any()) }
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    @Test fun `revoked permission at confirmation never dispatches`() {
        AuctionBidMenu(auction, service, lang).open(player)
        click(12)
        permission.setPermission(AuctionBidMenu.BID_PERMISSION, false)
        click(9)
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    @Test fun `expired custom prompt never opens confirmation`() {
        AuctionBidMenu.pendingCustomBids[player.uniqueId] = auction to Instant.now().minusSeconds(60)
        AuctionChatListener(lang, service).onChat(chat("137"))
        queued.single().invoke()
        verify { lang.msg("gui.auction_bid.custom_timeout", *anyVararg()) }
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    @Test fun `quit cleanup invalidates an already queued custom continuation`() {
        AuctionBidMenu(auction, service, lang).open(player)
        click(11)
        AuctionChatListener(lang, service).onChat(chat("137"))
        AuctionBidMenu.cancelPending(player)
        clearMocks(service, answers = false)
        queued.single().invoke()
        verify(exactly = 0) { service.auctionForBid(any()) }
        assertFalse(AuctionBidMenu.pendingCustomBids.containsKey(player.uniqueId))
    }

    @Test fun `named guild picker reaches forty sixth guild without replacing personal selection`() {
        every { service.eligibleGuilds(player.uniqueId) } returns (1..46).map {
            GuildProvider.GuildRef("g%02d".format(it), "Guild $it")
        }
        AuctionBidMenu(auction, service, lang).open(player)
        click(10)
        assertEquals(54, player.openInventory.topInventory.size)
        click(51)
        click(0)
        click(12)
        click(9)
        verify(exactly = 1) { service.placeBid(auction.id,
            match<AuctionLifecycleService.BidRequest> { it.guildId == "g46" }) }
    }

    @Test fun `replacement player session with same UUID cannot consume old prompt`() {
        AuctionBidMenu(auction, service, lang).open(player)
        click(11)
        val replacement = mockk<org.bukkit.entity.Player>(relaxed = true)
        every { replacement.uniqueId } returns player.uniqueId
        every { replacement.isOnline } returns true
        val continuation = assertNotNull(AuctionBidMenu.claimChat(replacement, "137", lang, service))
        clearMocks(service, answers = false)
        continuation()
        verify(exactly = 0) { service.auctionForBid(any()) }
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    @Test fun `disabled UI generation rejects an already queued chat continuation`() {
        AuctionBidMenu(auction, service, lang).open(player)
        click(11)
        AuctionChatListener(lang, service).onChat(chat("137"))
        AuctionBidUi.reset()
        clearMocks(service, answers = false)
        queued.single().invoke()
        verify(exactly = 0) { service.auctionForBid(any()) }
        verify(exactly = 0) { service.placeBid(any(), any<AuctionLifecycleService.BidRequest>()) }
    }

    private fun click(slot: Int) { assertTrue(player.simulateInventoryClick(slot).isCancelled) }
    private fun chat(message: String) = mockk<AsyncChatEvent>(relaxed = true) {
        every { player } returns this@AuctionBidMenuReviewTest.player
        every { message() } returns Component.text(message)
    }
}
