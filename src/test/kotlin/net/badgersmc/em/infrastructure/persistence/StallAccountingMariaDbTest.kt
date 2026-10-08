package net.badgersmc.em.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.badgersmc.em.domain.shop.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.*

@EnabledIfEnvironmentVariable(named = "MARKET_ACCOUNTING_TEST_MARIA_PORT", matches = "[0-9]+")
internal class StallAccountingMariaDbTest {
    @Test fun nativeFifoReplayAndRollback() {
        val port = System.getenv("MARKET_ACCOUNTING_TEST_MARIA_PORT").toInt()
        require(port in 1024..65535 && port != 3306)
        val schema = "em_accounting_" + UUID.randomUUID().toString().replace("-", "")
        val user = requireNotNull(System.getenv("MARKET_ACCOUNTING_TEST_MARIA_USER"))
        val password = requireNotNull(System.getenv("MARKET_ACCOUNTING_TEST_MARIA_PASSWORD"))
        require(user.isNotBlank() && password.isNotBlank())
        val admin = "jdbc:mariadb://127.0.0.1:$port/"
        DriverManager.getConnection(admin, user, password).use { c ->
            c.createStatement().use { it.execute("CREATE DATABASE $schema") }
            try {
                HikariDataSource(HikariConfig().apply {
                    jdbcUrl = admin + schema; username = user; this.password = password; maximumPoolSize = 2
                }).use { ds ->
                    verifyContracts(ds)
                }
            } finally { c.createStatement().use { it.execute("DROP DATABASE $schema") } }
        }
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
