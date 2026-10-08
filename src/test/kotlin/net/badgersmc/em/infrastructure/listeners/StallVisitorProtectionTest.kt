package net.badgersmc.em.infrastructure.listeners

import io.mockk.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Lectern
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTakeLecternBookEvent
import kotlin.test.Test

class StallVisitorProtectionTest {
    private val listener = object : StallVisitorProtectionListener(mockk(), mockk()) {
        override fun mayModify(player: Player, location: Location) = false
    }

    @Test fun `visitor reads a copy without accessing lectern inventory`() {
        val event = mockk<PlayerInteractEvent>(relaxed = true)
        val lectern = mockk<Lectern>(relaxed = true)
        val inventory = mockk<org.bukkit.inventory.LecternInventory>(relaxed = true)
        every { lectern.inventory } returns inventory
        val book = mockk<ItemStack>()
        val copy = mockk<ItemStack>()
        every { event.action } returns Action.RIGHT_CLICK_BLOCK
        every { event.hand } returns EquipmentSlot.HAND
        every { event.clickedBlock?.state } returns lectern
        every { inventory.getItem(0) } returns book
        every { book.type } returns Material.WRITTEN_BOOK
        every { book.clone() } returns copy
        listener.onReadLectern(event)
        verify { event.player.openBook(copy) }
        verify { event.setUseInteractedBlock(Event.Result.DENY) }
        verify(exactly = 0) { inventory.setItem(any(), any()) }
    }

    @Test fun `visitor cannot take lectern book`() {
        val event = mockk<PlayerTakeLecternBookEvent>(relaxed = true)
        listener.onTakeBook(event)
        verify { event.isCancelled = true }
    }
}
