package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.events.PostShopTransactionEvent
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.UUID

class ShopSaleNotificationRecordingTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun `online delivery records only its sale notified without mass acknowledgement`() {
        val owner = server.addPlayer()
        val event = PostShopTransactionEvent(server.addPlayer(), owner.uniqueId, ItemStack(Material.DIAMOND), 1, 100.0)
        val repo = mockk<ShopTransactionRepository>()
        val captured = slot<ShopTransaction>()
        every { repo.record(capture(captured)) } answers { captured.captured }
        val config = EnthusiaMarketConfig().also { it.shop.notifyEnabled = true }
        val notifier = ShopSaleNotifier(config, mockk<LangService>(relaxed = true))
        val recorder = ShopTransactionRecorder(repo, config)
        val plugin = MockBukkit.createMockPlugin()
        // Register backwards: event priority, rather than discovery order, must order delivery before recording.
        server.pluginManager.registerEvents(recorder, plugin)
        server.pluginManager.registerEvents(notifier, plugin)
        server.pluginManager.callEvent(event)
        assertTrue(captured.captured.notified)
        verify(exactly = 0) { repo.markNotified(any()) }
    }

    @Test fun `offline or disabled delivery retains unread sale`() {
        val repo = mockk<ShopTransactionRepository>()
        val captured = slot<ShopTransaction>()
        every { repo.record(capture(captured)) } answers { captured.captured }
        val config = EnthusiaMarketConfig().also { it.shop.notifyEnabled = true }
        val recorder = ShopTransactionRecorder(repo, config)
        recorder.onTransaction(PostShopTransactionEvent(server.addPlayer(), UUID.randomUUID(), ItemStack(Material.DIAMOND), 1, 100.0))
        assertFalse(captured.captured.notified)
        config.shop.notifyEnabled = false
        recorder.onTransaction(PostShopTransactionEvent(server.addPlayer(), server.addPlayer().uniqueId, ItemStack(Material.DIAMOND), 1, 100.0))
        assertFalse(captured.captured.notified)
    }
}
