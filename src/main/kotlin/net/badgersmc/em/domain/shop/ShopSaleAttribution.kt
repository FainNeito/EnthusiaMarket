package net.badgersmc.em.domain.shop

import java.util.UUID

/** Immutable sale-time identifiers; absent for legacy records and unknown sources. */
data class ShopSaleAttribution(val stallId: String?, val guildId: UUID?, val publicCashEligible: Boolean = false)
