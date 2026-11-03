package net.badgersmc.em.domain.shop

/** Facts must be captured at sale time; unknown facts never qualify a public sale. */
data class WebsiteSaleFacts(
    val direction: SignDirection,
    val amount: Long,
    val quantity: Int,
    val stallId: String?,
    val cashCurrencyVerified: Boolean,
    val adminShop: Boolean?,
    val ownShop: Boolean?,
    val modifiedItem: Boolean?,
    val guildOwned: Boolean,
    val guildMemberBuyer: Boolean?,
)

object WebsiteSaleEligibility {
    fun accepts(facts: WebsiteSaleFacts): Boolean =
        facts.direction == SignDirection.SELL && facts.amount in 1..1_000_000_000_000L && facts.quantity in 1..1_000_000_000 &&
            facts.stallId?.matches(Regex("[A-Za-z0-9_.:-]{1,80}")) == true && facts.cashCurrencyVerified &&
            facts.adminShop == false && facts.ownShop == false && facts.modifiedItem == false &&
            (!facts.guildOwned || facts.guildMemberBuyer == false)
}
