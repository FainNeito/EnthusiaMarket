package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.tree.LiteralCommandNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.em.infrastructure.bedrock.PlayerNameResolver
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.PaperCommandDefinition
import net.badgersmc.nexus.paper.commands.PaperCommandRegistry
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShopTrustCommandRegistrationTest {
    private val management = mockk<ShopManagementService>(relaxed = true)
    private val names = mockk<PlayerNameResolver>()
    private val lang = mockk<LangService>().also {
        every { it.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }
    private val commands = ShopCommands(
        management, mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), lang,
        mockk(), mockk(), mockk(), names,
    )
    private val player = mockk<Player>(relaxed = true).also {
        every { it.hasPermission("enthusiamarket.shop.use") } returns true
        every { it.uniqueId } returns UUID.randomUUID()
    }
    private val source = mockk<CommandSourceStack>().also { every { it.sender } returns player }
    private val dispatcher by lazy { registeredDispatcher() }

    @Test fun `bare and explicit menu routes reach the same name validation without mutation`() {
        every { names.resolve("MissingPlayer") } returns null
        assertEquals(1, dispatcher.execute("shop trust MissingPlayer", source))
        assertEquals(1, dispatcher.execute("shop trust MissingPlayer menu", source))
        verify(exactly = 2) { names.resolve("MissingPlayer") }
        verify(exactly = 2) { player.sendMessage(Component.text("shop.cmd.unknown_player")) }
        verify(exactly = 0) { management.trustAll(any(), any()); management.trust(any(), any(), any()) }
    }

    @Test fun `explicit all still applies only the resolved target to the actor shops`() {
        val targetId = UUID.randomUUID()
        val target = mockk<OfflinePlayer>().also { every { it.uniqueId } returns targetId }
        every { names.resolve("KnownPlayer") } returns target
        assertEquals(1, dispatcher.execute("shop trust KnownPlayer all", source))
        verify(exactly = 1) { management.trustAll(player.uniqueId, targetId) }
        verify(exactly = 0) { management.trust(any(), any(), any()) }
    }

    @Test fun `every trust route retains base permission and player only restriction`() {
        val routes = listOf("shop trust KnownPlayer", "shop trust KnownPlayer menu", "shop trust KnownPlayer all")
        every { player.hasPermission("enthusiamarket.shop.use") } returns false
        routes.forEach { assertFailsWith<CommandSyntaxException> { dispatcher.execute(it, source) } }
        val console = mockk<CommandSender>(relaxed = true)
        every { console.hasPermission("enthusiamarket.shop.use") } returns true
        every { source.sender } returns console
        routes.forEach { assertEquals(1, dispatcher.execute(it, source)) }
        verify(exactly = 3) { console.sendMessage(Component.text("shop.cmd.players_only")) }
        verify(exactly = 0) { names.resolve(any()); management.trustAll(any(), any()) }
    }

    @Test fun `player name remains required and extra arguments are rejected`() {
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("shop trust", source) }
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("shop trust KnownPlayer menu extra", source) }
        verify(exactly = 0) { names.resolve(any()); management.trustAll(any(), any()) }
    }

    private fun registeredDispatcher(): CommandDispatcher<CommandSourceStack> {
        MarketSearchArgumentRegistration.register()
        WebsiteSyncSecretArgumentRegistration.register()
        val definition = PaperCommandScanner().scanCommands(
            "net.badgersmc.em.infrastructure.commands", javaClass.classLoader,
        ).single { it.annotation.name == "shop" }
        val registry = PaperCommandRegistry(
            mockk(), mockk(), mockk(),
            mapOf("itemMaterials" to SuggestionProvider { _, builder -> builder.buildFuture() }),
        )
        // Use the actual pinned registrar: a reconstructed tree would miss ignored Kotlin defaults.
        val buildTree = PaperCommandRegistry::class.java.getDeclaredMethod(
            "buildCommandTree", PaperCommandDefinition::class.java, Any::class.java,
        ).also { it.isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val tree = buildTree.invoke(registry, definition, commands) as LiteralCommandNode<CommandSourceStack>
        return CommandDispatcher<CommandSourceStack>().also { it.root.addChild(tree) }
    }
}
