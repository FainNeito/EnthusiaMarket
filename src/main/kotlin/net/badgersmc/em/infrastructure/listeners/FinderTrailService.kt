package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.FinderTrailTracker
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.ports.RegionProvider
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.time.Instant
import kotlin.math.floor

@net.badgersmc.nexus.paper.listeners.Listener
@Component
class FinderTrailService(
    private val config: EnthusiaMarketConfig,
    private val lang: LangService,
    private val plugin: Plugin,
    private val regions: RegionProvider,
) : Listener, AutoCloseable {
    private val tracker = FinderTrailTracker()
    internal val navigation = FinderNavigationHud(config, lang, plugin)
    private var task: BukkitTask? = null
    internal var clock: java.time.Clock = java.time.Clock.systemUTC()

    fun start(player: Player, shop: Shop) {
        if (!config.finderTrail.enabled) { player.sendMessage(lang.msg("finder_trail.disabled")); return }
        val target = FinderTrailTracker.Point(shop.signX + 0.5, shop.signY + 0.5, shop.signZ + 0.5)
        val position = player.location
        val current = FinderTrailTracker.Point(position.x, position.y, position.z)
        if (position.world?.name != shop.signWorld || current.distance(target) > range()) {
            player.sendMessage(lang.msg("finder_trail.out_of_range")); return
        }
        val trail = FinderTrailTracker.Trail(shop.signWorld, target,
            Instant.now(clock).plusSeconds(config.finderTrail.durationSeconds.coerceIn(1, MAX_DURATION)),
            footprint(shop))
        if (!tracker.start(player.uniqueId, trail, config.finderTrail.maxActive.coerceIn(1, MAX_ACTIVE))) {
            player.sendMessage(lang.msg("finder_trail.busy")); return
        }
        navigation.start(player, shop)
        ensureRenderTask()
        player.sendMessage(lang.msg("finder_trail.started", "seconds" to config.finderTrail.durationSeconds.coerceIn(1, MAX_DURATION)))
    }

    private fun footprint(shop: Shop): RegionProvider.Footprint? =
        if (config.finderTrail.outline.enabled) regions.footprint(shop.signWorld, shop.stallId) else null

    private fun ensureRenderTask() {
        if (task == null) task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { render() }, 1, RENDER_TICKS)
    }

    fun stop(player: Player) {
        tracker.stop(player.uniqueId)
        navigation.stop(player)
        player.sendMessage(lang.msg("finder_trail.stopped"))
    }

    fun setMode(player: Player, value: String) {
        if (!FinderNavigationPreferences.set(player, value)) {
            player.sendMessage(lang.msg("finder_trail.mode_usage"))
            return
        }
        player.sendMessage(lang.msg("finder_trail.mode_set",
            "mode" to FinderNavigationPreferences.get(player, config.finderTrail.mode).name.lowercase()))
    }

    internal fun render() {
        if (!config.finderTrail.enabled) tracker.clear()
        val plans = tracker.renderFrames(Instant.now(clock), FinderTrailTracker.RenderSettings(
            config.finderTrail.maxParticlesPerRender.coerceIn(0, MAX_BUDGET),
            range(), FinderOutlineStyle.options(config.finderTrail.outline))) { id ->
            val player = Bukkit.getPlayer(id)?.takeIf { it.isOnline && !it.isDead } ?: return@renderFrames null
            val point = player.location
            player.world.name to FinderTrailTracker.Point(point.x, point.y + 0.5, point.z)
        }
        val dust = FinderOutlineStyle.dust(config.finderTrail.outline)
        navigation.retain(plans.keys)
        plans.forEach { (id, frame) ->
            val player = Bukkit.getPlayer(id) ?: return@forEach
            val direction = if (navigation.render(player, frame)) frame.direction else emptyList()
            direction.filter { loaded(player, it) }
                .forEach { player.spawnParticle(Particle.END_ROD, it.x, it.y, it.z, 1, 0.0, 0.0, 0.0, 0.0) }
            frame.outline.filter { loaded(player, it) }
                .forEach { player.spawnParticle(Particle.DUST, it.x, it.y, it.z, 1, 0.0, 0.0, 0.0, 0.0, dust) }
        }
        if (tracker.count() == 0) { task?.cancel(); task = null }
    }

    private fun loaded(player: Player, point: FinderTrailTracker.Point): Boolean =
        player.world.isChunkLoaded(floor(point.x).toInt() shr 4, floor(point.z).toInt() shr 4)

    private fun range(): Double = config.finderTrail.maxRange.takeIf { it.isFinite() && it > 0 }
        ?.coerceAtMost(MAX_RANGE) ?: DEFAULT_RANGE

    @EventHandler fun onQuit(event: PlayerQuitEvent) { remove(event.player) }
    @EventHandler fun onWorldChange(event: PlayerChangedWorldEvent) { remove(event.player) }
    @EventHandler fun onDeath(event: PlayerDeathEvent) { remove(event.player) }

    private fun remove(player: Player) {
        tracker.stop(player.uniqueId)
        navigation.stop(player)
    }

    override fun close() { tracker.clear(); navigation.clear(); task?.cancel(); task = null }

    companion object {
        private const val MAX_DURATION = 300L
        private const val MAX_ACTIVE = 256
        private const val MAX_BUDGET = 1000
        private const val MAX_RANGE = 1024.0
        private const val DEFAULT_RANGE = 256.0
        private const val RENDER_TICKS = 10L
    }
}
