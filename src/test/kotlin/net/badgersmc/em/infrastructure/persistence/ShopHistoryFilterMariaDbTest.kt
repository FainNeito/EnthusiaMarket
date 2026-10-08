package net.badgersmc.em.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.badgersmc.em.domain.shop.ShopHistoryWindow
import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.SignDirection
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@Testcontainers(disabledWithoutDocker = true)
class ShopHistoryFilterMariaDbTest {
    @Test fun `MariaDB window privacy boundaries and stable pages match SQLite contract`() {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = database.jdbcUrl
            username = database.username
            password = database.password
        }).use { ds ->
            prepareTable(ds)
            verifyWindow(ShopTransactionRepositorySql(ds))
        }
    }

    private fun prepareTable(ds: javax.sql.DataSource) {
        ds.connection.use { connection ->
            connection.createStatement().use { sql ->
                sql.executeUpdate("DROP TABLE IF EXISTS shop_transactions")
                sql.executeUpdate(CREATE_HISTORY_TABLE)
            }
        }
    }

    private fun verifyWindow(repo: ShopTransactionRepositorySql) {
        val viewer = UUID.randomUUID()
        val expected = seedSales(repo, viewer)
        val window = ShopHistoryWindow(1000, 2000)
        val first = repo.findByOwnerOrBuyer(viewer, 2, 0, window)
        val second = repo.findByOwnerOrBuyer(viewer, 2, 2, window)
        assertEquals(expected, (first + second).map { it.id })
        assertEquals(emptyList(), repo.findByOwnerOrBuyer(viewer, 2, 4, window))
        assertEquals(15, repo.countUnnotified(viewer))
    }

    private fun seedSales(repo: ShopTransactionRepositorySql, viewer: UUID): List<Long> {
        val other = UUID.randomUUID()
        sale(repo, viewer, other, 999)
        val start = sale(repo, viewer, other, 1000)
        val firstTie = sale(repo, other, viewer, 1500)
        val secondTie = sale(repo, viewer, other, 1500)
        sale(repo, other, UUID.randomUUID(), 1600)
        repeat(12) { sale(repo, viewer, other, 2000) }
        return listOf(secondTie.id, firstTie.id, start.id)
    }

    private fun sale(repo: ShopTransactionRepositorySql, owner: UUID, buyer: UUID, time: Long) =
        repo.record(ShopTransaction(0, 1, owner, buyer, SignDirection.SELL, "diamond", 1, 10, time))

    companion object {
        private const val CREATE_HISTORY_TABLE = """CREATE TABLE shop_transactions (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT, shop_id BIGINT NOT NULL,
                    owner VARCHAR(36) NOT NULL, buyer VARCHAR(36) NOT NULL,
                    direction VARCHAR(10) NOT NULL, item TEXT NOT NULL, quantity INT NOT NULL,
                    total_price BIGINT NOT NULL, created_at BIGINT NOT NULL, notified INT NOT NULL DEFAULT 0
                )"""

        @Container @JvmStatic
        private val database = FilterMariaDbContainer("mariadb:11.8.3")
            .withDatabaseName("history_filter_test").withUsername("market_test").withPassword("market_test")
    }

    private class FilterMariaDbContainer(image: String) :
        MariaDBContainer<FilterMariaDbContainer>(DockerImageName.parse(image))
}
