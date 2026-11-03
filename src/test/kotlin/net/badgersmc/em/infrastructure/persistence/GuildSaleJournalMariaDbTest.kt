package net.badgersmc.em.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.badgersmc.em.domain.ports.GuildSaleIntent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import net.badgersmc.em.testing.MarketMariaDbFixture
import java.util.UUID
import kotlin.test.assertEquals

/** Disposable fixture; never uses server/operator connection configuration. */
class GuildSaleJournalMariaDbTest {
    companion object {
        @JvmField @RegisterExtension
        val database = MarketMariaDbFixture("guild_sale_journal")
    }

    @Test fun `MariaDB journal persists completed only replay with idempotent acknowledgement states`() {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = database.jdbcUrl
            username = database.username
            password = database.password
            maximumPoolSize = 2
        }).use { ds ->
            ds.connection.use { c ->
                c.createStatement().use { it.executeUpdate("DROP TABLE IF EXISTS guild_sale_xp_journal") }
                val sql = checkNotNull(javaClass.classLoader.getResourceAsStream("migrations/V031__guild_sale_xp_journal.sql"))
                    .bufferedReader().use { it.readText() }
                sql.split(';').filter { it.isNotBlank() }.forEach { statement -> c.createStatement().use { it.executeUpdate(statement) } }
            }
            val repo = GuildSaleJournalSql(ds)
            val guild = UUID.randomUUID()
            val buyer = UUID.randomUUID()
            val done = GuildSaleIntent(UUID.randomUUID(), guild, buyer, 42, 1000)
            val pending = done.copy(id = UUID.randomUUID(), occurredAt = 2000)
            val aborted = done.copy(id = UUID.randomUUID(), occurredAt = 3000)
            listOf(done, pending, aborted).forEach(repo::prepare)
            repo.completed(done.id)
            repo.aborted(aborted.id)
            val restarted = GuildSaleJournalSql(ds)
            assertEquals(listOf(done), restarted.pending(100))
            restarted.acknowledged(done.id, "AWARDED:5")
            assertEquals(emptyList(), restarted.pending(100))
            restarted.aborted(done.id)
            assertEquals(emptyList(), restarted.pending(100))
        }
    }
}
