package net.badgersmc.em.interaction.bedrock

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.impl.FormDefinitions
import org.geysermc.cumulus.form.impl.custom.CustomFormDefinition
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercise Cumulus decoding and its actual submit callback, not only menu construction. */
class BedrockEditorSubmissionTest {
    private val owner = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) {
        every { uniqueId } returns owner
        every { hasPermission(any<String>()) } returns false
    }
    private val repository = mockk<ShopRepository>(relaxed = true)
    private val lang = mockk<LangService>(relaxed = true) {
        every { legacy(any(), *anyVararg()) } answers { firstArg() }
    }
    private var current = Shop(1, "stall1", owner, "world", 1, 64, 3,
        "world", 4, 64, 6, "diamond", 8, "money", 100, stockCount = 256)

    private fun form(): CustomForm {
        every { repository.findById(1) } answers { current }
        every { repository.upsert(any()) } answers { current = firstArg(); current }
        return BedrockShopEditForm(player, current, repository, mockk<Logger>(relaxed = true),
            lang, ShopManagementService(repository)).buildForm()
    }

    private fun submit(form: CustomForm, json: String) {
        val definition: CustomFormDefinition = FormDefinitions.instance().definitionFor(form)
        definition.handleFormResponse(form, json)
    }

    @Test fun `valid form submits through authorized service and preserves stock`() {
        submit(form(), "[null,true,false,true,\"9\",\"110\"]")
        assertEquals(9, current.sellAmount)
        assertEquals(110, current.costAmount)
        assertEquals(256, current.stockCount)
        assertEquals(true, current.frozen)
        assertEquals(false, current.hopperAllowIn)
        verify(exactly = 1) { repository.upsert(any()) }
    }

    @Test fun `ownership revoked after opening prevents submit`() {
        val form = form()
        current = current.copy(owner = UUID.randomUUID())
        submit(form, "[null,true,false,true,\"9\",\"110\"]")
        verify(exactly = 0) { repository.upsert(any()) }
        assertEquals(8, current.sellAmount)
    }

    @Test fun `invalid quantity does not persist settings`() {
        submit(form(), "[null,true,false,true,\"0\",\"110\"]")
        verify(exactly = 0) { repository.upsert(any()) }
        assertEquals(false, current.frozen)
    }

    @Test fun `closed form does not save`() {
        submit(form(), "null")
        verify(exactly = 0) { repository.upsert(any()) }
    }
}
