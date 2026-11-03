package net.badgersmc.em.interaction

/** Pure text mapping. Values are literal text; IDs never enter identity fallbacks. */
internal class StallAccessLabels(private val translate: (String) -> String? = { null }) {
    fun text(key: String, vararg values: Pair<String, Any>): String {
        val template = translate(key)?.takeIf { it.isNotBlank() } ?: defaults[key].orEmpty()
        val substitutions = values.toMap()
        return placeholder.replace(template) { match -> substitutions[match.groupValues[1]]?.toString() ?: match.value }
    }

    fun title(stall: String, section: String, guildName: String? = null): String = text("title",
        "stall" to (literal(stall) ?: text("stall.unknown")), "section" to this.section(section, guildName))
    fun state(allowed: Boolean): String = text(if (allowed) "state.allowed" else "state.blocked")
    fun hasCapability(name: String): Boolean = "capability.${name.lowercase(java.util.Locale.ROOT)}" in defaults
    fun capability(name: String): String = if (hasCapability(name)) text("capability.${name.lowercase(java.util.Locale.ROOT)}") else humanize(name)
    fun effect(name: String): String = translate("effect.${name.lowercase(java.util.Locale.ROOT)}")?.takeIf { it.isNotBlank() }
        ?: humanize(name).ifBlank { text("effect.unknown") }
    fun player(name: String?, number: Int): String = literal(name) ?: text("player.unknown", "number" to number)
    fun guild(name: String?, number: Int): String = literal(name) ?: text("guild.unknown_entry", "number" to number)
    fun section(section: String, guildName: String? = null): String = when {
        section.startsWith("ally:") -> text("section.ally", "name" to (literal(guildName) ?: text("guild.unknown")))
        section in setOf("effects", "blacklist", "allies") -> text("section.$section")
        else -> text("section.flags")
    }

    private fun humanize(value: String): String = value.lowercase(java.util.Locale.ROOT).replace('_', ' ').trim()
        .replaceFirstChar { it.titlecase(java.util.Locale.ROOT) }

    private fun literal(value: String?): String? {
        return value?.replace(legacy, "")
            ?.filterNot { it.isISOControl() }?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private val placeholder = Regex("\\{(\\w+)\\}")
        private val legacy = Regex("(?i)§[0-9a-fk-orx]")
        internal val defaults = mapOf(
            "title" to "Stall {stall}: {section}",
            "stall.unknown" to "Unknown stall",
            "section.flags" to "Visitor access",
            "section.effects" to "Potion effects",
            "section.blacklist" to "Blocked players",
            "section.allies" to "Allied guild access",
            "section.ally" to "{name} access",
            "guild.unknown" to "Unknown guild",
            "guild.unknown_entry" to "Unknown guild {number}",
            "guild.configure" to "{name}: configure access",
            "player.unknown" to "Unknown player {number}",
            "player.unblock" to "Unblock {name}",
            "state.allowed" to "Allowed",
            "state.blocked" to "Blocked",
            "visitor.capability" to "Visitors · {name}: {state}",
            "capability.state" to "{name}: {state}",
            "effect.state" to "{name}: {state}",
            "effect.unknown" to "Unknown effect",
            "potions.incoming" to "Incoming splash/lingering potions: {state}",
            "blacklist.add" to "Add a blocked player (click for command)",
            "blacklist.command" to "Use /stallaccess blacklist {stall} <player> true to block a player.",
            "navigation.previous" to "Previous page",
            "navigation.next" to "Next page",
            "navigation.flags" to "Visitor access",
            "navigation.effects" to "Potion effects",
            "navigation.blacklist" to "Blocked players",
            "navigation.allies" to "Allied guilds (access off by default)",
            "error.setting_failed" to "We could not confirm this setting. Reopen the menu to check its current state; contact staff if this continues.",
            "capability.entry" to "Enter the stall",
            "capability.trade" to "Trade with shops",
            "capability.anvil" to "Use anvils",
            "capability.lectern" to "Use lecterns",
            "capability.doors" to "Open doors",
            "capability.buttons" to "Use buttons",
            "capability.levers" to "Use levers",
            "capability.item_pickup" to "Pick up items",
            "capability.chests" to "Open chests",
            "capability.stock" to "Manage shop stock",
            "capability.prices" to "Change shop prices"
        )
    }
}
