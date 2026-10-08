package net.badgersmc.em.application

import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopLocationIndex
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.ports.MarketMutationGate
import java.util.UUID

/**
 * [ShopRepository] decorator (REQ-281/282) that keeps a [ShopLocationIndex] in lockstep with the
 * persisted state and serves [findByContainer] and [findBySign] from memory. Application layer:
 * the single choke point through which every shop mutation flows (decision: docs/tasks.md PERF-2).
 *
 * Other reads pass through. Mutations persist first, then reconcile the index; stock and bulk freeze
 * updates reconcile without a second SQL read. Live moderation locks overlay either coordinate result.
 */
@Suppress("TooManyFunctions")
class IndexedShopRepository(
    private val delegate: ShopRepository,
    private val index: ShopLocationIndex,
    private val moderationGate: MarketMutationGate = MarketMutationGate.Open,
) : ShopRepository {

    override fun upsert(shop: Shop): Shop {
        val persisted = delegate.upsert(shop)
        index.put(persisted)
        return persisted
    }

    override fun delete(id: Long) {
        val shop = delegate.findById(id)
        delegate.delete(id)
        shop?.let { index.remove(it) }
    }

    override fun deleteByContainer(world: String, x: Int, y: Int, z: Int) {
        delegate.deleteByContainer(world, x, y, z)
        index.shopsAt(world, x, y, z).forEach { index.remove(it) }
    }

    override fun deleteByOwner(owner: UUID): Int {
        val owned = delegate.findByOwner(owner)
        val removed = delegate.deleteByOwner(owner)
        owned.forEach { index.remove(it) }
        return removed
    }

    override fun updateStock(id: Long, stockCount: Int) {
        delegate.updateStock(id, stockCount)
        index.updateStock(id, stockCount)
    }

    override fun updateStockBatch(batch: Map<Long, Int>) {
        delegate.updateStockBatch(batch)
        batch.forEach { (id, stock) -> index.updateStock(id, stock) }
    }

    override fun freezeByStall(stallId: String, frozen: Boolean) {
        delegate.freezeByStall(stallId, frozen)
        index.freezeByStall(stallId, frozen)
    }

    override fun findByContainer(world: String, x: Int, y: Int, z: Int): List<Shop> =
        index.shopsAt(world, x, y, z).map { shop ->
            if (moderationGate.isStallLocked(shop.stallId)) shop.copy(frozen = true) else shop
        }

    // --- pass-through reads ---
    override fun findById(id: Long): Shop? = delegate.findById(id)
    override fun findBySign(world: String, x: Int, y: Int, z: Int): Shop? =
        index.shopAtSign(world, x, y, z)?.let { shop ->
            if (moderationGate.isStallLocked(shop.stallId)) shop.copy(frozen = true) else shop
        }
    override fun findByStall(stallId: String): List<Shop> = delegate.findByStall(stallId)
    override fun findByOwner(owner: UUID): List<Shop> = delegate.findByOwner(owner)
    override fun all(): List<Shop> = delegate.all()
    override fun countAll(): Int = delegate.countAll()
    override fun countByOwner(owner: UUID): Int = delegate.countByOwner(owner)
    override fun findBySellMaterial(material: String): List<Shop> = delegate.findBySellMaterial(material)
    override fun findBySellMaterialPrefix(prefix: String): List<Shop> = delegate.findBySellMaterialPrefix(prefix)
    override fun backfillSellMaterials(): Int = delegate.backfillSellMaterials()
}
