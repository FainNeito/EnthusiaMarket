package net.badgersmc.em.domain.ports

import java.util.UUID

fun interface WeeklyPlayerNames {
    fun nameOf(playerId: UUID): String?
}
