package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallAccessSettings
import net.badgersmc.em.domain.stall.StallAccessSettingsRepository
import net.badgersmc.em.domain.stall.StallCapability
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

class StallAccessSettingsSql(private val ds: DataSource) : StallAccessSettingsRepository {
    override fun all(): List<StallAccessSettings> = ds.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM stall_access_settings").use { statement ->
            statement.executeQuery().use { result -> buildList { while (result.next()) add(decode(result)) } }
        }
    }

    override fun save(settings: StallAccessSettings, expected: Stall): StallAccessSettings = ds.connection.use { connection ->
        connection.autoCommit = false
        try {
            lockOwnership(connection, settings, expected)
            val prior = find(connection, settings.stallId)
            if (prior?.ownershipKey == settings.ownershipKey) check(prior.revision == settings.revision) { "Settings changed; reopen the menu" }
            else check(settings.revision == 0L) { "Ownership changed; reopen the menu" }
            val updated = settings.copy(revision = Math.addExact(settings.revision, 1))
            write(connection, updated)
            connection.commit()
            updated
        } catch (failure: Exception) {
            runCatching { connection.rollback() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun lockOwnership(connection: Connection, settings: StallAccessSettings, expected: Stall) {
        check(settings.stallId == expected.id.value && settings.ownershipKey == StallAccessSettings.ownershipKey(expected))
        connection.prepareStatement("UPDATE stalls SET moderation_revision = moderation_revision WHERE id = ? AND moderation_revision = ?").use {
            it.setString(1, settings.stallId); it.setLong(2, expected.moderationRevision)
            // MariaDB may report zero changed rows for the lock-only update; validate the row separately.
            it.executeUpdate()
        }
        connection.prepareStatement("SELECT owner_type, owner_id, owner_since, state, moderation_revision FROM stalls WHERE id = ?").use {
            it.setString(1, settings.stallId)
            it.executeQuery().use { row ->
                check(row.next()) { "Stall missing" }
                val since = row.getLong("owner_since").takeIf { !row.wasNull() }
                val key = "${row.getString("owner_type")}:${row.getString("owner_id")}:$since"
                check(key == settings.ownershipKey && row.getLong("moderation_revision") == expected.moderationRevision) { "Stall ownership changed" }
                check(row.getString("state") in setOf("OWNED", "GRACE")) { "Stall is not actively owned" }
            }
        }
        connection.prepareStatement("SELECT stall_id FROM market_moderation_locks WHERE stall_id = ?").use {
            it.setString(1, settings.stallId)
            it.executeQuery().use { row -> check(!row.next()) { "Stall is reserved for moderation" } }
        }
    }

    private fun find(connection: Connection, id: String): StallAccessSettings? =
        connection.prepareStatement("SELECT * FROM stall_access_settings WHERE stall_id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { row -> if (row.next()) decode(row) else null }
        }

    private fun write(connection: Connection, settings: StallAccessSettings) {
        connection.prepareStatement("DELETE FROM stall_access_settings WHERE stall_id = ?").use {
            it.setString(1, settings.stallId); it.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO stall_access_settings (stall_id, ownership_key, visitor_flags, blacklist, blocked_effects, allow_potions, allies, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?)").use {
            it.setString(1, settings.stallId); it.setString(2, settings.ownershipKey)
            it.setString(3, settings.visitorFlags.entries.sortedBy { flag -> flag.key.name }.joinToString(",") { flag -> "${flag.key.name}=${flag.value}" })
            it.setString(4, settings.blacklist.map(UUID::toString).sorted().joinToString(","))
            it.setString(5, settings.blockedEffects.sorted().joinToString(",")); it.setInt(6, if (settings.allowIncomingPotions) 1 else 0)
            it.setString(7, settings.allies.toSortedMap().entries.joinToString(";") { ally ->
                "${ally.key}=${ally.value.map(StallCapability::name).sorted().joinToString(",")}" })
            it.setLong(8, settings.revision); check(it.executeUpdate() == 1)
        }
    }

    private fun decode(row: ResultSet) = StallAccessSettings(
        row.getString("stall_id"), row.getString("ownership_key"),
        split(row.getString("visitor_flags")).associate { entry ->
            val parts = entry.split('=', limit = 2)
            StallCapability.valueOf(parts[0]) to parts[1].toBooleanStrict()
        },
        split(row.getString("blacklist")).map(UUID::fromString).toSet(),
        split(row.getString("blocked_effects")).toSet(), row.getInt("allow_potions") == 1,
        row.getString("allies").split(';').filter(String::isNotEmpty).associate { entry ->
            val parts = entry.split('=', limit = 2)
            UUID.fromString(parts[0]).toString() to split(parts[1]).map(StallCapability::valueOf).toSet()
        },
        row.getLong("revision"),
    )

    private fun split(value: String): List<String> = value.split(',').filter(String::isNotEmpty)
}
