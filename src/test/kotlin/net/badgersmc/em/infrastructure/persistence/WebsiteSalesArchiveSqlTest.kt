package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.em.domain.shop.WebsiteSaleFacts
import net.badgersmc.nexus.persistence.DatabaseFactory
import net.badgersmc.nexus.persistence.DatabaseSpec
import net.badgersmc.nexus.persistence.MigrationRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import net.badgersmc.em.infrastructure.website.WebsiteSalesHttpServer
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebsiteSalesArchiveSqlTest {
    @TempDir lateinit var directory: Path

    @Test fun `archive accepts verified sales and preserves them independently of shop history`() {
        val ds = DatabaseFactory.open(DatabaseSpec.Sqlite(directory.resolve("market.db").toFile()))
        try {
        MigrationRunner(ds, resourcePrefix = "migrations", classLoader = javaClass.classLoader).runAll()
        val archive = WebsiteSalesArchiveSql(ds)
        val facts = WebsiteSaleFacts(SignDirection.SELL, 42, 3, "stall1", true, false, false, false, false, null)
        val receipt = UUID.randomUUID()
        ds.connection.use { connection ->
            archive.insert(connection, 7, receipt, facts, "diamond", null, 1_700_000_000_000)
        }
        val page = archive.readAfter(0)
        assertEquals(1, page.items.size)
        assertEquals(receipt, page.items.single().recordingId)
        assertEquals("stall1", page.items.single().stall)
        assertEquals(42L, page.items.single().gold)
        val port = ServerSocket(0).use { it.localPort }
        val token = "test-only-" + "a".repeat(32)
        val generation = UUID.randomUUID()
        val listener = WebsiteSalesHttpServer(port, token, { generation }, reader = archive)
        listener.start()
        try {
            val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/api/community/sales?protocol=2&after=0&generation=$generation"))
                .header("Authorization", "Bearer $token").GET().build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"stall\":\"stall1\""))
            assertTrue(response.body().contains("\"currency\":\"RAW_GOLD\""))
        } finally { listener.close() }
        ds.connection.use { it.createStatement().use { statement -> statement.executeUpdate("DELETE FROM shop_transactions") } }
        assertEquals(page, archive.readAfter(0))
        assertEquals(emptyList(), archive.readAfter(7).items)
        assertFailsWith<IllegalArgumentException> { archive.readAfter(8) }
        ds.connection.use { connection ->
            assertFailsWith<IllegalArgumentException> {
                archive.insert(connection, 8, UUID.randomUUID(), facts.copy(adminShop = null), "diamond", null, 1_700_000_000_000)
            }
        }
        ds.connection.use { connection ->
            connection.autoCommit = false
            archive.insert(connection, 9, UUID.randomUUID(), facts, "diamond", null, 1_700_000_000_000)
            connection.rollback()
        }
        assertEquals(page, archive.readAfter(0), "rolled-back records must never become public")
        } finally { (ds as? AutoCloseable)?.close() }
    }

    @Test fun `archive pages have stable exclusive cursors without identity fields`() {
        val ds = DatabaseFactory.open(DatabaseSpec.Sqlite(directory.resolve("pages.db").toFile()))
        try {
            MigrationRunner(ds, resourcePrefix = "migrations", classLoader = javaClass.classLoader).runAll()
            val archive = WebsiteSalesArchiveSql(ds)
            val facts = WebsiteSaleFacts(SignDirection.SELL, 1, 1, "stall1", true, false, false, false, false, null)
            ds.connection.use { connection ->
                connection.autoCommit = false
                for (id in 1L..251L) archive.insert(connection, id, UUID.randomUUID(), facts, "stone", null, 1_700_000_000_000)
                connection.commit()
            }
            val first = archive.readAfter(0)
            assertEquals(250, first.items.size)
            assertEquals(250L, first.nextCursor)
            val last = archive.readAfter(first.nextCursor!!)
            assertEquals(listOf(251L), last.items.map { it.id })
            assertEquals(null, last.nextCursor)
            assertEquals(setOf("id", "stall", "guild", "item", "quantity", "gold", "occurredAt", "recordingId", "awardOwner"),
                first.items.first().javaClass.declaredFields.filterNot { it.isSynthetic }.map { it.name }.toSet())
        } finally { (ds as? AutoCloseable)?.close() }
    }
}
