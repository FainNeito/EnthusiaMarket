package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.StallAccessIndex
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Invalidate before a moderation reservation drops; ownership reads stay off event handlers. */
class StallAccessRefreshWorker(private val index: StallAccessIndex) : AutoCloseable {
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(64))
    fun invalidateAndRefresh(id: String) {
        index.invalidate(id)
        // Rejected refresh remains stale and cannot grant former-owner/ally authority.
        runCatching { executor.execute { runCatching { index.refresh(id) } } }
    }
    override fun close() { executor.shutdownNow() }
}
