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
        val shown = renderLocator(player, target, mode, frame.arrived)
        renderStatus(session, frame)
        return mode != FinderNavigationMode.LOCATOR || !shown
    }

    private fun renderLocator(player: Player, target: FinderTrailTracker.Point,
                              mode: FinderNavigationMode, arrived: Boolean): Boolean = try {
        if (arrived || mode == FinderNavigationMode.PARTICLES || !config.finderTrail.locator.enabled) {
            locator.clear(player)
            false
        } else {
            locator.show(player, target, style(player))
        }
    } catch (error: RuntimeException) {
        warn(error)
        false
    } catch (error: LinkageError) {
        warn(error)
        false
    }

    private fun style(player: Player): String {
        val configured = config.finderTrail.locator.style
        val valid = configured.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))
        val packAvailable = !config.finderTrail.locator.requireResourcePack || player.hasResourcePack()
        return if (valid && (configured.startsWith("minecraft:") || packAvailable)) configured else VANILLA_STYLE
    }

    private fun renderStatus(session: Session, frame: FinderTrailTracker.Frame) {
        val player = session.player
        if (frame.arrived && !session.arrived && config.finderTrail.arrivalSound) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.5f)
        }
        session.arrived = frame.arrived
        if (config.finderTrail.showDistance) {
            val point = player.location
            val distance = frame.target?.distance(FinderTrailTracker.Point(point.x, point.y, point.z)) ?: return
            player.sendActionBar(lang.msg(if (frame.arrived) "finder_trail.arrived" else "finder_trail.destination",
                "stall" to session.stall, "item" to session.item, "distance" to ceil(distance).toInt().toString()))
            session.actionBar = true
        } else if (session.actionBar) {
            player.sendActionBar(Component.empty())
            session.actionBar = false
        }
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
