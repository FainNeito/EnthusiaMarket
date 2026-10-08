package net.badgersmc.em.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.domain.ports.EconomyProvider
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.StallAccessPolicy
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.stall.StallCapability
import net.badgersmc.em.domain.stall.StallRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertIs

class StallTradeAccessTest {
    @Test fun `all monetary and barter directions deny before persistence money or inventory access`() {
        val stalls = mockk<StallRepository>()
        val economy = mockk<EconomyProvider>()
        val guilds = mockk<GuildProvider>()
        val access = mockk<StallAccessPolicy>()
        val actor = UUID.randomUUID()
        every { access.allows("s", actor, StallCapability.TRADE) } returns false
        val shop = Shop(1, "s", UUID.randomUUID(), "world", 1, 2, 3, "world", 1, 2, 3,
            "unused", 1, "unused", 10)
        val service = ContainerTradeService(stalls, economy, guilds, stallAccess = access)
        assertIs<ContainerTradeResult.Failure>(service.executeBuyBatch(shop, actor, 1))
        assertIs<ContainerTradeResult.Failure>(service.executeSellBatch(shop, actor, 1))
        assertIs<ContainerTradeResult.Failure>(service.executeTrade(shop, actor))
        verify(exactly = 0) { stalls.findById(any()) }
        verify(exactly = 0) { economy.withdraw(any(), any()) }
        verify(exactly = 0) { economy.deposit(any(), any()) }
    }
}
