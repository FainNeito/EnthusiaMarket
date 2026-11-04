package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.guild.PolicyKind
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.events.GuildTradePolicyChangedEvent
import net.badgersmc.em.infrastructure.i18n.EnthusiaMarketLang
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.text.minimessage.MiniMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuildPolicyLocaleTest {
    @TempDir lateinit var directory: Path
    private fun plain(component: net.kyori.adventure.text.Component) =
        PlainTextComponentSerializer.plainText().serialize(component)

    @Test fun `partial server locale renders all policy notifications and retains overrides`() {
        Files.createDirectories(directory.resolve("lang"))
        Files.writeString(directory.resolve("lang/en_US.yml"), "guildpolicy:\n  no_permission: 'Custom denial'\n")
        val host = object : LangHost {
            override val dataFolder = directory.toFile()
            override val resourceClassLoader = javaClass.classLoader
        }
        val lang = LangService(host, Locale("en_US"), EnthusiaMarketLang::class.java)
        val provider = mockk<GuildProvider> {
            every { guildById("g1") } returns GuildProvider.GuildRef("g1", "Alpha")
            every { guildById("g2") } returns GuildProvider.GuildRef("g2", "Beta")
        }
        val listener = GuildPolicyAnnounceListener(provider, lang, EnthusiaMarketConfig())
        val tariffComponent = listener.buildMessage(GuildTradePolicyChangedEvent("g1", "g2", PolicyKind.TARIFF, 20, GuildTradePolicyChangedEvent.Action.SET))!!
        val embargoComponent = listener.buildMessage(GuildTradePolicyChangedEvent("g1", "g2", PolicyKind.EMBARGO, 0, GuildTradePolicyChangedEvent.Action.SET))!!
        val tariff = plain(tariffComponent)
        val embargo = plain(embargoComponent)
        val cleared = plain(listener.buildMessage(GuildTradePolicyChangedEvent("g1", "g2", null, 0, GuildTradePolicyChangedEvent.Action.CLEARED))!!)
        assertTrue(listOf("Alpha", "Beta", "20").all(tariff::contains), tariff)
        assertTrue(listOf("Alpha", "Beta").all(embargo::contains), embargo)
        assertTrue(listOf("Alpha", "Beta").all(cleared::contains), cleared)
        assertTrue(listOf(tariff, embargo, cleared).all { it.startsWith("[Market]") })
        val tariffStyles = MiniMessage.miniMessage().serialize(tariffComponent).lowercase()
        val embargoStyles = MiniMessage.miniMessage().serialize(embargoComponent).lowercase()
        assertTrue(tariffStyles.contains("#ffcf1f") && tariffStyles.contains("#fff6e0"), tariffStyles)
        assertTrue(embargoStyles.contains("#c0261d"), embargoStyles)
        val tariffWarning = plain(lang.msg("guildpolicy.entry.tariff", "owner" to "Alpha", "rate" to 20))
        val embargoWarning = plain(lang.msg("guildpolicy.entry.embargo", "owner" to "Alpha"))
        assertTrue(tariffWarning.contains("Alpha") && tariffWarning.contains("20"), tariffWarning)
        assertTrue(embargoWarning.contains("Alpha"), embargoWarning)
        assertTrue(tariffWarning.startsWith("[Market]") && embargoWarning.startsWith("[Market]"))
        assertEquals("Custom denial", plain(lang.msg("guildpolicy.no_permission")))
    }
}
