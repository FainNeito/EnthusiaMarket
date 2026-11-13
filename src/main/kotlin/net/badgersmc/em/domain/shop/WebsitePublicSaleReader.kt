package net.badgersmc.em.domain.shop

import java.util.UUID

/** Public fields only; buyer and seller identities never enter this projection. */
data class WebsitePublicSale(
    val id: Long, val stall: String, val guild: String, val item: String,
    val quantity: Int, val gold: Long, val occurredAt: Long, val recordingId: UUID, val awardOwner: SaleAwardOwner? = null,
)
data class WebsitePublicSalePage(val items: List<WebsitePublicSale>, val nextCursor: Long?, val checkpointCursor: Long)
fun interface WebsitePublicSaleReader { fun readAfter(after: Long): WebsitePublicSalePage }
