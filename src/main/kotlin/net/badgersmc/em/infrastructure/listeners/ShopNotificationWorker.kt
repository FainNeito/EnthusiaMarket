package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.domain.shop.PendingShopSales
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Storage isolation and session coalescing; never runs rejected work on the caller. */
internal class ShopNotificationWorker(
    private val transactions: ShopTransactionRepository,
    private val io: Executor,
    private val server: Executor,
    private val report: (Exception) -> Unit,
) : AutoCloseable {
    private val pending = mutableMapOf<UUID, Request>()
    private val closed = AtomicBoolean()

    fun notify(owner: UUID, deliver: (PendingShopSales) -> Boolean) {
        val request = synchronized(pending) {
            if (closed.get()) return
            val existing = pending[owner]
            if (existing != null) {
                existing.deliver = deliver
                return
            }
            Request(deliver).also { pending[owner] = it }
        }
        schedule(io, owner, request) {
            val summary = transactions.pendingSales(owner)
            if (summary.count == 0) {
                finish(owner, request, false)
            } else {
                schedule(server, owner, request) {
                    val currentDelivery = synchronized(pending) {
                        request.deliver.also { request.deliver = null }
                    }
                    if (currentDelivery?.invoke(summary) == true) {
                        schedule(io, owner, request) {
                            transactions.markNotifiedThrough(owner, summary.lastId)
                            finish(owner, request, true)
                        }
                    } else {
                        finish(owner, request, true)
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun schedule(executor: Executor, owner: UUID, request: Request, action: () -> Unit) {
        try {
            executor.execute {
                if (!closed.get()) {
                    try {
                        action()
                    } catch (failure: Exception) {
                        finish(owner, request, false)
                        report(failure)
                    }
                } else {
                    finish(owner, request, false)
                }
            }
        } catch (failure: Exception) {
            finish(owner, request, false)
            report(failure)
        }
    }

    override fun close() {
        closed.set(true)
        synchronized(pending) { pending.clear() }
    }

    private fun finish(owner: UUID, request: Request, retryQueued: Boolean) {
        val next = synchronized(pending) {
            if (pending[owner] !== request) return
            pending.remove(owner)
            if (retryQueued) request.deliver else null
        }
        if (next != null) notify(owner, next)
    }

    private class Request(var deliver: ((PendingShopSales) -> Boolean)?)
}
