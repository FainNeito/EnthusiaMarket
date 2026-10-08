package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.domain.shop.PendingShopSales
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertEquals

class ShopNotificationWorkerTest {
    private val owner = UUID.randomUUID()
    private val repo = mockk<ShopTransactionRepository>(relaxed = true)
    private val io = QueueExecutor()
    private val server = QueueExecutor()
    private val failures = mutableListOf<Exception>()
    private val worker = ShopNotificationWorker(repo, io, server, failures::add)
    private val summary = PendingShopSales(3, 41)

    @Test fun `SQL and delivery execute on separate queues and acknowledge only snapshot`() {
        every { repo.pendingSales(owner) } returns summary
        val deliveries = mutableListOf<PendingShopSales>()
        worker.notify(owner) { deliveries.add(it); true }
        verify(exactly = 0) { repo.pendingSales(any()) }
        io.runNext()
        assertEquals(0, deliveries.size)
        server.runNext()
        assertEquals(listOf(summary), deliveries)
        verify(exactly = 0) { repo.markNotifiedThrough(any(), any()) }
        io.runNext()
        verify(exactly = 1) { repo.markNotifiedThrough(owner, 41) }
        verify(exactly = 0) { repo.markNotified(any()) }
    }

    @Test fun `storage failure is recoverable on next join`() {
        every { repo.pendingSales(owner) } throws IllegalStateException("unavailable")
        worker.notify(owner) { true }
        io.runNext()
        assertEquals(1, failures.size)
        every { repo.pendingSales(owner) } returns summary
        worker.notify(owner) { true }
        io.runNext(); server.runNext(); io.runNext()
        verify(exactly = 1) { repo.markNotifiedThrough(owner, 41) }
    }

    @Test fun `disconnect or replaced player session leaves rows unread`() {
        every { repo.pendingSales(owner) } returns summary
        worker.notify(owner) { false }
        io.runNext(); server.runNext()
        assertEquals(0, io.size)
        verify(exactly = 0) { repo.markNotifiedThrough(any(), any()) }
        worker.notify(owner) { true }
        assertEquals(1, io.size)
    }

    @Test fun `coalesce duplicate pending requests for same owner`() {
        worker.notify(owner) { true }
        worker.notify(owner) { true }
        assertEquals(1, io.size)
    }

    @Test fun `rejoin during storage read uses latest session callback`() {
        every { repo.pendingSales(owner) } returns summary
        worker.notify(owner) { error("Original session has disconnected") }
        worker.notify(owner) { true }
        io.runNext(); server.runNext(); io.runNext()
        verify(exactly = 1) { repo.markNotifiedThrough(owner, 41) }
    }

    @Test fun `rejoin during acknowledgement schedules a fresh snapshot`() {
        every { repo.pendingSales(owner) } returns summary
        worker.notify(owner) { true }
        io.runNext(); server.runNext()
        worker.notify(owner) { true }
        io.runNext()
        assertEquals(1, io.size)
        io.runNext(); server.runNext(); io.runNext()
        verify(exactly = 2) { repo.pendingSales(owner) }
    }

    @Test fun `queue rejection never executes SQL on caller`() {
        val rejecting = Executor { throw RejectedExecutionException("full") }
        ShopNotificationWorker(repo, rejecting, server, failures::add).notify(owner) { true }
        assertEquals(1, failures.size)
        verify(exactly = 0) { repo.pendingSales(any()) }
    }

    @Test fun `server scheduling failure leaves rows unread`() {
        every { repo.pendingSales(owner) } returns summary
        val rejecting = Executor { throw RejectedExecutionException("disabled") }
        ShopNotificationWorker(repo, io, rejecting, failures::add).notify(owner) { true }
        io.runNext()
        assertEquals(1, failures.size)
        verify(exactly = 0) { repo.markNotifiedThrough(any(), any()) }
    }

    @Test fun `shutdown before delivery prevents message and acknowledgement`() {
        every { repo.pendingSales(owner) } returns summary
        var deliveries = 0
        worker.notify(owner) { deliveries++; true }
        io.runNext(); worker.close(); server.runNext()
        worker.notify(owner) { deliveries++; true }
        assertEquals(0, deliveries)
        assertEquals(0, io.size)
        verify(exactly = 0) { repo.markNotifiedThrough(any(), any()) }
    }

    @Test fun `acknowledgement failure allows summary to retry`() {
        every { repo.pendingSales(owner) } returns summary
        every { repo.markNotifiedThrough(owner, 41) } throws IllegalStateException("storage down")
        worker.notify(owner) { true }
        io.runNext(); server.runNext(); io.runNext()
        assertEquals(1, failures.size)
        worker.notify(owner) { true }
        assertEquals(1, io.size)
    }

    @Test fun `empty summary has no message or acknowledgement`() {
        every { repo.pendingSales(owner) } returns PendingShopSales(0, 0)
        worker.notify(owner) { error("Empty summaries must not be delivered") }
        io.runNext()
        assertEquals(0, server.size)
        verify(exactly = 0) { repo.markNotifiedThrough(any(), any()) }
    }

    private class QueueExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val size: Int get() = tasks.size
        override fun execute(command: Runnable) { tasks.add(command) }
        fun runNext() { tasks.removeFirst().run() }
    }
}
