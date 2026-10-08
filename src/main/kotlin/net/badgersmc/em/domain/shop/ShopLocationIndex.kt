package net.badgersmc.em.domain.shop

/**
 * Authoritative in-memory index of which container coordinates host shops (REQ-282).
 *
 * The hopper-control hot path (REQ-281) resolves shop status from this index instead of
 * querying the database on the server thread. Implementations keep it in lockstep with the
 * persisted set; a missed update would let a hopper bypass a locked shop, so consistency is
 * the correctness contract, not an optimization.
 */
interface ShopLocationIndex {

    /** Shops whose container block is at ([world], [x], [y], [z]); empty when none. */
    fun shopsAt(world: String, x: Int, y: Int, z: Int): List<Shop>

    /** Current shop at a sign coordinate; no database fallback. */
    fun shopAtSign(world: String, x: Int, y: Int, z: Int): Shop? =
        throw UnsupportedOperationException("Sign indexing is not supported by this implementation")

    /** Reconcile a successful persisted stock update without another SQL read. */
    fun updateStock(id: Long, stockCount: Int) {
        throw UnsupportedOperationException("Stock reconciliation is not supported by this implementation")
    }

    /** Reconcile a successful bulk freeze without a follow-up database query. */
    fun freezeByStall(stallId: String, frozen: Boolean) {
        throw UnsupportedOperationException("Freeze reconciliation is not supported by this implementation")
    }

    /** Index (or re-index) [shop] under its container coordinate. */
    fun put(shop: Shop)

    /** Drop [shop] from its container coordinate. */
    fun remove(shop: Shop)

    /** Replace the entire index contents with [shops] (startup rebuild from persistence). */
    fun rebuild(shops: Collection<Shop>)
}
