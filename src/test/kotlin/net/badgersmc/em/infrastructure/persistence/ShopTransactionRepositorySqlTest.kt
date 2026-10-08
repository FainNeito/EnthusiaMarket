package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.nexus.persistence.DatabaseFactory
import net.badgersmc.nexus.persistence.DatabaseSpec
import net.badgersmc.nexus.persistence.MigrationRunner
import org.mockbukkit.mockbukkit.MockBukkit
import java.io.File
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ShopTransactionRepositorySqlTest {

    private val dbFile = File.createTempFile("em-shop-tx", ".db")
    private lateinit var ds: DataSource

    @BeforeTest fun setup() {
        MockBukkit.mock()
        ds = DatabaseFactory.open(DatabaseSpec.Sqlite(dbFile)).also {
            MigrationRunner(it, resourcePrefix = "migrations", classLoader = javaClass.classLoader).runAll()
        }
    }

    @AfterTest fun teardown() { MockBukkit.unmock(); dbFile.delete() }

    private fun tx(owner: UUID, createdAt: Long, notified: Boolean = false) = ShopTransaction(
        shopId = 1, owner = owner, buyer = UUID.randomUUID(), direction = SignDirection.SELL,
        item = "diamond", quantity = 5, totalPrice = 100, createdAt = createdAt, notified = notified,
    )

    @Test fun `record then find by owner newest-first`() {
        val repo = ShopTransactionRepositorySql(ds)
        val owner = UUID.randomUUID()
        repo.record(tx(owner, createdAt = 1_000))
        repo.record(tx(owner, createdAt = 2_000))
        val rows = repo.findByOwner(owner, limit = 10, offset = 0)
        assertEquals(2, rows.size)
        assertEquals(2_000, rows.first().createdAt) // newest first
    }

    @Test fun `receipt prevents replay even after history pruning`() {
        val repo = ShopTransactionRepositorySql(ds)
        val owner = UUID.randomUUID()
        val id = UUID.randomUUID()
        val sale = tx(owner, 1000)
        repo.recordOnce(id, sale)
        repo.recordOnce(id, sale)
        assertEquals(1, repo.findByOwner(owner, 10, 0).size)
        repo.prune(2000)
        repo.recordOnce(id, sale)
        assertEquals(0, repo.findByOwner(owner, 10, 0).size)
    }

    @Test fun `failed history insert rolls back its receipt`() {
        val repo = ShopTransactionRepositorySql(ds)
        val id = UUID.randomUUID()
        val owner = UUID.randomUUID()
        ds.connection.use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TRIGGER reject_history BEFORE INSERT ON shop_transactions BEGIN SELECT RAISE(ABORT, 'test outage'); END")
            }
        }
        kotlin.test.assertFailsWith<java.sql.SQLException> { repo.recordOnce(id, tx(owner, 1000)) }
        ds.connection.use { connection -> connection.createStatement().use { it.execute("DROP TRIGGER reject_history") } }
        repo.recordOnce(id, tx(owner, 1000))
        assertEquals(1, repo.findByOwner(owner, 10, 0).size)
    }

    @Test fun `countUnnotified and markNotified`() {
        val repo = ShopTransactionRepositorySql(ds)
        val owner = UUID.randomUUID()
        repo.record(tx(owner, createdAt = 1_000))
        repo.record(tx(owner, createdAt = 2_000))
        assertEquals(2, repo.countUnnotified(owner))
        repo.markNotified(owner)
        assertEquals(0, repo.countUnnotified(owner))
    }

    @Test fun `prune deletes only older rows`() {
        val repo = ShopTransactionRepositorySql(ds)
        val owner = UUID.randomUUID()
        repo.record(tx(owner, createdAt = 1_000))
        repo.record(tx(owner, createdAt = 5_000))
        assertEquals(1, repo.prune(beforeMs = 2_000))
        assertEquals(1, repo.findByOwner(owner, 10, 0).size)
    }

    @Test fun `summary acknowledgement excludes later sales and other owners`() {
        val repo = ShopTransactionRepositorySql(ds)
        val owner = UUID.randomUUID()
        val other = UUID.randomUUID()
        repo.record(tx(owner, createdAt = 1_000))
        val last = repo.record(tx(owner, createdAt = 1_000))
        val summary = repo.pendingSales(owner)
        assertEquals(2, summary.count)
        assertEquals(last.id, summary.lastId)
        repo.record(tx(owner, createdAt = 1_000))
        repo.record(tx(other, createdAt = 1_000))
        repo.markNotifiedThrough(owner, summary.lastId)
        assertEquals(1, repo.countUnnotified(owner))
        assertEquals(1, repo.countUnnotified(other))
        repo.markNotifiedThrough(owner, summary.lastId)
        assertEquals(1, repo.countUnnotified(owner))
    }
}
