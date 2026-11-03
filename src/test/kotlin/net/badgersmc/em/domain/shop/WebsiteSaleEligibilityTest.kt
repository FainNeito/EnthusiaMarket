package net.badgersmc.em.domain.shop

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebsiteSaleEligibilityTest {
    private val eligible = WebsiteSaleFacts(SignDirection.SELL, 20, 2, "stall1", true,
        adminShop = false, ownShop = false, modifiedItem = false, guildOwned = false, guildMemberBuyer = null)

    @Test fun `only verified completed player cash sales qualify`() {
        assertTrue(WebsiteSaleEligibility.accepts(eligible))
        listOf(
            eligible.copy(direction = SignDirection.BUY), eligible.copy(amount = 0), eligible.copy(quantity = 0),
            eligible.copy(stallId = null), eligible.copy(stallId = "bad stall"), eligible.copy(cashCurrencyVerified = false),
            eligible.copy(amount = 1_000_000_000_001L), eligible.copy(quantity = 1_000_000_001),
            eligible.copy(adminShop = true), eligible.copy(adminShop = null),
            eligible.copy(ownShop = true), eligible.copy(ownShop = null),
            eligible.copy(modifiedItem = true), eligible.copy(modifiedItem = null),
        ).forEach { assertFalse(WebsiteSaleEligibility.accepts(it)) }
    }

    @Test fun `guild sales require verified nonmember buyer`() {
        assertTrue(WebsiteSaleEligibility.accepts(eligible.copy(guildOwned = true, guildMemberBuyer = false)))
        assertFalse(WebsiteSaleEligibility.accepts(eligible.copy(guildOwned = true, guildMemberBuyer = true)))
        assertFalse(WebsiteSaleEligibility.accepts(eligible.copy(guildOwned = true, guildMemberBuyer = null)))
    }
}
