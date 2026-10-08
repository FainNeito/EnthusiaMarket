package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.application.GuildSaleRewardService
import net.badgersmc.em.domain.ports.GuildShopXpGateway
import net.badgersmc.nexus.persistence.DatabaseFactory
import net.badgersmc.nexus.persistence.DatabaseSpec
import net.badgersmc.nexus.persistence.MigrationRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GuildSaleRewardDeliveryTest {
    @TempDir lateinit var directory: Path
    private lateinit var ds: DataSource
    private lateinit var journal: GuildSaleJournalSql
    private val guild = UUID.randomUUID()
    private val buyer = UUID.randomUUID()
    private val failures = mutableListOf<UUID?>()
    private val gateway = ReceiptGateway()

    @BeforeEach fun setup() {
        ds = DatabaseFactory.open(DatabaseSpec.Sqlite(directory.resolve("sales.db").toFile()))
        MigrationRunner(ds, resourcePrefix = "migrations", classLoader = javaClass.classLoader).runAll()
        journal = GuildSaleJournalSql(ds)
    }

    @AfterEach fun close() { (ds as? AutoCloseable)?.close() }

    private fun service() = GuildSaleRewardService(journal, gateway, { id, _ -> failures.add(id) }, { 1_791_374_400_000L })

    @Test fun `only completed purchases replay after restart`() {
        val service = service()
        val completed = service.prepare(guild, buyer, 1)
        service.completed(completed)
        service.prepare(guild, buyer, 2) // crash left preparation ambiguous
        val aborted = service.prepare(guild, buyer, 3)
        service.aborted(aborted)
        service().replay()
        assertEquals(setOf(completed), gateway.receipts)
        assertEquals(emptyList(), journal.pending(100))
    }

    @Test fun `transient provider failure retains ordered queue and retry delivers once`() {
        val service = service()
        val id = service.prepare(guild, buyer, 1)
        service.completed(id)
        gateway.fail = true
        service.replay()
        assertEquals(listOf(id), journal.pending(100).map { it.id })
        gateway.fail = false
        service().replay()
        service().replay()
        assertEquals(setOf(id), gateway.receipts)
        assertEquals(listOf<UUID?>(id), failures)
    }

    @Test fun `lost acknowledgement replays same durable ID rather than new award`() {
        val service = service()
        val id = service.prepare(guild, buyer, 1)
        service.completed(id)
        ds.connection.use { c -> c.createStatement().use { it.executeUpdate("CREATE TRIGGER reject_ack BEFORE UPDATE ON guild_sale_xp_journal WHEN NEW.state = 'ACKNOWLEDGED' BEGIN SELECT RAISE(ABORT, 'test fault'); END") } }
        service.replay()
        assertEquals(setOf(id), gateway.receipts)
        assertEquals(1, journal.pending(100).size)
        ds.connection.use { c -> c.createStatement().use { it.executeUpdate("DROP TRIGGER reject_ack") } }
        service().replay()
        assertEquals(setOf(id), gateway.receipts)
        assertEquals(emptyList(), journal.pending(100))
    }

    @Test fun `preparation failure cannot enter the completed replay queue`() {
        gateway.fail = true
        assertFails { service().prepare(guild, buyer, 1) }
        assertEquals(emptyList(), journal.pending(100))
    }

    @Test fun `completion journal write failure stays ambiguous and never auto awards`() {
        val service = service()
        val id = service.prepare(guild, buyer, 1)
        ds.connection.use { c -> c.createStatement().use { it.executeUpdate("CREATE TRIGGER reject_complete BEFORE UPDATE ON guild_sale_xp_journal BEGIN SELECT RAISE(ABORT, 'test fault'); END") } }
        service.completed(id)
        service.replay()
        assertEquals(emptySet(), gateway.receipts)
        assertEquals(listOf<UUID?>(id), failures)
    }

    private class ReceiptGateway : GuildShopXpGateway {
        var fail = false
        val receipts = mutableSetOf<UUID>()
        override fun prepare(id: UUID, guild: UUID, buyer: UUID, occurredAt: Long): String {
            check(!fail)
            return "PREPARED"
        }
        override fun complete(id: UUID): String {
            check(!fail)
            receipts.add(id)
            return "AWARDED:5"
        }
    }
}
