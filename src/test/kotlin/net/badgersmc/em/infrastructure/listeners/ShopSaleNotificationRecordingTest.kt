package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopSaleAttribution
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.UUID

class ShopSaleNotificationRecordingTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun `event captures sale-time stall and guild separately from transaction`() {
        val storage = mockk<ShopHistoryStorage>()
        val attribution = slot<ShopSaleAttribution>()
        val transaction = slot<ShopTransaction>()
        every { storage.recordAttributed(capture(transaction), capture(attribution)) } returns Unit
        val event = PostShopTransactionEvent(server.addPlayer(), UUID.randomUUID(), ItemStack(Material.DIAMOND), 1, 100.0)
        event.stallId = "stall17"
        event.publicCashEligible = true
        event.grossPayment = 9_007_199_254_740_993L
        val config = EnthusiaMarketConfig().also { it.shop.archiveCaptureEnabled = true }
        ShopTransactionRecorder(storage, config).onTransaction(event)
        assertEquals(ShopSaleAttribution("stall17", null, publicCashEligible = true), attribution.captured)
        assertEquals(9_007_199_254_740_993L, transaction.captured.totalPrice)
        verify(exactly = 0) { storage.record(any()) }
    }

    @Test fun `archive capture defaults off and records legacy history even when sale has attribution`() {
        val storage = mockk<ShopHistoryStorage>()
        val transaction = slot<ShopTransaction>()
        every { storage.record(capture(transaction)) } returns Unit
        val event = PostShopTransactionEvent(server.addPlayer(), UUID.randomUUID(), ItemStack(Material.DIAMOND), 1, 100.0)
        event.stallId = "stall17"
        event.publicCashEligible = true
        event.grossPayment = 9_007_199_254_740_993L
        ShopTransactionRecorder(storage, EnthusiaMarketConfig()).onTransaction(event)
        assertEquals(9_007_199_254_740_993L, transaction.captured.totalPrice)
        verify(exactly = 1) { storage.record(any()) }
        verify(exactly = 0) { storage.recordAttributed(any(), any()) }
    }

    @Test fun `online delivery records only its sale notified without mass acknowledgement`() {
        val owner = server.addPlayer()
        val event = PostShopTransactionEvent(server.addPlayer(), owner.uniqueId, ItemStack(Material.DIAMOND), 1, 100.0)
        val repo = mockk<ShopHistoryStorage>()
        val captured = slot<ShopTransaction>()
        every { repo.record(capture(captured)) } returns Unit
        val config = EnthusiaMarketConfig().also { it.shop.notifyEnabled = true }
        val notifier = ShopSaleNotifier(config, mockk<LangService>(relaxed = true))
        val recorder = ShopTransactionRecorder(repo, config)
        val plugin = MockBukkit.createMockPlugin()
        // Register backwards: event priority, rather than discovery order, must order delivery before recording.
        server.pluginManager.registerEvents(recorder, plugin)
        server.pluginManager.registerEvents(notifier, plugin)
        server.pluginManager.callEvent(event)
        assertTrue(captured.captured.notified)

    }

    @Test fun `offline or disabled delivery retains unread sale`() {
        val repo = mockk<ShopHistoryStorage>()
        val captured = slot<ShopTransaction>()
        every { repo.record(capture(captured)) } returns Unit
        val config = EnthusiaMarketConfig().also { it.shop.notifyEnabled = true }
        val recorder = ShopTransactionRecorder(repo, config)
        recorder.onTransaction(PostShopTransactionEvent(server.addPlayer(), UUID.randomUUID(), ItemStack(Material.DIAMOND), 1, 100.0))
        assertFalse(captured.captured.notified)
        config.shop.notifyEnabled = false
        recorder.onTransaction(PostShopTransactionEvent(server.addPlayer(), server.addPlayer().uniqueId, ItemStack(Material.DIAMOND), 1, 100.0))
        assertFalse(captured.captured.notified)
    }
}
