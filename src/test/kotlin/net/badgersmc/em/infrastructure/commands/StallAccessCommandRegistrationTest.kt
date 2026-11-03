package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolvers
import kotlin.test.Test
import kotlin.test.assertEquals

class StallAccessCommandRegistrationTest {
    @Test fun `documented settings and blacklist commands consume arguments without literal placeholders`() {
        MarketSearchArgumentRegistration.register()
        WebsiteSyncSecretArgumentRegistration.register()
        val definition = PaperCommandScanner().scanCommands(
            "net.badgersmc.em.infrastructure.commands", javaClass.classLoader,
        ).single { it.annotation.name == "stallaccess" }
        val dispatcher = CommandDispatcher<Unit>()
        val captured = mutableListOf<Pair<String, List<Any>>>()
        definition.subcommands.forEach { sub ->
            val args = sub.parameters.filter { it.isArg }
            val command = com.mojang.brigadier.Command<Unit> { context ->
                captured.add(sub.method.name to args.map { parameter ->
                    requireNotNull(PaperArgumentResolvers.get(parameter.type)).extract(context, parameter.name)
                })
                1
            }
            var child: com.mojang.brigadier.builder.ArgumentBuilder<Unit, *>? = null
            args.asReversed().forEach { parameter ->
                val resolver = requireNotNull(PaperArgumentResolvers.get(parameter.type))
                @Suppress("UNCHECKED_CAST")
                val node = RequiredArgumentBuilder.argument<Unit, Any>(parameter.name, resolver.argumentType() as ArgumentType<Any>)
                if (child == null) node.executes(command) else node.then(child)
                child = node
            }
            sub.path.asReversed().forEach { literal ->
                val node = LiteralArgumentBuilder.literal<Unit>(literal)
                if (child == null) node.executes(command) else node.then(child)
                child = node
            }
            dispatcher.register(LiteralArgumentBuilder.literal<Unit>("stallaccess").then(requireNotNull(child)))
        }
        dispatcher.execute("stallaccess settings stall24", Unit)
        dispatcher.execute("stallaccess blacklist stall24 TestVisitor true", Unit)
        dispatcher.execute("stallaccess blacklist stall24 TestVisitor false", Unit)
        assertEquals<List<Pair<String, List<Any>>>>(listOf(
            "settings" to listOf("stall24"),
            "blacklist" to listOf("stall24", "TestVisitor", "true"),
            "blacklist" to listOf("stall24", "TestVisitor", "false"),
        ), captured)
    }
}
