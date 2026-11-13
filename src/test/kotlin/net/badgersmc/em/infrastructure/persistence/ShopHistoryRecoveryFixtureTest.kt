package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.ShopSaleAttribution
import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.nexus.persistence.DatabaseFactory
import net.badgersmc.nexus.persistence.DatabaseSpec
import net.badgersmc.nexus.persistence.MigrationRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ShopHistoryRecoveryFixtureTest {
    @TempDir lateinit var directory: Path

    @Test fun `v3 pending retry after committed database and lost acknowledgement remains single sale`() {
        val ds = DatabaseFactory.open(DatabaseSpec.Sqlite(directory.resolve("market.db").toFile()))
        try {
            MigrationRunner(ds, resourcePrefix = "migrations", classLoader = javaClass.classLoader).runAll()
            val owner = UUID.randomUUID()
            val tx = ShopTransaction(shopId = 42, owner = owner, buyer = UUID.randomUUID(),
                direction = SignDirection.SELL, item = "diamond", quantity = 3, totalPrice = 300, createdAt = 12345)
            val repository = ShopTransactionRepositorySql(ds)
            val outboxPath = directory.resolve("outbox")
            val outbox = ShopHistoryOutbox(outboxPath, repository, acknowledge = { throw java.io.IOException("fixture ack failure") })
            val id = outbox.appendAttributed(tx, ShopSaleAttribution("stall17", null, true))
            val pending = outboxPath.resolve("$id.pending")
            val original = Files.readAllBytes(pending)
            assertFailsWith<java.io.IOException> { outbox.drain() }
            assertTrue(Files.readAllBytes(pending).contentEquals(original))
            val firstPage = WebsiteSalesArchiveSql(ds).readAfter(0)
            assertEquals(id, firstPage.items.single().recordingId)
            ShopHistoryOutbox(outboxPath, repository).drain()
            assertEquals(firstPage, WebsiteSalesArchiveSql(ds).readAfter(0))
            assertEquals(1, repository.findByOwner(owner, 10, 0).size)
            assertTrue(Files.notExists(pending))
        } finally { (ds as? AutoCloseable)?.close() }
    }

    @Test fun `previous history insert shape preserves public archive on upgraded sqlite schema`() {
        val ds = DatabaseFactory.open(DatabaseSpec.Sqlite(directory.resolve("legacy-shape.db").toFile()))
        try {
            MigrationRunner(ds, resourcePrefix = "migrations", classLoader = javaClass.classLoader).runAll()
            val owner = UUID.randomUUID()
            val repository = ShopTransactionRepositorySql(ds)
            val sale = ShopTransaction(shopId = 42, owner = owner, buyer = UUID.randomUUID(),
                direction = SignDirection.SELL, item = "diamond", quantity = 3, totalPrice = 300, createdAt = 12345)
            val receipt = UUID.randomUUID()
            repository.recordAttributedOnce(receipt, sale, ShopSaleAttribution("stall17", null, true))
            val archive = WebsiteSalesArchiveSql(ds)
            val initialPage = archive.readAfter(0)

            // Exact column list used by the prior fork-main repository at 7177a0b4.
            // This exercises its SQL shape, not the older executable or its migration runner.
            ds.connection.use { connection ->
                connection.prepareStatement("""INSERT INTO shop_transactions
                    (shop_id, owner, buyer, direction, item, quantity, total_price, created_at, notified)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""").use { statement ->
                    statement.setLong(1, 43)
                    statement.setString(2, owner.toString())
                    statement.setString(3, UUID.randomUUID().toString())
                    statement.setString(4, "SELL")
                    statement.setString(5, "stone")
                    statement.setInt(6, 1)
                    statement.setLong(7, 5)
                    statement.setLong(8, 12346)
                    statement.setBoolean(9, false)
                    assertEquals(1, statement.executeUpdate())
                }
                connection.prepareStatement("SELECT sale_stall_id, sale_guild_id FROM shop_transactions WHERE shop_id = ?").use { statement ->
                    statement.setLong(1, 43)
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertNull(rows.getString(1))
                        assertNull(rows.getString(2))
                    }
                }
            }
            assertEquals(2, repository.findByOwner(owner, 10, 0).size)
            assertEquals(initialPage, archive.readAfter(0))
        } finally { (ds as? AutoCloseable)?.close() }
    }

    @Test fun `upgrade from pre-attribution schema keeps legacy history unattributed`() {
        val ds = DatabaseFactory.open(DatabaseSpec.Sqlite(directory.resolve("upgrade.db").toFile()))
        try {
            val runner = MigrationRunner(ds, resourcePrefix = "migrations", classLoader = javaClass.classLoader)
            runner.runAll()
            val owner = UUID.randomUUID()
            ds.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("DROP TABLE website_sales_rejections")
                    statement.execute("DROP TABLE website_public_sales")
                    statement.execute("ALTER TABLE shop_transactions DROP COLUMN sale_stall_id")
                    statement.execute("ALTER TABLE shop_transactions DROP COLUMN sale_guild_id")
                    statement.execute("DELETE FROM schema_migration WHERE version IN (36,37)")
                }
                connection.prepareStatement("""INSERT INTO shop_transactions
                    (shop_id, owner, buyer, direction, item, quantity, total_price, created_at, notified)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""").use { statement ->
                    statement.setLong(1, 42)
                    statement.setString(2, owner.toString())
                    statement.setString(3, UUID.randomUUID().toString())
                    statement.setString(4, "SELL")
                    statement.setString(5, "stone")
                    statement.setInt(6, 1)
                    statement.setLong(7, 5)
                    statement.setLong(8, 12345)
                    statement.setBoolean(9, false)
                    statement.executeUpdate()
                }
            }
            assertEquals(listOf(35, 36), runner.runAll().map { it.version })
            assertEquals(1, ShopTransactionRepositorySql(ds).findByOwner(owner, 10, 0).size)
            assertTrue(WebsiteSalesArchiveSql(ds).readAfter(0).items.isEmpty())
            ds.connection.use { connection -> connection.createStatement().use { statement ->
                statement.executeQuery("SELECT sale_stall_id, sale_guild_id FROM shop_transactions").use { rows ->
                    assertTrue(rows.next())
                    assertNull(rows.getString(1))
                    assertNull(rows.getString(2))
                }
            } }
        } finally { (ds as? AutoCloseable)?.close() }
    }
}
