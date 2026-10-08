package net.badgersmc.em.infrastructure.lumaguilds

import org.bukkit.Bukkit
import java.util.UUID

/** Public Bukkit service only; older companions fail closed without a binary API requirement. */
internal object OptionalGuildAllianceLookup {
    fun areAllied(guild: UUID, ally: UUID): Boolean = runCatching {
        val manager = Bukkit.getServicesManager()
        val type = manager.knownServices.firstOrNull { it.name == "net.lumalyte.lg.api.GuildAllianceLookup" }
            ?: return false
        val provider = manager.load(type) ?: return false
        type.getMethod("areAllied", UUID::class.java, UUID::class.java).invoke(provider, guild, ally) == true
    }.getOrDefault(false)
}
