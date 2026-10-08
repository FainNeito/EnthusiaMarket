package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.domain.shop.PendingShopSales
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.event.player.PlayerJoinEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import net.kyori.adventure.text.Component
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ShopSaleJoinNotifierTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun `join never accesses unavailable storage inline`() {
        val repo = mockk<ShopTransactionRepository>()
        every { repo.countUnnotified(any()) } throws IllegalStateException("Storage unavailable")
        val config = EnthusiaMarketConfig().also { it.shop.notifyEnabled = true }
        ShopNotificationStorage(repo, MockBukkit.createMockPlugin()).use { storage ->
            val notifier = ShopSaleJoinNotifier(storage, config, mockk<LangService>())
            notifier.onJoin(PlayerJoinEvent(server.addPlayer(), null as net.kyori.adventure.text.Component?))
        }
        verify(exactly = 0) { repo.countUnnotified(any()) }
        verify(exactly = 0) { repo.markNotified(any()) }
    }

    @Test fun `summary delivery rechecks config before acknowledgement`() {
        val callback = slot<(PendingShopSales) -> Boolean>()
        val storage = mockk<ShopNotificationStorage>()
        every { storage.notify(any(), capture(callback)) } returns Unit
        val config = EnthusiaMarketConfig().also { it.shop.notifyEnabled = true }
        val lang = mockk<LangService>()
        every { lang.msg("shop.notify.away_summary", "count" to 3) } returns Component.text("three sales")
        val player = server.addPlayer()
        ShopSaleJoinNotifier(storage, config, lang).onJoin(PlayerJoinEvent(player, null as Component?))
        assertTrue(callback.captured(PendingShopSales(3, 41)))
        config.shop.notifyEnabled = false
        assertFalse(callback.captured(PendingShopSales(3, 41)))
        verify(exactly = 1) { lang.msg("shop.notify.away_summary", "count" to 3) }
    }
}
