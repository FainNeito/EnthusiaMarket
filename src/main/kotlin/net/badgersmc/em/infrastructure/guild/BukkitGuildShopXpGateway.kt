package net.badgersmc.em.infrastructure.guild

import net.badgersmc.em.domain.ports.GuildShopXpGateway
import org.bukkit.Bukkit
import java.util.UUID

/** Resolve the provider's own interface class, never Market's shaded Kotlin or a stale cached service. */
class BukkitGuildShopXpGateway : GuildShopXpGateway {
    override fun prepare(id: UUID, guild: UUID, buyer: UUID, occurredAt: Long): String =
        invoke("prepare", arrayOf(UUID::class.java, UUID::class.java, UUID::class.java, java.lang.Long.TYPE), id, guild, buyer, occurredAt)

    override fun complete(id: UUID): String = invoke("complete", arrayOf(UUID::class.java), id)

    private fun invoke(name: String, types: Array<Class<*>>, vararg args: Any): String {
        val manager = Bukkit.getServicesManager()
        val type = manager.knownServices.firstOrNull { it.name == "net.lumalyte.lg.api.GuildShopXpApi" }
            ?: error("Guild-shop XP API is unavailable; install the reviewed companion Guilds build")
        val registration = manager.getRegistration(type) ?: error("Guild-shop XP registration disappeared")
        check(registration.plugin.isEnabled) { "Guild-shop XP provider disabled" }
        val provider = registration.provider
        check(type.getMethod("apiVersion").invoke(provider) == 1) { "Unsupported guild-shop XP API version" }
        return type.getMethod(name, *types).invoke(provider, *args) as String
    }
}
