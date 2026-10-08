package net.badgersmc.em.application

import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopLocationIndex

/**
 * In-memory [ShopLocationIndex] (REQ-282). Application layer: depends on domain + Kotlin stdlib only.
 *
 * Container and sign coordinates resolve without a database query. Stable IDs reconcile metadata,
 * stock and moves in both maps. Access is synchronized so readers cannot observe a half-updated
 * pair of indices. Containers may have multiple signs; legacy duplicate sign coordinates resolve
 * deterministically to the lowest stable ID rather than depending on reindex order.
 */
class InMemoryShopLocationIndex : ShopLocationIndex {

    private val byCoord = mutableMapOf<String, MutableList<Shop>>()
    private val bySign = mutableMapOf<String, MutableList<Shop>>()
    private val byId = mutableMapOf<Long, Shop>()

    private fun key(world: String, x: Int, y: Int, z: Int): String = "$world:$x:$y:$z"

    private fun key(shop: Shop): String =
        key(shop.containerWorld, shop.containerX, shop.containerY, shop.containerZ)

    @Synchronized
    override fun shopsAt(world: String, x: Int, y: Int, z: Int): List<Shop> =
        byCoord[key(world, x, y, z)]?.toList() ?: emptyList()

    @Synchronized
    override fun shopAtSign(world: String, x: Int, y: Int, z: Int): Shop? =
        bySign[key(world, x, y, z)]?.minByOrNull { it.id }

    @Synchronized
    override fun updateStock(id: Long, stockCount: Int) {
        byId[id]?.let { put(it.copy(stockCount = stockCount)) }
    }

    @Synchronized
    override fun freezeByStall(stallId: String, frozen: Boolean) {
        byId.values.filter { it.stallId == stallId }.forEach { put(it.copy(frozen = frozen)) }
    }

    @Synchronized
    override fun put(shop: Shop) {
        if (shop.id != 0L) {
            byId[shop.id]?.let(::remove)
            byId[shop.id] = shop
        }
        byCoord.getOrPut(key(shop)) { mutableListOf() }.add(shop)
        bySign.getOrPut(signKey(shop)) { mutableListOf() }.add(shop)
    }

    private fun signKey(shop: Shop) = key(shop.signWorld, shop.signX, shop.signY, shop.signZ)

    @Synchronized
    override fun remove(shop: Shop) {
        val indexed = if (shop.id != 0L) byId.remove(shop.id) ?: shop else shop
        removeFrom(byCoord, key(indexed), indexed)
        removeFrom(bySign, signKey(indexed), indexed)
    }

    private fun removeFrom(entries: MutableMap<String, MutableList<Shop>>, k: String, shop: Shop) {
        val list = entries[k] ?: return
        // Match by stable id, not full-object equality: a delete may pass a DB-sourced Shop whose
        // non-key fields (e.g. stockCount, updated via updateStock without re-indexing) differ from
        // the indexed copy — equality removal would leave a stale ghost entry. id == 0 means unsaved.
        if (shop.id != 0L) {
            list.removeAll { it.id == shop.id }
        } else {
            list.remove(shop)
        }
        if (list.isEmpty()) entries.remove(k)
    }

    @Synchronized
    override fun rebuild(shops: Collection<Shop>) {
        byCoord.clear()
        bySign.clear()
        byId.clear()
        shops.forEach(::put)
    }
}
