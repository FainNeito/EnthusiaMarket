package net.badgersmc.em.infrastructure.website

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin

internal class StallWeekExpansion(
    private val plugin: JavaPlugin,
    private val display: StallWeekDisplay,
) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "enthusiamarket"
    override fun getAuthor(): String = "BadgersMC"
    override fun getVersion(): String = plugin.pluginMeta.version
    override fun persist(): Boolean = true
    override fun onRequest(player: OfflinePlayer?, params: String): String? =
        if (params.equals("stall_of_the_week", ignoreCase = true)) display.line() else null

    companion object {
        fun start(plugin: JavaPlugin): AutoCloseable? {
            val display = StallWeekDisplay()
            val expansion = StallWeekExpansion(plugin, display)
            if (!expansion.register()) return null
            val task = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable(display::refresh), 20L, 1200L)
            return AutoCloseable {
                task.cancel()
                expansion.unregister()
            }
        }
    }
}
