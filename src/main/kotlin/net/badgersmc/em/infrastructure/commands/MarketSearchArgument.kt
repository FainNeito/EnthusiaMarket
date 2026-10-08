package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolver
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolvers

/** Search-only remainder argument; default Nexus String arguments retain their existing grammar. */
data class MarketSearchArgument(val value: String)

object MarketSearchArgumentRegistration {
    fun argumentType(): ArgumentType<String> = StringArgumentType.greedyString()

    fun register() {
        PaperArgumentResolvers.register(object : PaperArgumentResolver<MarketSearchArgument> {
            override val type = MarketSearchArgument::class
            @Suppress("UNCHECKED_CAST")
            override fun argumentType(): ArgumentType<MarketSearchArgument> =
                MarketSearchArgumentRegistration.argumentType() as ArgumentType<MarketSearchArgument>
            override fun extract(context: CommandContext<*>, name: String): MarketSearchArgument =
                MarketSearchArgument(StringArgumentType.getString(context, name))
        })
    }
}
