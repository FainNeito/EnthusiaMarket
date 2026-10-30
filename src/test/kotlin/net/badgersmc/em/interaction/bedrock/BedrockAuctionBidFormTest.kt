package net.badgersmc.em.interaction.bedrock

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.em.interaction.AuctionBidUi
import net.badgersmc.em.interaction.MenuFactory
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.scheduler.NexusScheduler
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.impl.simple.SimpleFormImpl
import org.geysermc.cumulus.response.SimpleFormResponse
import org.geysermc.cumulus.response.result.ValidFormResponseResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BedrockAuctionBidFormTest {
    private val queued = mutableListOf<() -> Unit>()
    private val player = mockk<Player>(relaxed = true)
    private var clicks = 0

    private fun form(): SimpleFormImpl {
        val scheduler = mockk<NexusScheduler>()
        every { scheduler.runOnMain(any()) } answers { queued.add(firstArg()); AutoCloseable { } }
        AuctionBidUi.configure(scheduler, mockk<MenuFactory>())
        every { player.isOnline } returns true
        return BedrockAuctionBidForm(player, "Review your bid", "Personal\nFULL bid: 100",
            listOf("Confirm full bid" to { clicks++ }, "Back — do not bid" to { }),
            mockk<LangService>()).buildForm() as SimpleFormImpl
    }

    @AfterTest fun cleanup() { AuctionBidUi.reset() }

    @Test fun `form preserves review content and defers selected action to main`() {
        val form = form()
        assertEquals("Personal\nFULL bid: 100", form.content())
        respond(form, 0)
        assertEquals(0, clicks)
        queued.single().invoke()
        assertEquals(1, clicks)
    }

    @Test fun `offline actor cannot run a queued form action`() {
        val form = form()
        respond(form, 0)
        every { player.isOnline } returns false
        queued.single().invoke()
        assertEquals(0, clicks)
    }

    @Test fun `invalid button cannot dispatch an action`() {
        respond(form(), 99)
        assertEquals(0, queued.size)
        assertEquals(0, clicks)
    }

    private fun respond(form: SimpleFormImpl, index: Int) {
        val response = mockk<SimpleFormResponse>()
        every { response.clickedButtonId() } returns index
        form.callResultHandler(ValidFormResponseResult.of(response))
    }
}
