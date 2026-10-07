package net.badgersmc.em.infrastructure.guild

import io.mockk.every
import io.mockk.mockk
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
    /** Persistence executes first off-thread; coordinates resolve only on the server executor. */
    @Test
    fun separatesThreadBoundaries() {
        val io = ArrayDeque<Runnable>()
        val main = ArrayDeque<Runnable>()
        val query = mockk<GuildStallQueryService>()
        val regions = mockk<RegionProvider>()
        val guild = UUID.randomUUID()
        val viewer = UUID.randomUUID()
        every { query.read(guild, viewer) } returns listOf(
            GuildStallView("stall1", "stall1", "world", "OWNED", 100L, 86400L, null, null, emptyList()),
        )
        every { regions.bounds("world", "stall1") } returns null
        val provider = GuildStallReadProvider(query, regions, Executor { io.add(it) }, Executor { main.add(it) })
        val result = provider.guildStalls(guild, viewer).toCompletableFuture()
        assertFalse(result.isDone)
        io.removeFirst().run()
        assertFalse(result.isDone)
        main.removeFirst().run()
        assertEquals("stall1", result.join().single().id())
        assertNull(result.join().single().coordinates())
        assertEquals(1, provider.apiVersion())
    }
}
