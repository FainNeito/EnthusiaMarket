package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.FinderTrailTracker
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/** An optional private HUD transport; never changes vanilla player waypoints. */
interface FinderLocator {
    fun supported(player: Player): Boolean
    fun show(player: Player, target: FinderTrailTracker.Point, style: String): Boolean
    fun clear(player: Player)
}

internal object UnavailableFinderLocator : FinderLocator {
    override fun supported(player: Player) = false
    override fun show(player: Player, target: FinderTrailTracker.Point, style: String) = false
    override fun clear(player: Player) = Unit
}

internal object FinderLocators {
    fun create(plugin: Plugin): FinderLocator {
        if (!plugin.server.pluginManager.isPluginEnabled("packetevents")) return UnavailableFinderLocator
        return try {
            PacketEventsFinderLocator()
        } catch (error: LinkageError) {
            plugin.logger.warning("Stall locator adapter unavailable (${error.javaClass.simpleName}); using particles.")
            UnavailableFinderLocator
        }
    }
}
