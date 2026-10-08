package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.RentLoginWarningService
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@net.badgersmc.nexus.paper.listeners.Listener
@Component
class RentLoginWarningListener(
    stalls: StallRepository,
    private val warnings: RentLoginWarningService,
    private val config: EnthusiaMarketConfig,
    private val lang: LangService,
    plugin: Plugin,
) : Listener, AutoCloseable {
    private val lastDelivery = mutableMapOf<UUID, Instant>()
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64),
        { task -> Thread(task, "EnthusiaMarket-rent-warnings").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val worker = RentWarningWorker(stalls, executor,
        { task -> plugin.server.scheduler.runTask(plugin, task) },
        { failure -> plugin.logger.warning("Rent warning unavailable: ${failure.message}") })

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!config.rentWarnings.enabled) return
        val player = event.player
        val actor = player.uniqueId
        val now = Instant.now()
        val cooldown = config.rentWarnings.cooldownSeconds.coerceIn(0, MAX_COOLDOWN_SECONDS)
        lastDelivery.entries.removeIf { it.value.plusSeconds(cooldown).isBefore(now) }
        if (lastDelivery[actor]?.plusSeconds(cooldown)?.isAfter(now) == true) return
        worker.load(actor) { snapshot ->
            val currentSession = player.isOnline && Bukkit.getPlayer(actor) === player
            val fresh = Instant.now().isBefore(now.plusSeconds(MAX_REQUEST_AGE_SECONDS))
            if (config.rentWarnings.enabled && currentSession && fresh) {
                val notices = warnings.warnings(snapshot, actor, Instant.now())
                val limit = config.rentWarnings.maxMessages.coerceIn(1, MAX_MESSAGES)
                notices.take(limit).forEach { warning ->
                    player.sendMessage(lang.msg(if (warning.grace) "rent_warning.grace" else "rent_warning.due",
                        "stall" to warning.stallId, "deadline" to warning.deadline.toString()))
                    if (warning.insufficientFunds) player.sendMessage(lang.msg(
                        if (warning.guildPayer) "rent_warning.guild_funds" else "rent_warning.personal_funds",
                        "amount" to warning.amount))
                }
                if (notices.size > limit) player.sendMessage(lang.msg("rent_warning.more",
                    "count" to notices.size - limit))
                if (notices.isNotEmpty()) {
                    player.sendMessage(lang.msg("rent_warning.renew"))
                    lastDelivery[actor] = Instant.now()
                }
            }
        }
    }

    override fun close() {
        worker.close()
        executor.shutdownNow()
        lastDelivery.clear()
    }

    companion object {
        private const val MAX_MESSAGES = 50
        private const val MAX_REQUEST_AGE_SECONDS = 5L
        private const val MAX_COOLDOWN_SECONDS = 86400L
    }
}
