package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.Bukkit

/** On join, summarise sales the owner missed while offline, then mark them seen (SP6). */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
open class ShopSaleJoinNotifier(
    private val storage: ShopNotificationStorage,
    private val config: EnthusiaMarketConfig,
    private val lang: LangService,
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!config.shop.notifyEnabled) return
        val joinedPlayer = event.player
        val owner = joinedPlayer.uniqueId
        storage.notify(owner) { summary ->
            if (config.shop.notifyEnabled && joinedPlayer.isOnline && Bukkit.getPlayer(owner) === joinedPlayer) {
                joinedPlayer.sendMessage(lang.msg("shop.notify.away_summary", "count" to summary.count))
                true
            } else {
                false
            }
        }
    }
}
