package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

internal class StallAccountingRepositorySqlTest {
    @Test fun fifoReplayUnknownAndAtomicFailure(@TempDir folder: Path) {
        val ds = SQLiteDataSource().apply { url = "jdbc:sqlite:${folder.resolve("accounting.db")}" }
        ds.connection.use { c ->
            val sql = javaClass.classLoader.getResourceAsStream("migrations/V033__guild_stall_accounting.sql")!!.bufferedReader().readText()
            sql.split(';').filter { it.isNotBlank() }.forEach { c.createStatement().use { s -> s.execute(it) } }
        }
        val repo = StallAccountingRepositorySql(ds)
        val alice = UUID.randomUUID(); val bob = UUID.randomUUID()
        fun observation(before: Int, after: Int, actor: UUID?, sold: Int = 0, revenue: Long = 0) =
            StallAccountingObservation(UUID.randomUUID(), "guild", "stall", 1, "item", "diamond", actor, before, after, sold, revenue, 100)
        repo.apply(observation(0, 2, alice)); repo.apply(observation(2, 5, bob))
        val sale = observation(5, 2, null, 3, 10)
        repo.apply(sale); repo.apply(sale)
        val rows = repo.report("guild", "stall", ShopHistoryWindow(0, 1000))
        assertEquals(10, rows.sumOf { it.grossRevenue }); assertEquals(2, rows.single { it.contributor == alice }.quantity)
        assertEquals(emptyList(), repo.report("another", "stall", ShopHistoryWindow(0, 1000)))
        assertEquals(emptyList(), repo.report("guild", "stall", ShopHistoryWindow(101, 1000)))
        // Unobserved inventory drift invalidates remaining contributor guesses.
        repo.apply(observation(7, 6, null, 1, 9))
        assertEquals(9, repo.report("guild", "stall", ShopHistoryWindow(0, 1000)).single { it.contributor == null }.grossRevenue)
        ds.connection.use { c -> c.createStatement().use { it.execute("CREATE TRIGGER fail_sale BEFORE INSERT ON guild_sale_attribution BEGIN SELECT RAISE(ABORT, 'failure'); END") } }
        val failed = observation(6, 5, null, 1, 3)
        assertFails { repo.apply(failed) }
        ds.connection.use { c -> c.createStatement().use { it.execute("DROP TRIGGER fail_sale") } }
        repo.apply(failed)
        assertEquals(22, repo.report("guild", "stall", ShopHistoryWindow(0, 1000)).sumOf { it.grossRevenue })
    }

    @Test fun outboxRestartRetainsFailedDelivery(@TempDir folder: Path) {
        val delivered = mutableListOf<StallAccountingObservation>()
        var fail = true
        val repo = object : StallAccountingRepository {
            override fun apply(observation: StallAccountingObservation) { check(!fail) { "offline" }; delivered += observation }
            override fun report(guildId: String, stallId: String, window: ShopHistoryWindow) = emptyList<ContributorSales>()
        }
        val event = StallAccountingObservation(UUID.randomUUID(), "guild", "stall", 1, "item", "diamond", null, 0, 3, createdAt = 10)
        StallAccountingOutbox(folder, repo).append(event)
        assertFails { StallAccountingOutbox(folder, repo).drain() }
        fail = false
        val restarted = StallAccountingOutbox(folder, repo); restarted.drain(); restarted.drain()
        assertEquals(listOf(event), delivered)
    }
}
