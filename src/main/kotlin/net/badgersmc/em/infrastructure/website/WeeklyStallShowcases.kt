package net.badgersmc.em.infrastructure.website

import de.oliver.fancynpcs.api.FancyNpcsPlugin
import de.oliver.fancynpcs.api.Npc
import de.oliver.fancynpcs.api.NpcData
import de.oliver.fancynpcs.api.skins.SkinData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.TextDisplay
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

/** World entities and NPC registrations are confined to the server thread. */
internal class WeeklyStallShowcases private constructor(
    private val entries: List<Entry>,
) : AutoCloseable {
    private class Entry(val key: String, val title: String, val color: NamedTextColor, val location: Location) {
        var hologram: TextDisplay? = null
        var npc: Npc? = null
        var shownWinner: StallWeekWinner? = null
        var shownText: Component? = null
        @Volatile var skin: Pair<UUID, SkinData>? = null
    }

    /** Called off-thread after the website refresh. Cache only a resolved skin. */
    fun prepare(player: StallWeekDisplay, guild: StallWeekDisplay) {
        val api = FancyNpcsPlugin.get()
        entries.forEach { entry ->
            val winner = (if (entry.key == "player") player else guild).winner()
            val id = winner?.representative
            if (id == null || entry.skin?.first != id) {
                entry.skin = id?.let {
                    runCatching { api.skinManager.getByUUID(id, SkinData.SkinVariant.AUTO) }.getOrNull()
                        ?.takeIf { it.hasTexture() }?.let { skin -> id to skin }
                }
            }
        }
    }

    fun render(player: StallWeekDisplay, guild: StallWeekDisplay) {
        entries.forEach { entry ->
            val display = if (entry.key == "player") player else guild
            val winner = display.winner()
            renderNpc(entry, winner)
            val world = entry.location.world ?: return@forEach
            if (!world.isChunkLoaded(entry.location.blockX shr 4, entry.location.blockZ shr 4)) return@forEach
            val hologram = entry.hologram?.takeIf { it.isValid } ?: world.spawn(
                entry.location.clone().add(0.0, 2.55, 0.0), TextDisplay::class.java,
            ) {
                it.isPersistent = false
                it.billboard = org.bukkit.entity.Display.Billboard.CENTER
                it.isShadowed = true
                entry.hologram = it
                entry.shownText = null
            }
            val heading = Component.text(entry.title, entry.color)
            val result = if (winner == null) Component.text(display.line(), NamedTextColor.GRAY) else
                Component.text(winner.stall, NamedTextColor.WHITE).append(Component.newline())
                    .append(Component.text(winner.ownerName, entry.color))
            val text = heading.append(Component.newline()).append(result)
            if (entry.shownText != text) {
                hologram.text(text)
                entry.shownText = text
            }
        }
    }

    private fun renderNpc(entry: Entry, winner: StallWeekWinner?) {
        if (entry.shownWinner == winner && entry.npc != null) return
        removeNpc(entry)
        val skin = entry.skin?.takeIf { it.first == winner?.representative }?.second ?: return
        val api = FancyNpcsPlugin.get()
        val key = "enthusiamarket_weekly_${entry.key}"
        if (api.npcManager.getNpc(key) != null || winner == null) return
        val data = NpcData(key, winner.representative, entry.location.clone())
            .setDisplayName("<empty>").setSkinData(skin).setCollidable(false).setShowInTab(false)
        val npc = api.npcAdapter.apply(data)
        npc.isSaveToFile = false
        npc.create()
        api.npcManager.registerNpc(npc)
        entry.npc = npc
        entry.shownWinner = winner
        npc.checkAndUpdateVisibilityForAll()
    }

    private fun removeNpc(entry: Entry) {
        val npc = entry.npc ?: return
        entry.npc = null
        entry.shownWinner = null
        npc.removeForAll()
        if (Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) FancyNpcsPlugin.get().npcManager.removeNpc(npc)
    }

    override fun close() {
        entries.forEach {
            it.hologram?.remove()
            removeNpc(it)
        }
    }

    companion object {
        fun open(plugin: JavaPlugin): WeeklyStallShowcases? {
            val file = File(plugin.dataFolder, "weekly-stall-displays.yml")
            if (!file.exists()) plugin.saveResource(file.name, false)
            val config = YamlConfiguration.loadConfiguration(file)
            if (!config.getBoolean("enabled")) return null
            if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
                plugin.logger.warning("Weekly stall displays require FancyNpcs.")
                return null
            }
            val entries = listOf("player", "guild").mapNotNull { key ->
                val section = config.getConfigurationSection(key) ?: return@mapNotNull null
                val world = section.getString("world")?.let(Bukkit::getWorld) ?: return@mapNotNull null
                val coords = listOf("x", "y", "z").map { section.getDouble(it, Double.NaN) }
                val yaw = section.getDouble("yaw", 0.0).toFloat()
                if (coords.any { !it.isFinite() } || !yaw.isFinite()) return@mapNotNull null
                val guild = key == "guild"
                Entry(key, if (guild) "Guild Stall of the Week" else "Stall of the Week",
                    if (guild) NamedTextColor.LIGHT_PURPLE else NamedTextColor.GOLD,
                    Location(world, coords[0], coords[1], coords[2], yaw, 0f))
            }
            if (entries.size != 2 || entries[0].location.distanceSquaredIfSameWorld(entries[1].location) < 1.0) {
                plugin.logger.warning("Configure two distinct, loaded-world positions for weekly stall displays.")
                return null
            }
            return WeeklyStallShowcases(entries)
        }

        private fun Location.distanceSquaredIfSameWorld(other: Location) =
            if (world == other.world) distanceSquared(other) else Double.POSITIVE_INFINITY
    }
}
