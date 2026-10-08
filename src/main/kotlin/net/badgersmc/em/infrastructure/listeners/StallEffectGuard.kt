package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.StallAccessSettingsService
import net.badgersmc.em.domain.stall.StallCapability
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID

/** Suppress existing selected effects; restoration retains only unexpired original duration. */
@Component
@net.badgersmc.nexus.paper.listeners.Listener
class StallEffectGuard(
    private val plugin: JavaPlugin,
    private val regions: StallAccessRegions,
    private val access: StallAccessSettingsService,
    private val permissions: StallAccessListener,
) : Listener, AutoCloseable {
    private data class Held(val effect: PotionEffect, val expiresAt: Long?) {
        fun remaining(now: Long): PotionEffect? {
            val ticks = duration(now) ?: return null
            return PotionEffect(
                effect.type, ticks, effect.amplifier, effect.isAmbient, effect.hasParticles(), effect.hasIcon(),
            )
        }
        private fun duration(now: Long): Int? {
            val deadline = expiresAt ?: return -1
            val ticks = ((deadline - now) / 50).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            return ticks.takeIf { it > 0 }
        }
    }
    private val held = mutableMapOf<UUID, MutableMap<PotionEffectType, Held>>()
    private val changing = mutableSetOf<UUID>()
    private val lastAllowed = mutableMapOf<UUID, org.bukkit.Location>()
    private val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
        Bukkit.getOnlinePlayers().forEach { reconcile(it) }
    }, 1L, 10L)

    private fun blocked(player: Player): Set<String> = regions.at(player.location)
        .filter { it.state in setOf(net.badgersmc.em.domain.stall.StallState.OWNED, net.badgersmc.em.domain.stall.StallState.GRACE) }
        .flatMap { access.current(it).blockedEffects }.toSet()

    internal fun reconcile(player: Player, now: Long = System.currentTimeMillis()) {
        val blocked = blocked(player)
        val effects = held.getOrPut(player.uniqueId) { mutableMapOf() }
        player.activePotionEffects.filter { it.type.name in blocked }.forEach { effect ->
            effects.putIfAbsent(effect.type, Held(effect, if (effect.isInfinite) null else now + effect.duration.toLong() * 50))
            change(player) { player.removePotionEffect(effect.type) }
        }
        effects.keys.filter { it.name !in blocked }.forEach { type ->
            val effect = effects.remove(type)?.remaining(now)
            if (effect != null && player.getPotionEffect(type) == null) change(player) { player.addPotionEffect(effect) }
        }
        if (effects.isEmpty()) held.remove(player.uniqueId)
        reconcileEntry(player)
    }

    private fun reconcileEntry(player: Player) {
        if (permissions.allowed(player, player.location, StallCapability.ENTRY)) {
            lastAllowed[player.uniqueId] = player.location.clone()
            return
        }
        val candidate = listOfNotNull(lastAllowed[player.uniqueId], player.world.spawnLocation).firstOrNull { location ->
            allowedDestination(player, location)
        } ?: return
        player.teleport(candidate)
    }

    private fun allowedDestination(player: Player, location: org.bukkit.Location): Boolean =
        location.world?.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4) == true &&
            permissions.allowed(player, location, StallCapability.ENTRY)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEffect(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        if (player.uniqueId in changing) return
        val effect = event.newEffect ?: return
        val stalls = regions.at(player.location).filter {
            it.state in setOf(net.badgersmc.em.domain.stall.StallState.OWNED, net.badgersmc.em.domain.stall.StallState.GRACE)
        }
        val incoming = event.cause in setOf(EntityPotionEffectEvent.Cause.POTION_SPLASH, EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD)
        if (stalls.any { effect.type.name in access.current(it).blockedEffects ||
                (incoming && !access.current(it).allowIncomingPotions) }) event.isCancelled = true
    }

    @EventHandler fun onDeath(event: PlayerDeathEvent) { held.remove(event.entity.uniqueId); lastAllowed.remove(event.entity.uniqueId) }
    @EventHandler fun onQuit(event: PlayerQuitEvent) { restore(event.player); lastAllowed.remove(event.player.uniqueId) }

    private fun restore(player: Player) {
        val effects = held.remove(player.uniqueId) ?: return
        effects.values.mapNotNull { it.remaining(System.currentTimeMillis()) }.forEach { effect ->
            if (player.getPotionEffect(effect.type) == null) change(player) { player.addPotionEffect(effect) }
        }
    }

    private fun change(player: Player, action: () -> Unit) {
        changing.add(player.uniqueId)
        try { action() } finally { changing.remove(player.uniqueId) }
    }

    override fun close() {
        task.cancel()
        Bukkit.getOnlinePlayers().forEach(::restore)
        held.clear(); lastAllowed.clear()
    }
}
