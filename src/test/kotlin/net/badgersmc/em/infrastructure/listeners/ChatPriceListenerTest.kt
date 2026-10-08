package net.badgersmc.em.infrastructure.listeners

import io.mockk.*
import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.badgersmc.em.interaction.gui.CreateShopMenu
import net.badgersmc.em.interaction.gui.PurchaseBulkMenu
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatPriceListenerTest {
    private lateinit var server: ServerMock
    private lateinit var player: Player
    private val broadcast = LegacyBroadcaster()

    @BeforeEach fun setup() {
        server = MockBukkit.mock()
        player = server.addPlayer()
        val plugin = MockBukkit.createMockPlugin("MarketInputTest")
        mockkObject(CreateShopMenu.Companion, PurchaseBulkMenu.Companion)
        every { CreateShopMenu.isWaiting(any()) } returns true
        every { PurchaseBulkMenu.isWaiting(any()) } returns false
        every { CreateShopMenu.handleChat(any(), any(), any()) } returns true
        server.pluginManager.registerEvents(ChatPriceListener(mockk(relaxed = true), plugin), plugin)
        server.pluginManager.registerEvents(broadcast, plugin)
    }

    @AfterEach fun cleanup() {
        unmockkAll()
        MockBukkit.unmock()
    }

    @Test fun `price input is private before legacy broadcaster and deferred to server thread`() {
        val event = legacy("5")
        server.pluginManager.callEvent(event)
        assertTrue(event.isCancelled)
        assertTrue(broadcast.messages.isEmpty())
        verify(exactly = 0) { CreateShopMenu.handleChat(any(), any(), any()) }
        server.scheduler.performOneTick()
        verify(exactly = 1) { CreateShopMenu.handleChat(player, "5", any()) }
    }

    @Test fun `paper-only input remains private`() {
        val event = paper("5")
        server.pluginManager.callEvent(event)
        assertTrue(event.isCancelled)
        server.scheduler.performOneTick()
        verify(exactly = 1) { CreateShopMenu.handleChat(player, "5", any()) }
    }

    @Test fun `legacy cancellation propagated to Paper does not duplicate input`() {
        val legacy = legacy("5")
        server.pluginManager.callEvent(legacy)
        val paper = paper("5")
        paper.isCancelled = legacy.isCancelled
        server.pluginManager.callEvent(paper)
        server.scheduler.performOneTick()
        verify(exactly = 1) { CreateShopMenu.handleChat(player, "5", any()) }
    }

    @Test fun `ordinary chat passes through legacy broadcaster`() {
        every { CreateShopMenu.isWaiting(any()) } returns false
        val event = legacy("hello")
        server.pluginManager.callEvent(event)
        assertFalse(event.isCancelled)
        assertEquals(listOf("hello"), broadcast.messages)
        server.scheduler.performOneTick()
        verify(exactly = 0) { CreateShopMenu.handleChat(any(), any(), any()) }
    }

    @Test fun `bulk quantity input is also private on legacy path`() {
        every { CreateShopMenu.isWaiting(any()) } returns false
        every { PurchaseBulkMenu.isWaiting(any()) } returns true
        every { PurchaseBulkMenu.handleChat(any(), any(), any()) } just Runs
        val event = legacy("16")
        server.pluginManager.callEvent(event)
        assertTrue(event.isCancelled)
        assertTrue(broadcast.messages.isEmpty())
        server.scheduler.performOneTick()
        verify(exactly = 1) { PurchaseBulkMenu.handleChat(player, "16", any()) }
    }

    private fun legacy(message: String) = AsyncPlayerChatEvent(false, player, message, mutableSetOf())

    private fun paper(message: String) = AsyncChatEvent(
        false, player, mutableSetOf(), ChatRenderer.defaultRenderer(),
        Component.text(message), Component.text(message), mockk(relaxed = true),
    )

    /** Models RoseChat's LOW-priority, ignoreCancelled legacy broadcast path. */
    class LegacyBroadcaster : Listener {
        val messages = mutableListOf<String>()

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        fun onChat(event: AsyncPlayerChatEvent) {
            messages.add(event.message)
        }
    }
}
