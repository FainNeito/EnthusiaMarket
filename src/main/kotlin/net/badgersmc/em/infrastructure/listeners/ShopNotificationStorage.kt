package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.domain.shop.PendingShopSales
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.nexus.annotations.Component
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bounded read/acknowledgement worker; all player callbacks use the Bukkit scheduler. */
@Component
class ShopNotificationStorage(transactions: ShopTransactionRepository, plugin: Plugin) : AutoCloseable {
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64),
        { task -> Thread(task, "EnthusiaMarket-sale-notifications").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val worker = ShopNotificationWorker(
        transactions, executor,
        { task -> plugin.server.scheduler.runTask(plugin, task) },
        { failure -> plugin.logger.warning("Sale summary deferred; unread history retained: ${failure.message}") },
    )

    fun notify(owner: UUID, deliver: (PendingShopSales) -> Boolean) = worker.notify(owner, deliver)

    override fun close() {
        worker.close()
        executor.shutdownNow()
    }
}
