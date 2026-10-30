package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.tree.LiteralCommandNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.badgersmc.em.infrastructure.listeners.FinderTrailService
import net.badgersmc.nexus.paper.commands.PaperCommandDefinition
import net.badgersmc.nexus.paper.commands.PaperCommandRegistry
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShopTrailCommandRegistrationTest {
    private val finder = mockk<FinderTrailService>(relaxed = true)
    private val commands = ShopCommands(
        mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(),
        mockk(), mockk(), mockk(), mockk(), finderTrail = finder,
    )
    private val player = mockk<Player>(relaxed = true).also {
        every { it.hasPermission("enthusiamarket.shop.use") } returns true
    }
    private val source = mockk<CommandSourceStack>().also { every { it.sender } returns player }
    private val dispatcher by lazy { registeredDispatcher() }

    @Test fun `advertised stop command reaches cancellation without a mode argument`() {
        assertEquals(1, dispatcher.execute("shop trail stop", source))
        verify(exactly = 1) { finder.stop(player) }
        verify(exactly = 0) { finder.setMode(any(), any()); finder.start(any(), any()) }
    }

    @Test fun `mode commands preserve every supported preference and do not start guidance`() {
        listOf("locator", "particles", "both", "default").forEach { mode ->
            assertEquals(1, dispatcher.execute("shop trail mode $mode", source))
            verify(exactly = 1) { finder.setMode(player, mode) }
        }
        verify(exactly = 0) { finder.stop(any()); finder.start(any(), any()) }
    }

    @Test fun `mode value is required and stop rejects accidental extra arguments`() {
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("shop trail mode", source) }
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("shop trail stop unused", source) }
        verify(exactly = 0) { finder.stop(any()); finder.setMode(any(), any()); finder.start(any(), any()) }
    }

    @Test fun `both routes retain the shop permission gate`() {
        every { player.hasPermission("enthusiamarket.shop.use") } returns false
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("shop trail stop", source) }
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("shop trail mode both", source) }
        verify(exactly = 0) { finder.stop(any()); finder.setMode(any(), any()); finder.start(any(), any()) }
    }

    @Test fun `console cannot change or stop a player's guide`() {
        val console = mockk<CommandSender>(relaxed = true)
        every { console.hasPermission("enthusiamarket.shop.use") } returns true
        every { source.sender } returns console
        dispatcher.execute("shop trail stop", source)
        dispatcher.execute("shop trail mode both", source)
        verify(exactly = 0) { finder.stop(any()); finder.setMode(any(), any()); finder.start(any(), any()) }
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
        // Exercise the actual pinned Nexus registrar, including its parameter/permission
        // handling. Its tree builder is private; rebuilding it here would miss this bug.
        val buildTree = PaperCommandRegistry::class.java.getDeclaredMethod(
            "buildCommandTree", PaperCommandDefinition::class.java, Any::class.java,
        ).also { it.isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val tree = buildTree.invoke(registry, definition, commands) as LiteralCommandNode<CommandSourceStack>
        return CommandDispatcher<CommandSourceStack>().also { it.root.addChild(tree) }
    }
}
