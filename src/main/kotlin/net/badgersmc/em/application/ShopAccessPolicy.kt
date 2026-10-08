package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Service
import java.util.UUID

/** Guild permissions are evaluated from current membership, never from shop creator identity. */
@Service
class ShopAccessPolicy(
    private val stalls: StallRepository,
    private val guilds: GuildProvider,
) {
    fun isGuildShop(shop: Shop): Boolean = !shop.adminShop &&
        stalls.findById(net.badgersmc.em.domain.stall.StallId(shop.stallId))?.owner?.type == OwnerType.GUILD

    fun allows(shop: Shop, actor: UUID, permission: GuildProvider.GuildPermission): Boolean {
        if (shop.adminShop) return shop.owner == actor
        val stall = stalls.findById(net.badgersmc.em.domain.stall.StallId(shop.stallId))
        if (stall?.owner?.type == OwnerType.GUILD) {
            return allowsGuild(stall, actor, permission)
        }
        return shop.owner == actor || actor in shop.trusted
    }

    private fun allowsGuild(stall: net.badgersmc.em.domain.stall.Stall, actor: UUID, permission: GuildProvider.GuildPermission): Boolean =
        stall.isActiveGuildStall() && guilds.isMember(actor, stall.owner.id) &&
            guilds.hasShopPermission(actor, stall.owner.id, permission)
}
