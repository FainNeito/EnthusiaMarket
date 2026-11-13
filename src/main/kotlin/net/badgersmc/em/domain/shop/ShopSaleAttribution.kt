package net.badgersmc.em.domain.shop

import java.util.UUID

/** Immutable sale-time identifiers; absent for legacy records and unknown sources. */
data class ShopSaleAttribution(
    val stallId: String?, val guildId: UUID?, val publicCashEligible: Boolean = false,
    val awardOwner: SaleAwardOwner? = null,
)

data class SaleAwardOwner(val id: UUID, val name: String, val representative: UUID) {
    init {
        require(name.isNotBlank() && name.length <= 48 && name.none(Char::isISOControl))
    }
}
