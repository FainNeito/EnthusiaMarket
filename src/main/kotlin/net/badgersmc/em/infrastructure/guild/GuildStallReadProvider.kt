package net.badgersmc.em.infrastructure.guild

import net.badgersmc.em.application.GuildStallQueryService
import net.badgersmc.em.domain.ports.RegionProvider
import net.enthusia.market.api.guild.GuildStallMember
import net.enthusia.market.api.guild.GuildStallReadApi
import net.enthusia.market.api.guild.GuildStallSnapshot
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor

/** Database reads run off-thread; WorldGuard coordinates are resolved on-thread. */
class GuildStallReadProvider(
    private val query: GuildStallQueryService,
    private val regions: RegionProvider,
    private val io: Executor,
    private val serverThread: Executor,
) : GuildStallReadApi {
    override fun apiVersion(): Int = GuildStallReadApi.API_VERSION

    override fun guildStalls(guildId: UUID, viewerId: UUID): CompletionStage<List<GuildStallSnapshot>> =
        CompletableFuture.supplyAsync({ query.read(guildId, viewerId) }, io).thenApplyAsync({ rows ->
            rows.map { row ->
                val bounds = regions.bounds(row.world, row.region)
                val coordinates = bounds?.let {
                    "${it.minX + (it.maxX - it.minX) / 2}, ${it.minY}, ${it.minZ + (it.maxZ - it.minZ) / 2}"
                }
                GuildStallSnapshot(
                    row.id, row.region, row.world, row.state, row.rent, row.intervalSeconds,
                    row.nextRentAt, row.graceEndsAt, coordinates,
                    row.members.map { GuildStallMember(it.playerId, it.permissions) },
                )
            }
        }, serverThread)
}
