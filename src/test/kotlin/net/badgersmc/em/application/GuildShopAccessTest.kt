package net.badgersmc.em.application

import io.mockk.*
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.GuildProvider.GuildPermission
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.stall.*
import java.util.UUID
import kotlin.test.*

class GuildShopAccessTest {
    private val actor = UUID.randomUUID()
    private val guildId = UUID.randomUUID().toString()
    private val stalls = mockk<StallRepository>()
    private val guilds = mockk<GuildProvider>()
    private val shops = mockk<ShopRepository>(relaxed = true)
    private val stall = Stall(StallId("g1"), "g1", "world", StallState.OWNED, OwnerRef.guild(guildId), null, 100, RentTerms.formula(1.0))
    private val shop = Shop(1, "g1", actor, "world", 0, 64, 0, "world", 0, 64, 1, "item", 1, "cost", 10)
    private val policy = ShopAccessPolicy(stalls, guilds)
    private val management = ShopManagementService(shops, policy)

    @BeforeTest fun setup() {
        every { stalls.findById(stall.id) } returns stall
        every { guilds.isMember(actor, guildId) } returns true
        every { guilds.hasShopPermission(actor, guildId, any()) } returns false
        every { shops.findById(shop.id) } returns shop
    }

    @Test fun `creator cannot bypass guild rank`() {
        assertFalse(management.canEdit(shop, actor))
        assertFalse(management.delete(actor, shop.id))
        verify(exactly = 0) { shops.delete(any()) }
    }

    @Test fun `guild manager can delete another members shop`() {
        every { guilds.hasShopPermission(actor, guildId, GuildPermission.EDIT_SHOP_STOCK) } returns true
        val other = shop.copy(owner = UUID.randomUUID())
        every { shops.findById(shop.id) } returns other
        assertTrue(management.delete(actor, shop.id))
        verify { shops.delete(shop.id) }
    }

    @Test fun `price permission cannot change stock or hopper settings`() {
        every { guilds.hasShopPermission(actor, guildId, GuildPermission.MODIFY_SHOP_PRICES) } returns true
        assertTrue(management.saveEdits(actor, shop.copy(costAmount = 20)))
        assertFalse(management.saveEdits(actor, shop.copy(sellAmount = 2)))
        assertFalse(management.saveEdits(actor, shop.copy(hopperAllowOut = false)))
    }

    @Test fun `stock permission cannot change price`() {
        every { guilds.hasShopPermission(actor, guildId, GuildPermission.EDIT_SHOP_STOCK) } returns true
        assertTrue(management.saveEdits(actor, shop.copy(sellAmount = 2)))
        assertFalse(management.saveEdits(actor, shop.copy(costAmount = 20)))
    }

    @Test fun `leaving guild invalidates already open editor`() {
        every { guilds.hasShopPermission(actor, guildId, any()) } returns true
        assertTrue(management.canEdit(shop, actor))
        every { guilds.isMember(actor, guildId) } returns false
        assertFalse(management.saveEdits(actor, shop.copy(costAmount = 20)))
        verify(exactly = 0) { shops.upsert(any()) }
    }

    @Test fun `empty roster removes stale projection`() {
        every { stalls.all() } returns listOf(stall)
        every { guilds.memberIds(guildId) } returns emptySet()
        val regions = mockk<RegionMemberSync>(relaxed = true)
        GuildStallAccessSync(stalls, guilds, regions).refresh(guildId)
        verify { regions.syncGuildMembers("world", "g1", emptySet()) }
    }
}
