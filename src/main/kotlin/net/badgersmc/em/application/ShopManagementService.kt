package net.badgersmc.em.application

import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.nexus.annotations.Service
import java.util.UUID

/**
 * Player-facing shop management operations (ItemShops parity sub-project 1):
 * list / trust / untrust / delete over the owner's shops. All mutations are
 * ownership-checked against the actor; menus are a convenience layer on top.
 */
@Service
class ShopManagementService(
    private val shopRepository: ShopRepository,
    private val access: ShopAccessPolicy? = null,
) {
    private val log = java.util.logging.Logger.getLogger(ShopManagementService::class.java.name)

    fun shopsOwnedBy(owner: UUID): List<Shop> = if (access == null) shopRepository.findByOwner(owner)
        else shopRepository.all().filter { canEdit(it, owner) || canDelete(it, owner) }

    fun canEdit(shop: Shop, actor: UUID): Boolean =
        access?.allows(shop, actor, net.badgersmc.em.domain.ports.GuildProvider.GuildPermission.MODIFY_SHOP_PRICES)
            ?: (shop.owner == actor || actor in shop.trusted)

    fun canDelete(shop: Shop, actor: UUID): Boolean =
        if (access?.isGuildShop(shop) == true)
            access.memberAllows(shop, actor, net.badgersmc.em.domain.ports.GuildProvider.GuildPermission.EDIT_SHOP_STOCK)
        else shop.owner == actor

    /** Check field-specific authority against the current row when a menu submits. */
    fun saveEdits(actor: UUID, draft: Shop, admin: Boolean = false): Boolean {
        val current = shopRepository.findById(draft.id) ?: return false
        if (!admin && !maySaveEdits(actor, current, draft)) return false
        shopRepository.upsert(current.copy(
            sellItem = draft.sellItem, sellAmount = draft.sellAmount, costItem = draft.costItem,
            costAmount = draft.costAmount, hopperAllowIn = draft.hopperAllowIn,
            hopperAllowOut = draft.hopperAllowOut, frozen = draft.frozen, searchEnabled = draft.searchEnabled,
        ))
        return true
    }

    private fun maySaveEdits(actor: UUID, current: Shop, draft: Shop): Boolean {
        if (access?.isGuildShop(current) != true) return canEdit(current, actor)
        if (!mayChangePrice(actor, current, draft)) return false
        if (!mayChangeStock(actor, current, draft)) return false
        if (controlsChanged(current, draft) && !canDelete(current, actor)) return false
        return canEdit(current, actor) || access.allows(current, actor,
            net.badgersmc.em.domain.ports.GuildProvider.GuildPermission.EDIT_SHOP_STOCK)
    }

    private fun mayChangePrice(actor: UUID, current: Shop, draft: Shop): Boolean =
        !priceChanged(current, draft) || canEdit(current, actor)

    private fun mayChangeStock(actor: UUID, current: Shop, draft: Shop): Boolean =
        !stockChanged(current, draft) || access?.allows(current, actor,
            net.badgersmc.em.domain.ports.GuildProvider.GuildPermission.EDIT_SHOP_STOCK) == true

    private fun priceChanged(current: Shop, draft: Shop): Boolean =
        current.costAmount != draft.costAmount || current.costItem != draft.costItem

    private fun stockChanged(current: Shop, draft: Shop): Boolean =
        current.sellItem != draft.sellItem || current.sellAmount != draft.sellAmount

    private fun controlsChanged(current: Shop, draft: Shop): Boolean =
            current.hopperAllowIn != draft.hopperAllowIn || current.hopperAllowOut != draft.hopperAllowOut ||
            current.frozen != draft.frozen || current.searchEnabled != draft.searchEnabled

    /** Trust [target] on each of [shopIds] the [actor] actually owns. Returns count changed. */
    fun trust(actor: UUID, target: UUID, shopIds: List<Long>): Int =
        mutateOwned(actor, shopIds) { it.copy(trusted = it.trusted + target) }

    /** Untrust [target] on each of [shopIds] the [actor] owns. Returns count changed. */
    fun untrust(actor: UUID, target: UUID, shopIds: List<Long>): Int =
        mutateOwned(actor, shopIds) { it.copy(trusted = it.trusted - target) }

    fun trustAll(actor: UUID, target: UUID): Int =
        mutateAll(shopsOwnedBy(actor).filter { canDelete(it, actor) }) { it.copy(trusted = it.trusted + target) }

    fun untrustAll(actor: UUID, target: UUID): Int =
        mutateAll(shopsOwnedBy(actor).filter { canDelete(it, actor) }) { it.copy(trusted = it.trusted - target) }

    /** Delete a single shop if [actor] owns it. Returns true when deleted. */
    fun delete(actor: UUID, shopId: Long): Boolean {
        val shop = shopRepository.findById(shopId) ?: return false
        if (!canDelete(shop, actor)) return false
        shopRepository.delete(shopId)
        fireShopDeleted(shop.owner)
        return true
    }

    /** Delete every shop [actor] owns. Returns count deleted. */
    fun deleteAll(actor: UUID): Int {
        if (access == null) {
            val owned = shopRepository.findByOwner(actor)
            val count = shopRepository.deleteByOwner(actor)
            owned.forEach { fireShopDeleted(it.owner) }
            return count
        }
        val owned = shopsOwnedBy(actor).filter { canDelete(it, actor) }
        if (owned.isEmpty()) return 0
        return owned.count { delete(actor, it.id) }
    }

    /** Delete a shop regardless of owner (admin tooling, SP5). Returns true when deleted. */
    fun adminDelete(shopId: Long): Boolean {
        val shop = shopRepository.findById(shopId) ?: return false
        shopRepository.delete(shopId)
        fireShopDeleted(shop.owner)
        return true
    }

    /**
     * Fire [ShopDeletedEvent] so listeners (analytics, advancement hooks, sign
     * cleanup) react to command/menu/breakdelete deletes the same way they do to
     * container-break deletes. Null-safe + best-effort: `getServer()` is null in
     * unit tests (no event fired, no NPE), mirroring AuctionLifecycleService.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun fireShopDeleted(owner: UUID) {
        try {
            org.bukkit.Bukkit.getServer()?.pluginManager?.callEvent(
                net.badgersmc.em.events.ShopDeletedEvent(owner)
            )
        } catch (e: Exception) {
            log.warning("Failed to fire ShopDeletedEvent: ${e.message}")
        }
    }

    private fun mutateOwned(actor: UUID, shopIds: List<Long>, edit: (Shop) -> Shop): Int {
        var changed = 0
        for (id in shopIds) {
            val shop = shopRepository.findById(id) ?: continue
            if (!canDelete(shop, actor)) continue
            val updated = edit(shop)
            if (updated != shop) {
                shopRepository.upsert(updated)
                changed++
            }
        }
        return changed
    }

    /** Apply [edit] to shops already known to belong to the actor (no re-fetch, no owner re-check). */
    private fun mutateAll(owned: List<Shop>, edit: (Shop) -> Shop): Int {
        var changed = 0
        for (shop in owned) {
            val updated = edit(shop)
            if (updated != shop) {
                shopRepository.upsert(updated)
                changed++
            }
        }
        return changed
    }
}
