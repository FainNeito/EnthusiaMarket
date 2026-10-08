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
        assertEquals(setOf("", "export"), command.subcommands.map { it.path.joinToString(" ") }.toSet())
        val service = GuildSalesCommand(mockk(), mockk(), mockk(), mockk(), mockk())
        assertEquals("\"'=SUM(A1)\"", service.csvCell("=SUM(A1)"))
        assertEquals("\"diamond, \"\"named\"\"\"", service.csvCell("diamond, \"named\""))
    }

    @Test fun missingStallCannotQueryOrExport() {
        val repository = mockk<StallAccountingRepository>()
        val stalls = mockk<StallRepository>()
        every { stalls.findById(any()) } returns null
        val lang = mockk<LangService>()
        every { lang.msg("accounting.denied") } returns Component.text("Denied")
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
        val stall = mockk<net.badgersmc.em.domain.stall.Stall>()
        every { stall.owner } returns net.badgersmc.em.domain.stall.OwnerRef(net.badgersmc.em.domain.stall.OwnerType.GUILD, guild)
        every { stall.isActiveGuildStall() } returns true
        every { stalls.findById(any()) } returns stall
        val provider = mockk<GuildProvider>()
        var member = false
        every { provider.isMember(any(), guild) } answers { member }
        every { provider.hasShopPermission(any(), guild, GuildProvider.GuildPermission.MANAGE_SHOPS) } returns false
        val lang = mockk<LangService>()
        every { lang.msg("accounting.denied") } returns Component.text("Denied")
        val player = mockk<Player>(relaxed = true)
        val service = GuildSalesCommand(repository, stalls, provider, lang, mockk<Plugin>())
        service.report(player, "stall")
        member = true
        service.exportReport(player, "stall")
        verify(exactly = 0) { repository.report(any(), any(), any()) }
        verify(exactly = 2) { player.sendMessage(Component.text("Denied")) }
    }

}
