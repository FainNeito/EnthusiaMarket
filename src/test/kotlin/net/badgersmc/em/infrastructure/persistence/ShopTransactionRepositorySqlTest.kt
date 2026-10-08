package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopHistoryWindow
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

    @Test fun `window covers owner and buyer but never outsiders or end boundary`() {
        val repo = ShopTransactionRepositorySql(ds)
        val viewer = UUID.randomUUID()
        repo.record(tx(viewer, 999))
        val start = repo.record(tx(viewer, 1000))
        val bought = repo.record(tx(UUID.randomUUID(), 1500).copy(buyer = viewer))
        repo.record(tx(UUID.randomUUID(), 1600))
        repo.record(tx(viewer, 2000))
        val rows = repo.findByOwnerOrBuyer(viewer, 10, 0, ShopHistoryWindow(1000, 2000))
        assertEquals(listOf(bought.id, start.id), rows.map { it.id })
        assertEquals(3, repo.countUnnotified(viewer))
        assertEquals(4, repo.findByOwnerOrBuyer(viewer, 10, 0).size)
    }

    @Test fun `filter happens before paging and ties use stable newest ID`() {
        val repo = ShopTransactionRepositorySql(ds)
        val viewer = UUID.randomUUID()
        val matching = (1..4).map { repo.record(tx(viewer, 1500)) }.reversed()
        repeat(12) { repo.record(tx(viewer, 3000)) }
        val window = ShopHistoryWindow(1000, 2000)
        val first = repo.findByOwnerOrBuyer(viewer, 2, 0, window)
        val second = repo.findByOwnerOrBuyer(viewer, 2, 2, window)
        assertEquals(matching.map { it.id }, (first + second).map { it.id })
        assertEquals(emptyList(), repo.findByOwnerOrBuyer(viewer, 2, 4, window))
    }

    @Test fun `pruned rows are absent from both all and range`() {
        val repo = ShopTransactionRepositorySql(ds)
        val viewer = UUID.randomUUID()
        repo.record(tx(viewer, 1000))
        repo.prune(2000)
        assertEquals(emptyList(), repo.findByOwnerOrBuyer(viewer, 10, 0))
        assertEquals(emptyList(), repo.findByOwnerOrBuyer(viewer, 10, 0, ShopHistoryWindow(0, 2000)))
    }
}
