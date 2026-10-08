package net.badgersmc.em.application

import java.util.Locale

/** Explicit selectors avoid collisions between category and material names. */
data class MarketSearchQuery(val term: String, val mode: Mode) {
    enum class Mode { AUTO, ITEM, CATEGORY }
    val category: SearchCategory? = SearchCategory.resolve(term)
    val tickerItem: String? get() = when (mode) {
        Mode.ITEM -> term
        Mode.CATEGORY -> null
        Mode.AUTO -> term.takeIf { category == null }
    }

    companion object {
        fun parse(raw: String): MarketSearchQuery? {
            val normalized = raw.trim().uppercase(Locale.ROOT).replace(' ', '_')
            val prefix = normalized.substringBefore(':', "")
            val mode = MODES[prefix] ?: return null
            val term = normalized.substringAfter(':', normalized).removePrefix("MINECRAFT:")
            if (term.length < MIN_LENGTH || ':' in term) return null
            val query = MarketSearchQuery(term, mode)
            return query.takeUnless { mode == Mode.CATEGORY && it.category == null }
        }

        private const val MIN_LENGTH = 2
        private val MODES = mapOf("" to Mode.AUTO, "ITEM" to Mode.ITEM,
            "CATEGORY" to Mode.CATEGORY, "MINECRAFT" to Mode.ITEM)
    }
}
