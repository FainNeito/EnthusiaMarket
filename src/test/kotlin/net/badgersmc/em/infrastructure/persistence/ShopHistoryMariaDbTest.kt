package net.badgersmc.em.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.SignDirection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Testcontainers(disabledWithoutDocker = true)
class ShopHistoryMariaDbTest {
    private lateinit var source: HikariDataSource
    private lateinit var repository: ShopTransactionRepositorySql
    private val owner = UUID.randomUUID()
    private val sale get() = ShopTransaction(shopId = 42, owner = owner, buyer = owner,
        direction = SignDirection.SELL, item = "diamond", quantity = 3, totalPrice = 300, createdAt = 1000)

    @BeforeEach fun setup() {
        source = HikariDataSource(HikariConfig().apply {
            jdbcUrl = database.jdbcUrl
            username = database.username
            password = database.password
            maximumPoolSize = 4
        })
        source.connection.use { connection -> connection.createStatement().use { statement ->
            statement.execute("DROP TABLE IF EXISTS shop_transactions")
            statement.execute("DROP TABLE IF EXISTS shop_history_receipts")
            statement.execute("""CREATE TABLE shop_transactions (
                id BIGINT PRIMARY KEY AUTO_INCREMENT, shop_id BIGINT NOT NULL, owner VARCHAR(36) NOT NULL,
                buyer VARCHAR(36) NOT NULL, direction VARCHAR(8) NOT NULL, item VARCHAR(128) NOT NULL,
                quantity INT NOT NULL, total_price BIGINT NOT NULL, created_at BIGINT NOT NULL, notified BOOLEAN NOT NULL
            ) ENGINE=InnoDB""")
            val migration = javaClass.classLoader.getResourceAsStream("migrations/V032__shop_history_receipts.sql")!!
                .bufferedReader().use { it.readText() }
            statement.execute(migration)
            statement.execute(migration) // Reapplication must preserve receipts.
        } }
        repository = ShopTransactionRepositorySql(source)
    }

    @AfterEach fun cleanup() { source.close() }

    @Test fun `mariadb replay survives retention`() {
        val id = UUID.randomUUID()
        repository.recordOnce(id, sale)
        repository.recordOnce(id, sale)
        assertEquals(1, repository.findByOwner(owner, 10, 0).size)
        repository.prune(2000)
        repository.recordOnce(id, sale)
        assertEquals(0, repository.findByOwner(owner, 10, 0).size)
    }

    @Test fun `mariadb history failure rolls back receipt`() {
        val id = UUID.randomUUID()
        source.connection.use { it.createStatement().use { statement ->
            statement.execute("CREATE TRIGGER reject_history BEFORE INSERT ON shop_transactions FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test outage'")
        } }
        assertFailsWith<java.sql.SQLException> { repository.recordOnce(id, sale) }
        source.connection.use { it.createStatement().use { statement -> statement.execute("DROP TRIGGER reject_history") } }
        repository.recordOnce(id, sale)
        assertEquals(1, repository.findByOwner(owner, 10, 0).size)
    }

    @Test fun `mariadb simultaneous replay creates one row and loser can retry`() {
        val id = UUID.randomUUID()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map { pool.submit<java.sql.SQLException?> {
                start.await()
                try { repository.recordOnce(id, sale); null } catch (failure: java.sql.SQLException) { failure }
            } }
            start.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            repository.recordOnce(id, sale)
            assertEquals(1, repository.findByOwner(owner, 10, 0).size)
        } finally { pool.shutdownNow() }
    }

    companion object {
        @Container @JvmStatic
        val database = HistoryMariaDbContainer("mariadb:11.8.3").apply {
            withDatabaseName("enthusia_market_test")
            withUsername("market_test")
            withPassword("market_test")
        }
    }

    class HistoryMariaDbContainer(image: String) : MariaDBContainer<HistoryMariaDbContainer>(image)
}
