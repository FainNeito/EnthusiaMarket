package net.badgersmc.em.domain.shop

/** Epoch milliseconds: inclusive start, exclusive end. Filtering precedes pagination. */
data class ShopHistoryWindow(val fromMs: Long, val toMs: Long) {
    init { require(fromMs < toMs) { "History window must be nonempty" } }
}
