package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.tree.LiteralCommandNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.PaperCommandDefinition
import net.badgersmc.nexus.paper.commands.PaperCommandRegistry
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShopHistoryCommandRegistrationTest {
    private val routes = listOf("shop history", "shop history all", "shop history today",
        "shop history range 2026-10-01 2026-10-30")
    private val sender = mockk<CommandSender>(relaxed = true).also {
        every { it.hasPermission("enthusiamarket.shop.use") } returns true
    }
    private val source = mockk<CommandSourceStack>().also { every { it.sender } returns sender }
    private val lang = mockk<LangService>().also {
        every { it.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }
    private val commands = ShopCommands(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(),
        mockk(), lang, mockk(), mockk(), mockk(), mockk())

    @Test fun `history keeps legacy paging and exposes today all and range`() {
        WebsiteSyncSecretArgumentRegistration.register()
        MarketSearchArgumentRegistration.register()
        val definitions = PaperCommandScanner().scanCommands(
            "net.badgersmc.em.infrastructure.commands", javaClass.classLoader,
        )
        val shop = definitions.single { it.annotation.name == "shop" }
        val paths = shop.subcommands.map { it.path.joinToString(" ") }.toSet()
        assertTrue("history" in paths)
        assertTrue("history today" in paths, "Missing today filter")
        assertTrue("history all" in paths, "Missing retained-history filter")
        assertTrue("history range" in paths, "Missing date-range filter")
    }

    @Test fun `omitted pages reach the existing player guard through the real registrar`() {
        val dispatcher = dispatcher()
        org.junit.jupiter.api.Assertions.assertAll(routes.map { route ->
            org.junit.jupiter.api.function.Executable { assertEquals(1, dispatcher.execute(route, source)) }
        })
        verify(exactly = 4) { sender.sendMessage(Component.text("shop.cmd.players_only")) }
    }

    @Test fun `explicit pages remain registered and permission guarded`() {
        val dispatcher = dispatcher()
        routes.forEach { assertEquals(1, dispatcher.execute("$it 2", source)) }
        verify(exactly = 4) { sender.sendMessage(Component.text("shop.cmd.players_only")) }
        every { sender.hasPermission("enthusiamarket.shop.use") } returns false
        routes.flatMap { listOf(it, "$it 2") }.forEach {
            assertFailsWith<CommandSyntaxException> { dispatcher.execute(it, source) }
        }
    }

    @Test fun `range dates remain required and excess page arguments are rejected`() {
        val dispatcher = dispatcher()
        listOf("shop history range", "shop history range 2026-10-01").forEach {
            assertFailsWith<CommandSyntaxException> { dispatcher.execute(it, source) }
        }
        routes.forEach { assertFailsWith<CommandSyntaxException> { dispatcher.execute("$it 2 extra", source) } }
    }

    private fun dispatcher(): CommandDispatcher<CommandSourceStack> {
        WebsiteSyncSecretArgumentRegistration.register()
        MarketSearchArgumentRegistration.register()
        val definition = PaperCommandScanner().scanCommands(
            "net.badgersmc.em.infrastructure.commands", javaClass.classLoader,
        ).single { it.annotation.name == "shop" }
        val registry = PaperCommandRegistry(mockk(), mockk(), mockk(),
            mapOf("itemMaterials" to SuggestionProvider { _, builder -> builder.buildFuture() }))
        val buildTree = PaperCommandRegistry::class.java.getDeclaredMethod(
            "buildCommandTree", PaperCommandDefinition::class.java, Any::class.java,
        ).also { it.isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val tree = buildTree.invoke(registry, definition, commands) as LiteralCommandNode<CommandSourceStack>
        return CommandDispatcher<CommandSourceStack>().also { it.root.addChild(tree) }
    }
}
