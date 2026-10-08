package net.badgersmc.em.infrastructure.listeners

import io.mockk.*
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.shop.Shop
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

internal class StallStockContributorListenerTest {
    @AfterEach fun close() { unmockkAll(); if (MockBukkit.isMocked()) MockBukkit.unmock() }

    @Test fun provableManualAddition() {
        val fixture = Fixture()
        fixture.clickAndTick(3)
        verify { fixture.accounting.stock(fixture.shop, 0, 3, fixture.player.uniqueId) }
    }

    @Test fun unobservedMovementInvalidatesContributor() {
        val fixture = Fixture()
        fixture.contents[0] = ItemStack(Material.DIAMOND, 3)
        fixture.clickAndTick(7)
        verify { fixture.accounting.stock(fixture.shop, 3, 7, null) }
    }

    @Test fun sharedPhysicalStockNeverCreditsTwoShops() {
        val fixture = Fixture()
        fixture.candidates = listOf(fixture.shop, fixture.shop.copy(id = 2))
        fixture.clickAndTick(3)
        verify { fixture.accounting.stock(fixture.shop, 0, 3, null) }
        verify { fixture.accounting.stock(fixture.shop.copy(id = 2), 0, 3, null) }
    }

    private class Fixture {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val player = server.addPlayer()
        val world = server.addSimpleWorld("world")
        val inventory = mockk<Inventory>(relaxed = true)
        val contents = arrayOfNulls<ItemStack>(27)
        val shop = Shop(1, "stall", player.uniqueId, "world", 0, 0, 0, "world", 1, 2, 3,
            ItemStackSerializer.serialize(ItemStack(Material.DIAMOND)), 1, "cost", 5)
        val stock = mockk<ContainerStockListener>()
        var candidates = listOf(shop)
        val accounting = mockk<GuildStockAccounting>(relaxed = true)
        val listener = StallStockContributorListener(stock, accounting, plugin)
        init {
            every { inventory.location } returns Location(world, 1.0, 2.0, 3.0)
            every { inventory.storageContents } answers { contents }
            every { inventory.size } returns 27
            every { stock.accountingCandidates() } answers { candidates }
        }

        fun clickAndTick(after: Int) {
            val event = mockk<InventoryClickEvent>(relaxed = true)
            every { event.inventory } returns inventory
            every { event.clickedInventory } returns inventory
            every { event.whoClicked } returns player
            every { event.action } returns InventoryAction.PLACE_ALL
            every { event.cursor } returns ItemStack(Material.DIAMOND, 3)
            every { event.currentItem } returns null
            listener.onClick(event)
            contents[0] = ItemStack(Material.DIAMOND, after)
            server.scheduler.performOneTick()
        }
    }
}
