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

    @Test fun manualAdditionAndInterference() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val player = server.addPlayer()
        val world = server.addSimpleWorld("world")
        val inventory = mockk<Inventory>(relaxed = true)
        every { inventory.location } returns Location(world, 1.0, 2.0, 3.0)
        var contents = arrayOfNulls<ItemStack>(27)
        every { inventory.storageContents } answers { contents }
        every { inventory.size } returns 27
        val shop = Shop(1, "stall", player.uniqueId, "world", 0, 0, 0, "world", 1, 2, 3,
            ItemStackSerializer.serialize(ItemStack(Material.DIAMOND)), 1, "cost", 5)
        val stock = mockk<ContainerStockListener>()
        var candidates = listOf(shop)
        every { stock.accountingCandidates() } answers { candidates }
        val accounting = mockk<GuildStockAccounting>(relaxed = true)
        val listener = StallStockContributorListener(stock, accounting, plugin)
        fun click() {
            val event = mockk<InventoryClickEvent>(relaxed = true)
            every { event.inventory } returns inventory
            every { event.clickedInventory } returns inventory
            every { event.whoClicked } returns player
            every { event.action } returns InventoryAction.PLACE_ALL
            every { event.cursor } returns ItemStack(Material.DIAMOND, 3)
            every { event.currentItem } returns null
            listener.onClick(event)
        }
        click(); contents[0] = ItemStack(Material.DIAMOND, 3)
        server.scheduler.performOneTick()
        verify { accounting.stock(shop, 0, 3, player.uniqueId) }
        // Unobserved movement makes the actual delta disagree with the click.
        click(); contents[0] = ItemStack(Material.DIAMOND, 7)
        server.scheduler.performOneTick()
        verify { accounting.stock(shop, 3, 7, null) }
        // Two shops sharing this physical stock pool must not both claim a contributor.
        candidates = listOf(shop, shop.copy(id = 2))
        click(); contents[0] = ItemStack(Material.DIAMOND, 10)
        server.scheduler.performOneTick()
        verify { accounting.stock(shop, 7, 10, null) }
        verify { accounting.stock(shop.copy(id = 2), 7, 10, null) }
    }
}
