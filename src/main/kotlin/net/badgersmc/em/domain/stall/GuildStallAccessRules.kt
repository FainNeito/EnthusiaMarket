package net.badgersmc.em.domain.stall

import net.badgersmc.em.domain.ports.GuildProvider
import java.util.UUID

/** Shared authority for guild shops, protection and read-only capability display. */
object GuildStallAccessRules {
    fun allows(stall: Stall, actor: UUID, guilds: GuildProvider, permission: GuildProvider.GuildPermission): Boolean =
        stall.isActiveGuildStall() && guilds.isMember(actor, stall.owner.id) &&
            guilds.hasShopPermission(actor, stall.owner.id, permission)
}
