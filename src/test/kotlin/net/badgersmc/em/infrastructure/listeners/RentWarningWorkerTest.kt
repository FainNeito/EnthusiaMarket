package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.domain.stall.StallRepository
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals

class RentWarningWorkerTest {
    private val actor = UUID.randomUUID()
    private val stalls = mockk<StallRepository>()
    private val io = Queue()
    private val server = Queue()
    private val failures = mutableListOf<Exception>()
    private val worker = RentWarningWorker(stalls, io, server, failures::add)

    @Test fun `repository and delivery run in separate phases and pending joins coalesce`() {
        every { stalls.all() } returns emptyList()
        var delivered = 0
        worker.load(actor) { delivered += 1 }
        worker.load(actor) { delivered += 10 }
        verify(exactly = 0) { stalls.all() }
        assertEquals(1, io.tasks.size)
        io.run()
        assertEquals(0, delivered)
        server.run()
        assertEquals(10, delivered)
        verify(exactly = 1) { stalls.all() }
    }

    @Test fun `repository failures release pending request for retry`() {
        every { stalls.all() } throws IllegalStateException("offline")
        worker.load(actor) { error("must not deliver") }
        io.run()
        assertEquals(1, failures.size)
        every { stalls.all() } returns emptyList()
        var delivered = 0
        worker.load(actor) { delivered++ }
        io.run()
        server.run()
        assertEquals(1, delivered)
    }

    @Test fun `rejected queue never runs storage inline`() {
        val rejected = RentWarningWorker(stalls, Executor { throw RejectedExecutionException() }, server, failures::add)
        rejected.load(actor) { error("must not deliver") }
        assertEquals(1, failures.size)
        verify(exactly = 0) { stalls.all() }
    }

    @Test fun `shutdown suppresses queued callbacks and future reads`() {
        every { stalls.all() } returns emptyList()
        worker.load(actor) { error("stale callback") }
        io.run()
        worker.close()
        server.run()
        worker.load(actor) { error("after close") }
        assertEquals(0, io.tasks.size)
    }

    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun run() { tasks.removeFirst().run() }
    }
}
