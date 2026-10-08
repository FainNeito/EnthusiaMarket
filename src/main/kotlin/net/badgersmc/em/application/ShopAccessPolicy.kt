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
    private val stallAccess: net.badgersmc.em.domain.ports.StallAccessPolicy = net.badgersmc.em.domain.ports.StallAccessPolicy.Open,
) {
    fun isGuildShop(shop: Shop): Boolean = !shop.adminShop &&
        stalls.findById(net.badgersmc.em.domain.stall.StallId(shop.stallId))?.owner?.type == OwnerType.GUILD

    fun allows(shop: Shop, actor: UUID, permission: GuildProvider.GuildPermission): Boolean {
        if (shop.adminShop) return shop.owner == actor
        val capability = capability(permission)
        if (stallAccess.blacklistDenies(shop.stallId, actor)) return false
        val stall = stalls.findById(net.badgersmc.em.domain.stall.StallId(shop.stallId))
        if (stall?.owner?.type == OwnerType.GUILD) {
            if (!stallAccess.allows(shop.stallId, actor, capability)) return false
            return allowsGuild(stall, actor, permission) || stallAccess.alliedAllows(shop.stallId, actor, capability)
        }
        return shop.owner == actor || actor in shop.trusted
    }

    fun memberAllows(shop: Shop, actor: UUID, permission: GuildProvider.GuildPermission): Boolean {
        val stall = stalls.findById(net.badgersmc.em.domain.stall.StallId(shop.stallId)) ?: return false
        return stallAccess.allows(shop.stallId, actor, capability(permission)) && allowsGuild(stall, actor, permission)
    }

    private fun capability(permission: GuildProvider.GuildPermission) = when (permission) {
        GuildProvider.GuildPermission.ACCESS_SHOP_CHESTS -> net.badgersmc.em.domain.stall.StallCapability.CHESTS
        GuildProvider.GuildPermission.EDIT_SHOP_STOCK -> net.badgersmc.em.domain.stall.StallCapability.STOCK
        GuildProvider.GuildPermission.MODIFY_SHOP_PRICES -> net.badgersmc.em.domain.stall.StallCapability.PRICES
        GuildProvider.GuildPermission.MANAGE_SHOPS -> net.badgersmc.em.domain.stall.StallCapability.ENTRY
    }

    private fun allowsGuild(stall: net.badgersmc.em.domain.stall.Stall, actor: UUID, permission: GuildProvider.GuildPermission): Boolean =
        stall.isActiveGuildStall() && guilds.isMember(actor, stall.owner.id) &&
            guilds.hasShopPermission(actor, stall.owner.id, permission)
}
