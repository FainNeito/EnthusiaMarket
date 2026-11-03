package net.badgersmc.em.interaction.help

import net.badgersmc.em.infrastructure.commands.AdminCommands
import net.badgersmc.em.infrastructure.commands.ShopCommands
import net.badgersmc.em.infrastructure.commands.ShopHelpCommands
import net.badgersmc.em.infrastructure.commands.StoreCommands
import net.badgersmc.em.infrastructure.commands.VaultCommands
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import org.junit.jupiter.api.Test
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.test.assertTrue

class HelpCommandContractTest {
    @Test fun `advertised help commands exist and include their required arguments`() {
        val classes = listOf(AdminCommands::class, ShopCommands::class, ShopHelpCommands::class,
            StoreCommands::class, VaultCommands::class)
        val errors = mutableListOf<String>()
        HelpTopics.all.flatMap { it.commands }.filter { it.syntax.startsWith("/") }.forEach { entry ->
            val tokens = entry.syntax.removePrefix("/").split(" ")
            val owner = classes.firstOrNull {
                val command = it.findAnnotation<Command>()!!
                tokens.first() == command.name || tokens.first() in command.aliases
            }
            val candidates = owner?.functions?.mapNotNull { method ->
                method.findAnnotation<Subcommand>()?.let { method to it.value.split(" ") }
            }.orEmpty().filter { (_, path) -> tokens.drop(1).take(path.size) == path }
            val longestPath = candidates.maxOfOrNull { it.second.size }
            val match = candidates.filter { it.second.size == longestPath }.firstOrNull { (method, path) ->
                val arguments = method.parameters.filter { it.findAnnotation<Arg>() != null }
                val required = arguments.count {
                    it.findAnnotation<Arg>()!!.required && !it.isOptional && !it.type.isMarkedNullable
                }
                tokens.size - 1 - path.size in required..arguments.size
            }
            if (match == null) {
                errors += "Unregistered help command or invalid overload arguments: ${entry.syntax}"
            }
        }
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }

    @Test fun `shop tutorial aliases retain the required show subcommand`() {
        val root = ShopHelpCommands::class.findAnnotation<Command>()!!
        assertTrue(root.aliases.toSet().containsAll(listOf("shoptutorial", "sht")))
        assertTrue(
            ShopHelpCommands::class.functions.any {
                it.findAnnotation<Subcommand>()?.value == "show"
            }
        )
    }
}
