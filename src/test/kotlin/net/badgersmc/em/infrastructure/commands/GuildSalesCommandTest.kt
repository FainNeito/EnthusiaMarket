package net.badgersmc.em.infrastructure.commands

import io.mockk.*
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.shop.StallAccountingRepository
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import kotlin.test.*

internal class GuildSalesCommandTest {
    @Test fun registeredPathsAndCsvEscaping() {
        WebsiteSyncSecretArgumentRegistration.register()
        val command = PaperCommandScanner().scanCommands("net.badgersmc.em.infrastructure.commands", javaClass.classLoader)
            .single { it.annotation.name == "guildsales" }
        assertEquals(setOf("", "export", "range", "export range"), command.subcommands.map { it.path.joinToString(" ") }.toSet())
        val service = GuildSalesCommand(mockk(), mockk(), mockk(), mockk(), mockk())
        assertEquals("\"'=SUM(A1)\"", service.csvCell("=SUM(A1)"))
        assertEquals("\"diamond, \"\"named\"\"\"", service.csvCell("diamond, \"named\""))
    }

    @Test fun missingStallCannotQueryOrExport() {
        val repository = mockk<StallAccountingRepository>()
        val stalls = mockk<StallRepository>()
        every { stalls.findById(any()) } returns null
        val lang = denialLanguage()
        val player = mockk<Player>(relaxed = true)
        val service = GuildSalesCommand(repository, stalls, mockk<GuildProvider>(), lang, mockk<Plugin>())
        service.report(player, "missing")
        service.exportReport(player, "missing")
        verify(exactly = 0) { repository.report(any(), any(), any()) }
        verify(exactly = 2) { player.sendMessage(Component.text("Denied")) }
    }

    @Test fun currentMembershipAndShopAuthorityAreRequired() {
        val repository = mockk<StallAccountingRepository>()
        val stalls = mockk<StallRepository>()
        val guild = java.util.UUID.randomUUID().toString()
        val stall = activeStall(guild)
        every { stalls.findById(any()) } returns stall
        val provider = mockk<GuildProvider>()
        var member = false
        every { provider.isMember(any(), guild) } answers { member }
        every { provider.hasShopPermission(any(), guild, GuildProvider.GuildPermission.MANAGE_SHOPS) } returns false
        val lang = denialLanguage()
        val player = mockk<Player>(relaxed = true)
        val service = GuildSalesCommand(repository, stalls, provider, lang, mockk<Plugin>())
        service.report(player, "stall")
        member = true
        service.exportReport(player, "stall")
        verify(exactly = 0) { repository.report(any(), any(), any()) }
        verify(exactly = 2) { player.sendMessage(Component.text("Denied")) }
    }


    @Test fun invalidCalendarDatesAreRejectedBeforeQuery() {
        val repository = mockk<StallAccountingRepository>()
        val stalls = mockk<StallRepository>()
        every { stalls.findById(any()) } returns activeStall("guild")
        val provider = mockk<GuildProvider>(relaxed = true)
        every { provider.isMember(any(), any()) } returns true
        every { provider.hasShopPermission(any(), any(), any()) } returns true
        val lang = mockk<LangService>()
        every { lang.msg("accounting.usage") } returns Component.text("Usage")
        val player = mockk<Player>(relaxed = true)
        val service = GuildSalesCommand(repository, stalls, provider, lang, mockk<Plugin>())
        service.rangeReport(player, "stall", "2026-02-30", "2026-03-01")
        service.exportRange(player, "stall", "invalid", "2026-03-01")
        service.rangeReport(player, "stall", "2026-03-02", "2026-03-01")
        verify(exactly = 0) { repository.report(any(), any(), any()) }
        verify(exactly = 3) { player.sendMessage(Component.text("Usage")) }
    }

    private fun activeStall(guild: String): net.badgersmc.em.domain.stall.Stall = mockk {
        every { owner } returns net.badgersmc.em.domain.stall.OwnerRef(net.badgersmc.em.domain.stall.OwnerType.GUILD, guild)
        every { isActiveGuildStall() } returns true
    }

    private fun denialLanguage(): LangService = mockk {
        every { msg("accounting.denied") } returns Component.text("Denied")
    }

}
