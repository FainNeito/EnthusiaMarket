package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.StallRegionAccessService
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.MarketMutationGate
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.event.world.WorldLoadEvent
import java.util.logging.Logger

/**
 * Reconciles every eligible stall's access after startup and later world loads.
 * The historical class name is retained; SOLO and forfeited stalls need the
 * same recovery as guild stalls so saved WorldGuard ACLs cannot outlive ownership.
 *
 * Runs after [ServerLoadEvent] so all plugins (LumaGuilds, WorldGuard) have
 * finished loading and their APIs are available. A short additional delay
 * ensures DI beans are fully materialized.
 */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
class GuildStallResyncOnStartup(
    private val stalls: StallRepository,
    private val guildProvider: GuildProvider,
    private val regionMembers: RegionMemberSync,
    private val mutationGate: MarketMutationGate = MarketMutationGate.Open,
) : Listener {

    private val log = Logger.getLogger(javaClass.name)
    private val access = StallRegionAccessService(guildProvider, regionMembers, mutationGate)
    private var serverLoaded = false

    @EventHandler
    fun onServerLoad(@Suppress("UNUSED_PARAMETER") event: ServerLoadEvent) {
        serverLoaded = true
        // Run after a delay so all plugins are fully initialized
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("EnthusiaMarket")!!,
            Runnable { resyncStalls() },
            100L, // ~5 seconds after ServerLoad
        )
    }

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        if (!serverLoaded) return // Initial worlds are covered by ServerLoadEvent.
        val worldName = event.world.name
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("EnthusiaMarket")!!,
            Runnable { resyncStalls(worldName) },
            1L,
        )
    }

    private fun resyncStalls(worldName: String? = null) {
        var synced = 0
        var skipped = 0
        var errors = 0

        for (stall in stalls.all()) {
            if (worldName != null && stall.world != worldName) continue

            try {
                if (access.sync(stall)) synced++ else skipped++
            } catch (e: Exception) {
                log.warning(
                    "GuildStallResyncOnStartup: failed to resync WG for stall ${stall.id.value} " +
                        "(state=${stall.state}): ${e.message}"
                )
                errors++
            }
        }

        log.info(
            "GuildStallResyncOnStartup: resynced WG for $synced stall(s), " +
                "$skipped preserved, $errors errors"
        )
    }
}
