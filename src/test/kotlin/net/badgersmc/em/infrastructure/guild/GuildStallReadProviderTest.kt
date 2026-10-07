package net.badgersmc.em.infrastructure.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.application.GuildStallQueryService
import net.badgersmc.em.domain.ports.RegionProvider
import net.badgersmc.em.domain.stall.GuildStallView
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Actual public API records and separate IO/server executors are tested here. */
internal class GuildStallReadProviderTest {
    private val query = mockk<GuildStallQueryService>()
    /** Persistence executes first off-thread; coordinates resolve only on the server executor. */
    @Test
    fun separatesThreadBoundaries() {
        val io = ArrayDeque<Runnable>()
        val main = ArrayDeque<Runnable>()
        val guild = UUID.randomUUID()
        val viewer = UUID.randomUUID()
        val provider = provider(guild, viewer, io, main)
        val result = provider.guildStalls(guild, viewer).toCompletableFuture()
        assertFalse(result.isDone)
        io.removeFirst().run()
        assertFalse(result.isDone)
        verify(exactly = 0) { query.readLoaded(any(), any(), any()) }
        main.removeFirst().run()
        verify(exactly = 1) { query.readLoaded(guild, viewer, emptyList()) }
        assertEquals("stall1", result.join().single().id())
        assertNull(result.join().single().coordinates())
        assertEquals(1, provider.apiVersion())
    }

    private fun provider(
        guild: UUID, viewer: UUID, io: ArrayDeque<Runnable>, main: ArrayDeque<Runnable>,
    ): GuildStallReadProvider {
        val regions = mockk<RegionProvider>()
        every { query.load(guild) } returns emptyList()
        every { query.readLoaded(guild, viewer, emptyList()) } returns listOf(
            GuildStallView("stall1", "stall1", "world", "OWNED", 100L, 86400L, null, null, emptyList()),
        )
        every { regions.bounds("world", "stall1") } returns null
        return GuildStallReadProvider(query, regions, Executor { io.add(it) }, Executor { main.add(it) })
    }
}
