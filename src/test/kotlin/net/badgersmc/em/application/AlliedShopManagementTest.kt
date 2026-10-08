package net.badgersmc.em.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.StallAccessPolicy
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.stall.*
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AlliedShopManagementTest {
    @Test fun `allied stock or price grants cannot delete delegate trust or change shop controls`() {
        val actor = UUID.randomUUID()
        val guild = UUID.randomUUID().toString()
        val stall = Stall(StallId("s"), "s", "world", StallState.OWNED, OwnerRef.guild(guild),
            Instant.now(), 100, RentTerms.formula(1.0))
        val shop = Shop(1, "s", UUID.randomUUID(), "world", 1, 2, 3, "world", 1, 2, 3, "item", 1, "cost", 10)
        val repo = mockk<ShopRepository>(relaxed = true)
        val stalls = mockk<StallRepository>()
        val guilds = mockk<GuildProvider>(relaxed = true)
        val policy = mockk<StallAccessPolicy>()
        every { repo.all() } returns listOf(shop)
        every { repo.findById(1) } returns shop
        every { stalls.findById(StallId("s")) } returns stall
        every { policy.blacklistDenies("s", actor) } returns false
        every { policy.allows("s", actor, any()) } returns true
        every { policy.alliedAllows("s", actor, any()) } returns true
        val service = ShopManagementService(repo, ShopAccessPolicy(stalls, guilds, policy))
        assertTrue(service.saveEdits(actor, shop.copy(sellAmount = 2)))
        assertTrue(service.saveEdits(actor, shop.copy(costAmount = 20)))
        assertFalse(service.saveEdits(actor, shop.copy(frozen = true)))
        assertFalse(service.delete(actor, 1))
        assertEquals(0, service.trustAll(actor, UUID.randomUUID()))
        verify(exactly = 0) { repo.delete(any()) }
        verify(exactly = 2) { repo.upsert(any()) }
    }
}
