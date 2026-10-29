package net.badgersmc.em.interaction.bedrock

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.impl.simple.SimpleFormImpl
import org.geysermc.cumulus.response.SimpleFormResponse
import org.geysermc.cumulus.response.result.ValidFormResponseResult
import org.geysermc.floodgate.api.FloodgateApi
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BedrockOwnedShopsPaginationTest {
    private val actor = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true)
    private val repository = mockk<ShopRepository>()
    private val lang = mockk<LangService>()
    private val floodgate = mockk<FloodgateApi>(relaxed = true)
    private val rows = linkedMapOf<Long, Shop>()
    private var permitted = true
    private var sent: Form? = null
    private lateinit var menu: BedrockOwnedShopsForm

    @BeforeTest fun setup() {
        every { player.uniqueId } returns actor
        every { player.hasPermission("enthusiamarket.shop.use") } answers { permitted }
        every { lang.legacy(any(), *anyVararg()) } answers {
            firstArg<String>() + " " + secondArg<Array<Pair<String, Any>>>().joinToString()
        }
        every { repository.findByOwner(any()) } answers { rows.values.filter { it.owner == actor }.reversed() }
        every { repository.findById(any()) } answers { rows[firstArg<Long>()] }
        mockkStatic(FloodgateApi::class)
        every { FloodgateApi.getInstance() } returns floodgate
        every { floodgate.sendForm(actor, any<Form>()) } answers { sent = secondArg(); true }
        menu = BedrockOwnedShopsForm(player, repository, ShopManagementService(repository),
            Logger.getAnonymousLogger(), lang)
    }

    @AfterTest fun cleanup() { unmockkStatic(FloodgateApi::class) }

    @Test fun `Bedrock boundary pages use stable IDs and correct navigation indexes`() {
        for (count in listOf(0, 45, 46, 90, 91)) {
            seed(count)
            menu = BedrockOwnedShopsForm(player, repository, ShopManagementService(repository),
                Logger.getAnonymousLogger(), lang)
            var form = menu.buildForm()
            val pages = maxOf(1, (count + 44) / 45)
            val ids = mutableListOf<Int>()
            repeat(pages) { index ->
                assertTrue(form.content().contains("(page, ${index + 1})"))
                assertTrue(form.content().contains("(pages, $pages)"))
                ids.addAll(form.buttons().mapNotNull { button ->
                    button?.text()?.substringBefore(":")?.removePrefix("stall")?.toIntOrNull()
                })
                val next = form.buttons().indexOfFirst { it?.text()?.startsWith("gui.shop.search.next") == true }
                assertEquals(index + 1 < pages, next >= 0)
                if (next >= 0) {
                    respond(form, next)
                    form = sent as SimpleForm
                }
            }
            assertEquals((1..count).toList(), ids)
        }
    }

    @Test fun `Bedrock refresh clamps removed final page and previous returns first page`() {
        seed(91)
        var form = menu.buildForm()
        respond(form, 45)
        form = sent as SimpleForm
        respond(form, 46) // second-page next follows previous
        form = sent as SimpleForm
        rows.remove(91L)
        respond(form, 1) // third-page previous, refresh to page 2 of 2
        form = sent as SimpleForm
        assertTrue(form.content().contains("(page, 2)"))
        assertTrue(form.content().contains("(pages, 2)"))
        respond(form, 45)
        assertTrue((sent as SimpleForm).content().contains("(page, 1)"))
    }

    @Test fun `Bedrock stale shop uses snapshot ID even after row order changes`() {
        seed(46)
        respond(menu.buildForm(), 45)
        val form = sent as SimpleForm
        rows.remove(1L)
        rows.remove(46L)
        sent = null
        respond(form, 0)
        verify(exactly = 1) { repository.findById(46L) }
        assertTrue((sent as SimpleForm).content().contains("(page, 1)"))
    }

    @Test fun `Bedrock callbacks reject revoked use and transferred ownership`() {
        seed(46)
        val form = menu.buildForm()
        permitted = false
        respond(form, 0)
        respond(form, 45)
        verify(exactly = 0) { repository.findById(any()) }
        assertEquals(null, sent)
        permitted = true
        rows[1L] = rows.getValue(1L).copy(owner = UUID.randomUUID())
        respond(form, 0)
        assertEquals(45, (sent as SimpleForm).buttons().size)
    }

    @Test fun `deleted final captured entry refreshes and clamps without opening edit`() {
        seed(91)
        respond(menu.buildForm(), 45)
        respond(sent as SimpleForm, 46)
        val finalPage = sent as SimpleForm
        rows.remove(91L)
        respond(finalPage, 0)
        assertTrue((sent as SimpleForm).content().contains("(page, 2)"))
        assertTrue((sent as SimpleForm).content().contains("(pages, 2)"))
    }

    private fun respond(form: SimpleForm, index: Int) {
        val response = mockk<SimpleFormResponse>()
        every { response.clickedButtonId() } returns index
        (form as SimpleFormImpl).callResultHandler(ValidFormResponseResult.of(response))
    }

    private fun seed(count: Int) {
        rows.clear()
        sent = null
        for (id in 1..count) rows[id.toLong()] = Shop(id.toLong(), "stall$id", actor,
            "world", id, 65, 1, "world", id, 64, 1, "item", 1, "money", 10)
    }
}
