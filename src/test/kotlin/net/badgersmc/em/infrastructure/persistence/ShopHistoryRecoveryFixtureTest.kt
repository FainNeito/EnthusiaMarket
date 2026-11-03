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
}
