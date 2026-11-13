package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopSaleAttribution
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

    @Test fun `historical sale attribution survives persistence`() {
        val repo = ShopTransactionRepositorySql(ds)
        val owner = UUID.randomUUID()
        val attributed = tx(owner, 1000)
        val guildId = UUID.randomUUID()
        val receipt = UUID.randomUUID()
        repo.recordAttributedOnce(receipt, attributed, ShopSaleAttribution("stall17", guildId))
        repo.recordAttributedOnce(receipt, attributed, ShopSaleAttribution("stall17", guildId))
        ds.connection.use { connection ->
            connection.prepareStatement("SELECT sale_stall_id,sale_guild_id FROM shop_transactions").use { query ->
                query.executeQuery().use { rows ->
                    assertEquals(true, rows.next())
                    assertEquals("stall17", rows.getString("sale_stall_id"))
                    assertEquals(guildId.toString(), rows.getString("sale_guild_id"))
                    assertEquals(false, rows.next())
                }
            }
        }
    }

    @Test fun `verified public sale is archived atomically and survives history pruning`() {
        val repo = ShopTransactionRepositorySql(ds)
        val sale = tx(UUID.randomUUID(), System.currentTimeMillis())
        val receipt = UUID.randomUUID()
        val attribution = ShopSaleAttribution("stall17", null, publicCashEligible = true)
        repo.recordAttributedOnce(receipt, sale, attribution)
        repo.recordAttributedOnce(receipt, sale, attribution)
        val archive = WebsiteSalesArchiveSql(ds)
        val initial = archive.readAfter(0)
        assertEquals(1, initial.items.size)
        assertEquals("stall17", initial.items.single().stall)
        assertEquals(sale.totalPrice, initial.items.single().gold)
        repo.prune(sale.createdAt + 1)
        assertEquals(initial, archive.readAfter(0))
    }

    @Test fun `invalid public attribution is recorded for review without losing history`() {
        val repo = ShopTransactionRepositorySql(ds)
        val owner = UUID.randomUUID()
        val sale = tx(owner, System.currentTimeMillis()).copy(buyer = owner)
        val receipt = UUID.randomUUID()
        repo.recordAttributedOnce(receipt, sale, ShopSaleAttribution("stall17", null, publicCashEligible = true))
        assertEquals(1, repo.findByOwner(owner, 10, 0).size)
        assertEquals(emptyList(), WebsiteSalesArchiveSql(ds).readAfter(0).items)
        ds.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM shop_history_receipts WHERE recording_id=?").use { query ->
                query.setString(1, receipt.toString())
                query.executeQuery().use { rows -> rows.next(); assertEquals(1, rows.getInt(1)) }
            }
            connection.prepareStatement("SELECT COUNT(*) FROM website_sales_rejections WHERE recording_id=?").use { query ->
                query.setString(1, receipt.toString())
                query.executeQuery().use { rows -> rows.next(); assertEquals(1, rows.getInt(1)) }
            }
        }
    }

    @Test fun `guild award attribution survives outbox retry`() {
        val repo = ShopTransactionRepositorySql(ds)
        val sale = tx(UUID.randomUUID(), System.currentTimeMillis())
        val guild = UUID.randomUUID()
        val owner = net.badgersmc.em.domain.shop.SaleAwardOwner(guild, "Test Guild", UUID.randomUUID())
        val directory = java.nio.file.Files.createTempDirectory("guild-award-outbox")
        val outbox = ShopHistoryOutbox(directory, repo, acknowledge = {})
        val receipt = outbox.appendAttributed(sale, ShopSaleAttribution("stall17", guild, true, owner))
        outbox.drain()
        outbox.drain()
        val archived = WebsiteSalesArchiveSql(ds).readAfter(0).items.single()
        assertEquals(receipt, archived.recordingId)
        assertEquals(owner, archived.awardOwner)
        assertEquals("Test Guild", archived.guild)
        java.nio.file.Files.delete(directory.resolve("$receipt.pending"))
        java.nio.file.Files.delete(directory)
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
