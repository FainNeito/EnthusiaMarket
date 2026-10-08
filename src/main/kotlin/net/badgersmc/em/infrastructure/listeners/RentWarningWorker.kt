package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallRepository
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded adapters own executors; this worker never falls back to synchronous reads. */
internal class RentWarningWorker(
    private val stalls: StallRepository,
    private val io: Executor,
    private val server: Executor,
    private val report: (Exception) -> Unit,
) : AutoCloseable {
    private val pending = mutableMapOf<UUID, Request>()
    private val closed = AtomicBoolean()

    fun load(actor: UUID, deliver: (List<Stall>) -> Unit) {
        val request = synchronized(pending) {
            if (closed.get()) return
            pending[actor]?.let { it.deliver = deliver; return }
            Request(deliver).also { pending[actor] = it }
        }
        schedule(io, actor, request) {
            val snapshot = stalls.all()
            schedule(server, actor, request) {
                val callback = synchronized(pending) {
                    if (pending[actor] === request) pending.remove(actor)?.deliver else null
                }
                callback?.invoke(snapshot)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun schedule(executor: Executor, actor: UUID, request: Request, action: () -> Unit) {
        try {
            executor.execute {
                if (!closed.get()) {
                    try { action() } catch (failure: Exception) { fail(actor, request, failure) }
                }
            }
        } catch (failure: Exception) { fail(actor, request, failure) }
    }

    private fun fail(actor: UUID, request: Request, failure: Exception) {
        synchronized(pending) { if (pending[actor] === request) pending.remove(actor) }
        if (!closed.get()) report(failure)
    }

    override fun close() {
        closed.set(true)
        synchronized(pending) { pending.clear() }
    }

    private class Request(var deliver: (List<Stall>) -> Unit)
}
