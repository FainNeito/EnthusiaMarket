package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.events.PostShopTransactionEvent
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.assertFalse
import net.badgersmc.em.infrastructure.persistence.ShopHistoryOutbox
import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.SignDirection
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShopHistoryThreadBoundaryTest {
    @Test fun `shutdown interrupts delivery retains journal and stops admission`() {
        MockBukkit.mock()
        try {
            val plugin = MockBukkit.createMockPlugin()
            val repository = mockk<ShopTransactionRepository>()
            val started = CountDownLatch(1)
            val interrupted = CountDownLatch(1)
            every { repository.recordOnce(any(), any()) } answers {
                started.countDown()
                try { CountDownLatch(1).await() } finally { interrupted.countDown() }
            }
            val directory = plugin.dataFolder.toPath().resolve("history-outbox")
            val sale = ShopTransaction(shopId = 42, owner = UUID.randomUUID(), buyer = UUID.randomUUID(),
                direction = SignDirection.SELL, item = "diamond", quantity = 1, totalPrice = 100, createdAt = 1000)
            val id = ShopHistoryOutbox(directory, repository).append(sale)
            val storage = ShopHistoryStorage(repository, plugin)
            try {
                kotlin.test.assertTrue(started.await(3, TimeUnit.SECONDS))
                storage.close()
                kotlin.test.assertTrue(interrupted.await(3, TimeUnit.SECONDS))
                kotlin.test.assertTrue(java.nio.file.Files.exists(directory.resolve("$id.pending")))
                kotlin.test.assertFailsWith<IllegalStateException> { storage.record(sale) }
            } finally { storage.close() }
        } finally { MockBukkit.unmock() }
    }

    @Test fun `completed trade listener does not call SQL on caller`() {
        MockBukkit.mock()
        try {
            val repo = mockk<ShopTransactionRepository>()
            val caller = Thread.currentThread()
            val accessed = java.util.concurrent.atomic.AtomicBoolean(false)
            every { repo.record(any()) } answers { accessed.set(true); firstArg() }
            every { repo.recordOnce(any(), any()) } answers {
                if (Thread.currentThread() === caller) accessed.set(true)
                throw IllegalStateException("SQL unavailable")
            }
            val server = MockBukkit.getMock()!!
            val plugin = MockBukkit.createMockPlugin()
            val storage = ShopHistoryStorage(repo, plugin)
            try {
            ShopTransactionRecorder(storage, EnthusiaMarketConfig()).onTransaction(
                PostShopTransactionEvent(server.addPlayer(), server.addPlayer().uniqueId, ItemStack(Material.DIAMOND), 1, 100.0)
            )
            assertFalse(accessed.get(), "Completed trade event must not execute SQL inline")
            kotlin.test.assertTrue(plugin.dataFolder.resolve("history-outbox").listFiles()!!.any { it.extension == "pending" })
            } finally { storage.close() }
        } finally {
            MockBukkit.unmock()
        }
    }
}
