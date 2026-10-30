package net.badgersmc.em.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.badgersmc.em.domain.shop.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import net.badgersmc.em.testing.MarketMariaDbFixture
import java.util.UUID
import kotlin.test.*

internal class StallAccountingMariaDbTest {
    companion object {
        @JvmField @RegisterExtension
        val database = MarketMariaDbFixture("stall_accounting")
    }

    @Test fun nativeFifoReplayAndRollback() {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = database.jdbcUrl
            username = database.username
            password = database.password
            maximumPoolSize = 2
        }).use(::verifyContracts)
    }

    private fun verifyContracts(ds: HikariDataSource) {
                    ds.connection.use { connection ->
                        val migration = javaClass.classLoader.getResourceAsStream("migrations/V033__guild_stall_accounting.sql")!!.bufferedReader().readText()
                        migration.split(';').filter { it.isNotBlank() }.forEach { sql -> connection.createStatement().use { it.execute(sql) } }
                    }
                    val repo = StallAccountingRepositorySql(ds)
                    val member = UUID.randomUUID()
                    fun event(stock: Pair<Int, Int>, actor: UUID?, sold: Int = 0, payment: Long = 0) =
                        StallAccountingObservation(UUID.randomUUID(), "guild", "stall", 1, "item", "diamond", actor, stock.first, stock.second, sold, payment, 100)
                    repo.apply(event(0 to 3, member))
                    val sale = event(3 to 2, null, 1, 7)
                    ds.connection.use { connection -> connection.createStatement().use {
                        it.execute("CREATE TRIGGER reject_accounting BEFORE INSERT ON guild_sale_attribution FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test failure'")
                    } }
                    assertFails { repo.apply(sale) }
                    ds.connection.use { connection -> connection.createStatement().use { it.execute("DROP TRIGGER reject_accounting") } }
                    repo.apply(sale); repo.apply(sale)
                    val rows = repo.report("guild", "stall", ShopHistoryWindow(0, 1000))
                    assertEquals(1, rows.size)
                    assertEquals(member, rows.single().contributor)
                    assertEquals(7, rows.single().grossRevenue)
                    assertEquals(1, rows.single().quantity)
                    assertEquals(emptyList(), repo.report("other", "stall", ShopHistoryWindow(0, 1000)))
    }
}
