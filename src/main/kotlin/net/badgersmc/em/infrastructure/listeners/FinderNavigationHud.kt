package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.FinderNavigationMode
import net.badgersmc.em.application.FinderTrailTracker
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

/** Owns only Market's temporary HUD output. Labels are captured once, without per-render queries. */
internal class FinderNavigationHud(
    private val config: EnthusiaMarketConfig,
    private val lang: LangService,
    private val plugin: Plugin,
) {
    private data class Session(val player: Player, val stall: String, val item: String,
                               var arrived: Boolean = false, var actionBar: Boolean = false)
    private val sessions = mutableMapOf<UUID, Session>()
    internal var locator: FinderLocator = FinderLocators.create(plugin)
    private var warned = false

    fun start(player: Player, shop: Shop) {
        stop(player)
        val material = ItemStackSerializer.deserialize(shop.sellItem)?.type?.name ?: "SHOP"
        val label = material.lowercase(Locale.ROOT).split('_').joinToString(" ") {
            it.replaceFirstChar(Char::titlecase)
        }
        sessions[player.uniqueId] = Session(player, shop.stallId, label)
    }

    /** Returns whether this recipient still needs direction particles. Outline rendering is independent. */
    fun render(player: Player, frame: FinderTrailTracker.Frame): Boolean {
        val session = sessions[player.uniqueId] ?: return true
        val target = frame.target ?: return true
        val mode = FinderNavigationPreferences.get(player, config.finderTrail.mode)
        val shown = renderLocator(player, frame.waypoint ?: target, mode, frame.arrived)
        renderStatus(session, frame)
        return mode != FinderNavigationMode.LOCATOR || !shown
    }

    private fun renderLocator(player: Player, target: FinderTrailTracker.Point,
                              mode: FinderNavigationMode, arrived: Boolean): Boolean = attemptLocator {
        if (hideLocator(mode, arrived)) {
            locator.clear(player)
            false
        } else {
            locator.show(player, target, style(player))
        }
    }

    private fun hideLocator(mode: FinderNavigationMode, arrived: Boolean): Boolean =
        arrived || mode == FinderNavigationMode.PARTICLES || !config.finderTrail.locator.enabled

    private fun attemptLocator(action: () -> Boolean): Boolean {
        return try {
            action()
        } catch (error: RuntimeException) {
            warn(error)
            false
        } catch (error: LinkageError) {
            warn(error)
            false
        }
    }

    private fun style(player: Player): String {
        val configured = config.finderTrail.locator.style
        val valid = configured.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))
        val packAvailable = !config.finderTrail.locator.requireResourcePack || player.hasResourcePack()
        return if (valid && (configured.startsWith("minecraft:") || packAvailable)) configured else VANILLA_STYLE
    }

    private fun renderStatus(session: Session, frame: FinderTrailTracker.Frame) {
        arrivalFeedback(session, frame.arrived)
        when {
            config.finderTrail.showDistance -> distanceStatus(session, frame)
            session.actionBar -> {
                session.player.sendActionBar(Component.empty())
                session.actionBar = false
            }
        }
    }

    private fun arrivalFeedback(session: Session, arrived: Boolean) {
        if (arrived && !session.arrived && config.finderTrail.arrivalSound) {
            playArrivalSound(session.player)
        }
        session.arrived = arrived
    }

    private fun playArrivalSound(player: Player) {
        val options = config.finderTrail
        val sound = runCatching { Sound.valueOf(options.arrivalSoundName.uppercase(Locale.ROOT)) }
            .getOrDefault(Sound.BLOCK_NOTE_BLOCK_PLING)
        val volume = options.arrivalVolume.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.4
        val pitch = options.arrivalPitch.takeIf { it.isFinite() }?.coerceIn(0.5, 2.0) ?: 1.5
        player.playSound(player.location, sound, volume.toFloat(), pitch.toFloat())
    }

    private fun distanceStatus(session: Session, frame: FinderTrailTracker.Frame) {
        val point = session.player.location
        val distance = frame.remainingDistance ?: frame.target?.distance(FinderTrailTracker.Point(point.x, point.y, point.z)) ?: return
        val message = if (frame.arrived) "finder_trail.arrived" else "finder_trail.destination"
        session.player.sendActionBar(lang.msg(message, "stall" to session.stall, "item" to session.item,
            "distance" to ceil(distance).toInt().toString()))
        session.actionBar = true
    }

    fun retain(active: Set<UUID>) {
        sessions.keys.filter { it !in active }.forEach { sessions[it]?.player?.let(::stop) }
    }

    fun stop(player: Player) {
        val session = sessions.remove(player.uniqueId)
        try {
            locator.clear(player)
        } catch (error: RuntimeException) {
            warn(error)
        } catch (error: LinkageError) {
            warn(error)
        }
        if (session?.actionBar == true && player.isOnline) player.sendActionBar(Component.empty())
    }

    fun clear() {
        sessions.values.toList().forEach { stop(it.player) }
    }

    private fun warn(error: Throwable) {
        if (!warned) plugin.logger.warning("Stall locator failed (${error.javaClass.simpleName}); using particles.")
        warned = true
    }

    private companion object {
        const val VANILLA_STYLE = "minecraft:default"
    }
}
