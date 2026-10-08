package net.badgersmc.em.interaction.help

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.em.infrastructure.commands.ShopHelpCommands
import net.badgersmc.em.infrastructure.i18n.EnthusiaMarketLang
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShopTutorialLocaleTest {
    @TempDir lateinit var directory: Path

    @Test fun `partial existing locale preserves overrides and renders newly bundled tutorial lines`() {
        Files.createDirectories(directory.resolve("lang"))
        Files.writeString(directory.resolve("lang/en_US.yml"), "shop:\n  help:\n    line1: 'My tutorial'\n")
        val host = object : LangHost {
            override val dataFolder = directory.toFile()
            override val resourceClassLoader = javaClass.classLoader
        }
        val lang = LangService(host, Locale("en_US"), EnthusiaMarketLang::class.java)
        val sender = mockk<CommandSender>()
        val messages = mutableListOf<String>()
        every { sender.sendMessage(any<Component>()) } answers {
            messages += PlainTextComponentSerializer.plainText().serialize(firstArg())
        }
        ShopHelpCommands(lang).show(sender)
        assertEquals("My tutorial", messages.first())
        assertTrue(messages.any { "Bulk" in it })
        assertTrue(messages.any { "/shopvault open" in it })
        assertTrue(messages.none { "shop.help.line" in it })
    }
}
