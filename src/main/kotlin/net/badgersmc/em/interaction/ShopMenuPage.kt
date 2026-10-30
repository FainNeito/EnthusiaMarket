package net.badgersmc.em.interaction

/** One render snapshot. Buttons must capture an immutable ID, never a refreshed row offset. */
internal data class ShopMenuPage<T>(val items: List<T>, val index: Int, val pages: Int, val count: Int) {
    companion object {
        private const val SIZE = 45

        fun <T> of(rows: List<T>, requested: Int, id: (T) -> Long): ShopMenuPage<T> {
            val ordered = rows.sortedBy(id)
            val pages = if (ordered.isEmpty()) 1 else (ordered.size - 1) / SIZE + 1
            val index = requested.coerceIn(0, pages - 1)
            return ShopMenuPage(ordered.drop(index * SIZE).take(SIZE), index, pages, ordered.size)
        }
    }
}
