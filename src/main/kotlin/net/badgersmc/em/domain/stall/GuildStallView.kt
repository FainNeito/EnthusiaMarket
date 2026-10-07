package net.badgersmc.em.domain.stall

import java.time.Instant
import java.util.UUID

/** Framework-free stall read model; capabilities never grant permissions. */
data class GuildStallView(
    val id: String,
    val region: String,
    val world: String,
    val state: String,
    val rent: Long,
    val intervalSeconds: Long,
    val nextRentAt: Instant?,
    val graceEndsAt: Instant?,
    val members: List<GuildStallMemberView>,
)

/** Current guild members with at least one permitted shop action. */
data class GuildStallMemberView(val playerId: UUID, val permissions: Set<String>)
