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
    private var occurredAt = 1_791_374_400_000L

    @BeforeEach fun setup() {
        ds = DatabaseFactory.open(DatabaseSpec.Sqlite(directory.resolve("sales.db").toFile()))
        MigrationRunner(ds, resourcePrefix = "migrations", classLoader = javaClass.classLoader).runAll()
        journal = GuildSaleJournalSql(ds)
    }

    @AfterEach fun close() { (ds as? AutoCloseable)?.close() }

    private fun service() = GuildSaleRewardService(journal, gateway, { id, _ -> failures.add(id) }, { occurredAt++ })

    @Test fun `only completed purchases replay after restart`() {
        val service = service()
        val completed = service.prepare(guild, buyer, 1)
        service.completed(completed)
        service.prepare(guild, buyer, 2) // crash left preparation ambiguous
        val aborted = service.prepare(guild, buyer, 3)
        service.aborted(aborted)
        service().replay()
        assertEquals(setOf(aborted), gateway.aborts)
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
        ds.connection.use { c ->
            c.createStatement().use {
                it.executeUpdate("CREATE TRIGGER reject_ack BEFORE UPDATE ON guild_sale_xp_journal WHEN NEW.state = 'ACKNOWLEDGED' BEGIN SELECT RAISE(ABORT, 'test fault'); END")
            }
        }
        service.replay()
        assertEquals(setOf(id), gateway.receipts)
        assertEquals(5, gateway.awardedXp)
        assertEquals(1, journal.pending(100).size)
        ds.connection.use { c -> c.createStatement().use { it.executeUpdate("DROP TRIGGER reject_ack") } }
        service().replay()
        assertEquals(setOf(id), gateway.receipts)
        assertEquals(listOf(id, id), gateway.completionCalls)
        assertEquals(5, gateway.awardedXp)
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
        ds.connection.use { c ->
            c.createStatement().use {
                it.executeUpdate("CREATE TRIGGER reject_complete BEFORE UPDATE ON guild_sale_xp_journal BEGIN SELECT RAISE(ABORT, 'test fault'); END")
            }
        }
        service.completed(id)
        service.replay()
        assertEquals(emptySet(), gateway.receipts)
        assertEquals(listOf<UUID?>(id), failures)
    }

    @Test fun `abort provider failure stays retryable and does not terminalize journal`() {
        val service = service()
        val id = service.prepare(guild, buyer, 1)
        gateway.failAbort = true
        service.aborted(id)
        assertEquals("PREPARED", journalState(id))
        assertEquals(listOf<UUID?>(id), failures)
        assertEquals(emptySet(), gateway.aborts)

        gateway.failAbort = false
        service.aborted(id)
        assertEquals("ABORTED", journalState(id))
        assertEquals(setOf(id), gateway.aborts)
    }

    @Test fun `ABORTED completion outcome is terminal and never awards XP`() {
        val service = service()
        val id = service.prepare(guild, buyer, 1)
        service.completed(id)
        gateway.completionOutcome = "ABORTED"
        service.replay()
        assertEquals(emptySet(), gateway.receipts)
        assertEquals(emptyList(), journal.pending(100))
        assertEquals("ACKNOWLEDGED", journalState(id))
    }

    @Test fun `frozen chapter receipt is acknowledged and does not block following award`() {
        val service = service()
        val frozen = service.prepare(guild, buyer, 1)
        service.completed(frozen)
        val following = service.prepare(guild, buyer, 2)
        service.completed(following)
        gateway.completionOutcomes[frozen] = "CHAPTER_FROZEN"

        service.replay()

        assertEquals("ACKNOWLEDGED" to "CHAPTER_FROZEN", journalRecord(frozen))
        assertEquals("ACKNOWLEDGED" to "AWARDED:5", journalRecord(following))
        assertEquals(listOf(frozen, following), gateway.completionCalls)
        assertEquals(setOf(following), gateway.receipts)
        assertEquals(5, gateway.awardedXp)
        assertEquals(emptyList(), journal.pending(100))
        assertEquals(emptyList(), failures)

        service().replay()
        assertEquals(listOf(frozen, following), gateway.completionCalls)
        assertEquals(5, gateway.awardedXp)
    }

    @Test fun `unknown completion outcome preserves ordered queue across retries`() {
        val service = service()
        val unknown = service.prepare(guild, buyer, 1)
        service.completed(unknown)
        val following = service.prepare(guild, buyer, 2)
        service.completed(following)
        gateway.completionOutcomes[unknown] = "UNSUPPORTED_OUTCOME"

        service.replay()
        service().replay()

        assertEquals(listOf(unknown, following), journal.pending(100).map { it.id })
        assertEquals("COMPLETED" to null, journalRecord(unknown))
        assertEquals("COMPLETED" to null, journalRecord(following))
        assertEquals(listOf(unknown, unknown), gateway.completionCalls)
        assertEquals(listOf<UUID?>(unknown, unknown), failures)
        assertEquals(emptySet(), gateway.receipts)
        assertEquals(0, gateway.awardedXp)
    }

    @Test fun `lost frozen acknowledgement retries same receipt before following award`() {
        val service = service()
        val frozen = service.prepare(guild, buyer, 1)
        service.completed(frozen)
        val following = service.prepare(guild, buyer, 2)
        service.completed(following)
        gateway.completionOutcomes[frozen] = "CHAPTER_FROZEN"
        ds.connection.use { connection ->
            connection.createStatement().use {
                it.executeUpdate("CREATE TRIGGER reject_frozen_ack BEFORE UPDATE ON guild_sale_xp_journal WHEN NEW.state = 'ACKNOWLEDGED' AND NEW.id = '$frozen' BEGIN SELECT RAISE(ABORT, 'test fault'); END")
            }
        }

        service.replay()
        assertEquals(listOf(frozen, following), journal.pending(100).map { it.id })
        assertEquals(listOf(frozen), gateway.completionCalls)
        assertEquals(emptySet(), gateway.receipts)
        assertEquals(0, gateway.awardedXp)
        assertEquals(listOf<UUID?>(frozen), failures)

        ds.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("DROP TRIGGER reject_frozen_ack") }
        }
        service().replay()
        service().replay()

        assertEquals("ACKNOWLEDGED" to "CHAPTER_FROZEN", journalRecord(frozen))
        assertEquals("ACKNOWLEDGED" to "AWARDED:5", journalRecord(following))
        assertEquals(listOf(frozen, frozen, following), gateway.completionCalls)
        assertEquals(setOf(following), gateway.receipts)
        assertEquals(5, gateway.awardedXp)
        assertEquals(emptyList(), journal.pending(100))
        assertEquals(listOf<UUID?>(frozen), failures)
    }

    @Test fun `abort journal write failure remains retryable with the same companion id`() {
        val service = service()
        val id = service.prepare(guild, buyer, 1)
        ds.connection.use { c ->
            c.createStatement().use {
                it.executeUpdate("CREATE TRIGGER reject_abort BEFORE UPDATE ON guild_sale_xp_journal WHEN NEW.state = 'ABORTED' BEGIN SELECT RAISE(ABORT, 'test fault'); END")
            }
        }
        service.aborted(id)
        assertEquals("PREPARED", journalState(id))
        assertEquals(setOf(id), gateway.aborts)
        assertEquals(listOf<UUID?>(id), failures)

        ds.connection.use { c -> c.createStatement().use { it.executeUpdate("DROP TRIGGER reject_abort") } }
        service.aborted(id)
        assertEquals("ABORTED", journalState(id))
        assertEquals(setOf(id), gateway.aborts)
    }

    private fun journalState(id: UUID): String = journalRecord(id).first

    private fun journalRecord(id: UUID): Pair<String, String?> = ds.connection.use { connection ->
        connection.prepareStatement("SELECT state, outcome FROM guild_sale_xp_journal WHERE id = ?").use { statement ->
            statement.setString(1, id.toString())
            statement.executeQuery().use { rows ->
                check(rows.next()) { "Missing journal row for $id" }
                rows.getString("state") to rows.getString("outcome")
            }
        }
    }

    private class ReceiptGateway : GuildShopXpGateway {
        var fail = false
        var failAbort = false
        var completionOutcome = "AWARDED:5"
        val completionOutcomes = mutableMapOf<UUID, String>()
        val completionCalls = mutableListOf<UUID>()
        val receipts = mutableSetOf<UUID>()
        val aborts = mutableSetOf<UUID>()
        var awardedXp = 0
            private set

        override fun prepare(id: UUID, guild: UUID, buyer: UUID, occurredAt: Long): String {
            check(!fail)
            return "PREPARED"
        }

        override fun abort(id: UUID): String {
            check(!failAbort)
            aborts.add(id)
            return "ABORTED"
        }

        override fun complete(id: UUID): String {
            check(!fail)
            completionCalls.add(id)
            val outcome = completionOutcomes[id] ?: completionOutcome
            // Model the companion's durable receipt contract; retries reuse the sale ID.
            if (outcome.startsWith("AWARDED:") && receipts.add(id)) {
                awardedXp += outcome.substringAfter(':').toInt()
            }
            return outcome
        }
    }
}
