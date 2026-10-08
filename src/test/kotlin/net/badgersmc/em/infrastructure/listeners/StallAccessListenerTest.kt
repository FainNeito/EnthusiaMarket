package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.application.StallAccessSettingsService
import net.badgersmc.em.domain.stall.*
import org.bukkit.Location
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

class StallAccessListenerTest {
    private val actor = UUID.randomUUID()
    private val player = mockk<Player>()
    private val regions = mockk<StallAccessRegions>()
    private val access = mockk<StallAccessSettingsService>()
    private val listener = StallAccessListener(regions, access)
    private val stall = Stall(StallId("s"), "s", "world", StallState.OWNED,
        OwnerRef.solo(UUID.randomUUID()), Instant.now(), 100, RentTerms.formula(1.0))
    private val location = mockk<Location>()
    private fun inventory(): Inventory {
        every { player.uniqueId } returns actor
        every { player.hasPermission(any<String>()) } returns false
        every { regions.at(location) } returns listOf(stall)
        val holder = mockk<Container>()
        every { holder.location } returns location
        return mockk<Inventory> {
            every { this@mockk.holder } returns holder
            every { this@mockk.location } returns this@StallAccessListenerTest.location
            every { type } returns InventoryType.CHEST
        }
    }

    @Test fun `chest access does not grant stocking and revocation applies to an open chest`() {
        val inventory = inventory()
        every { access.allows("s", actor, any()) } returns true
        every { access.allows("s", actor, StallCapability.STOCK) } returns false
        val open = mockk<InventoryOpenEvent>(relaxed = true)
        every { open.player } returns player
        every { open.inventory } returns inventory
        listener.onOpen(open)
        verify(exactly = 0) { open.isCancelled = true }
        val click = mockk<InventoryClickEvent>(relaxed = true)
        every { click.whoClicked } returns player
        every { click.view.topInventory } returns inventory
        listener.onInventoryClick(click)
        verify { click.isCancelled = true }
        every { access.allows("s", actor, any()) } returns false
        listener.onOpen(open)
        verify { open.isCancelled = true }
    }

    @Test fun `both halves of a double chest must grant access`() {
        val inventory = inventory()
        val other = mockk<Location>()
        val second = stall.copy(id = StallId("other"))
        val left = mockk<Container> { every { location } returns this@StallAccessListenerTest.location }
        val right = mockk<Container> { every { location } returns other }
        val double = mockk<org.bukkit.block.DoubleChest> {
            every { leftSide } returns left
            every { rightSide } returns right
        }
        every { inventory.holder } returns double
        every { regions.at(other) } returns listOf(second)
        every { access.allows("s", actor, any()) } returns true
        every { access.allows("other", actor, any()) } returns false
        val open = mockk<InventoryOpenEvent>(relaxed = true)
        every { open.player } returns player
        every { open.inventory } returns inventory
        listener.onOpen(open)
        verify { open.isCancelled = true }
    }

    @Test fun `blocked invisibility cannot pick up stock before the suppression tick`() {
        inventory()
        every { access.allows("s", actor, any()) } returns true
        every { access.current(stall) } returns StallAccessSettings.defaults(stall)
        val effect = mockk<org.bukkit.potion.PotionEffect>()
        every { effect.type.name } returns "INVISIBILITY"
        every { player.activePotionEffects } returns listOf(effect)
        val pickup = mockk<org.bukkit.event.entity.EntityPickupItemEvent>(relaxed = true)
        every { pickup.entity } returns player
        every { pickup.item.location } returns location
        listener.onPickup(pickup)
        verify { pickup.isCancelled = true }
    }
}
