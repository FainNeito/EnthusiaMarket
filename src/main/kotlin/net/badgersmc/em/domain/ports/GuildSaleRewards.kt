package net.badgersmc.em.domain.ports

import java.util.UUID

/** Before payment / after successful delivery boundary, independent of Bukkit events. */
interface GuildSaleRewards {
    fun prepare(guild: UUID, buyer: UUID, shopId: Long): UUID
    fun completed(id: UUID)
    fun aborted(id: UUID)
}

interface GuildShopXpGateway {
    fun prepare(id: UUID, guild: UUID, buyer: UUID, occurredAt: Long): String
    fun complete(id: UUID): String
}

data class GuildSaleIntent(val id: UUID, val guild: UUID, val buyer: UUID, val shopId: Long, val occurredAt: Long)

interface GuildSaleJournal {
    fun prepare(sale: GuildSaleIntent)
    fun completed(id: UUID)
    fun aborted(id: UUID)
    fun pending(limit: Int): List<GuildSaleIntent>
    fun acknowledged(id: UUID, outcome: String)
}
