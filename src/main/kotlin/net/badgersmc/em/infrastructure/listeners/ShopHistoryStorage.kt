package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.domain.shop.ShopSaleAttribution
import net.badgersmc.em.infrastructure.persistence.ShopHistoryOutbox
import net.badgersmc.nexus.annotations.Component
import org.bukkit.plugin.Plugin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One periodic delivery task, with no per-sale executor queue or Bukkit access on its worker. */
@Component
open class ShopHistoryStorage(repository: ShopTransactionRepository, plugin: Plugin) : AutoCloseable {
    private val outbox = ShopHistoryOutbox(plugin.dataFolder.toPath().resolve("history-outbox"), repository, reportCorruption = { failure ->
        plugin.logger.warning("Corrupt shop history record quarantined for review: ${failure.message}")
    })
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "EnthusiaMarket-shop-history").apply { isDaemon = true }
    }
    private val outboxDirectory = plugin.dataFolder.toPath().resolve("history-outbox")
    private val captureFailure = outboxDirectory.resolve("award-capture-failed")
    @Volatile private var failed = false
    private var closed = false

    fun markCaptureFailure() {
        failed = true
        java.nio.file.Files.writeString(captureFailure, "Review sales coverage before clearing this marker.")
    }

    fun drained(): Boolean = !failed && java.nio.file.Files.list(outboxDirectory).use { paths ->
        paths.noneMatch { file ->
            val name = file.fileName.toString()
            name.endsWith(".pending") || name.endsWith(".tmp") || name.endsWith(".corrupt") || file == captureFailure
        }
    }
    private var lastFailureReport = 0L

    init {
        executor.scheduleWithFixedDelay({
            try { outbox.drain() } catch (failure: Exception) {
                val now = System.nanoTime()
                if (lastFailureReport == 0L || now - lastFailureReport >= TimeUnit.MINUTES.toNanos(1)) {
                    lastFailureReport = now
                    plugin.logger.warning("Shop history delivery deferred; local records retained: ${failure.message}")
                }
            }
        }, 0, 5, TimeUnit.SECONDS)
    }

    @Synchronized
    open fun record(tx: ShopTransaction) {
        check(!closed) { "Shop history storage is closed" }
        outbox.append(tx)
    }

    @Synchronized
    open fun recordAttributed(tx: ShopTransaction, attribution: ShopSaleAttribution) {
        check(!closed) { "Shop history storage is closed" }
        outbox.appendAttributed(tx, attribution)
    }

    @Synchronized
    override fun close() {
        closed = true
        executor.shutdownNow()
    }
}
