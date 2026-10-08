package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolvers
import kotlin.test.Test
import kotlin.test.assertEquals

class MarketSearchCommandRegistrationTest {
    @Test fun `actual Nexus search argument permits unquoted explicit selectors`() {
        MarketSearchArgumentRegistration.register()
        WebsiteSyncSecretArgumentRegistration.register()
        val shop = PaperCommandScanner().scanCommands("net.badgersmc.em.infrastructure.commands", javaClass.classLoader)
            .single { it.annotation.name == "shop" }
        val search = shop.subcommands.single { it.path == listOf("search") }
        val parameter = search.parameters.single { it.isArg }
        val resolver = requireNotNull(PaperArgumentResolvers.get(parameter.type))
        @Suppress("UNCHECKED_CAST")
        val type = resolver.argumentType() as ArgumentType<Any>
        executeQueries(type) { context ->
            val extracted = resolver.extract(context, parameter.name)
            if (extracted is MarketSearchArgument) extracted.value else extracted.toString()
        }
    }

    @Test fun `finditem argument and unchanged default String grammar are distinct`() {
        executeQueries(MarketSearchArgumentRegistration.argumentType()) { StringArgumentType.getString(it, "query") }
        val resolver = requireNotNull(PaperArgumentResolvers.get(String::class))
        val reader = com.mojang.brigadier.StringReader("item:stone")
        assertEquals("item", resolver.argumentType().parse(reader))
        assertEquals(4, reader.cursor)
    }

    private fun <T> executeQueries(type: ArgumentType<T>, extract: (com.mojang.brigadier.context.CommandContext<Unit>) -> String) {
        val dispatcher = CommandDispatcher<Unit>()
        var captured = ""
        dispatcher.register(LiteralArgumentBuilder.literal<Unit>("search").then(
            RequiredArgumentBuilder.argument<Unit, T>("query", type)
                .executes { captured = extract(it); 1 }
        ))
        listOf("category:stone", "item:minecraft:stone", "item:diamond_pickaxe", "stone_br", "diamond sword")
            .forEach { query -> dispatcher.execute("search $query", Unit); assertEquals(query, captured) }
    }
}
