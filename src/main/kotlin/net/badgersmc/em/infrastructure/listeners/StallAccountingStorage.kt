package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.domain.shop.StallAccountingObservation
import net.badgersmc.em.domain.shop.StallAccountingRepository
import net.badgersmc.em.infrastructure.persistence.StallAccountingOutbox
import net.badgersmc.nexus.annotations.Component
import org.bukkit.plugin.Plugin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Single delivery worker owns SQL ordering; capture performs no SQL or queued Bukkit access. */
@Component
open class StallAccountingStorage(repository: StallAccountingRepository, plugin: Plugin) : AutoCloseable {
    private val outbox = StallAccountingOutbox(plugin.dataFolder.toPath().resolve("stall-accounting-outbox"), repository)
    private val executor = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "Market-stall-accounting").apply { isDaemon = true } }
    private var closed = false
    private var lastReport = 0L
    init {
        executor.scheduleWithFixedDelay({
            try { outbox.drain() } catch (failure: Exception) {
                val now = System.nanoTime()
                if (lastReport == 0L || now - lastReport >= TimeUnit.MINUTES.toNanos(1)) {
                    lastReport = now; plugin.logger.warning("Stall accounting deferred; records retained: ${failure.message}")
                }
            }
        }, 0, 1, TimeUnit.SECONDS)
    }
    @Synchronized open fun record(observation: StallAccountingObservation) { check(!closed); outbox.append(observation) }
    @Synchronized override fun close() { closed = true; executor.shutdownNow() }
}
