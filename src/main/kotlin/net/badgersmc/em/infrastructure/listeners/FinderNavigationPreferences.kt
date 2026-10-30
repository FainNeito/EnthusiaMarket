package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.FinderNavigationMode
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

internal object FinderNavigationPreferences {
    private val key = NamespacedKey("enthusiamarket", "finder_navigation_mode")

    fun get(player: Player, defaultMode: String): FinderNavigationMode {
        val preference = player.persistentDataContainer.get(key, PersistentDataType.STRING)
        return preference?.let(FinderNavigationMode::parse)
            ?: FinderNavigationMode.parse(defaultMode) ?: FinderNavigationMode.BOTH
    }

    fun set(player: Player, value: String): Boolean {
        if (value.equals("default", ignoreCase = true)) {
            player.persistentDataContainer.remove(key)
            return true
        }
        val mode = FinderNavigationMode.parse(value) ?: return false
        player.persistentDataContainer.set(key, PersistentDataType.STRING, mode.name)
        return true
    }
}
