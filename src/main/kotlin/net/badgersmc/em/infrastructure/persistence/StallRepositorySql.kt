package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.stall.*
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class StallRepositorySql(private val ds: DataSource) : StallRepository {

    override fun findById(id: StallId): Stall? = queryOne("SELECT * FROM stalls WHERE id = ?") {
        setString(1, id.value)
    }

    override fun findByIds(ids: Collection<StallId>): Map<StallId, Stall> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        return queryMany("SELECT * FROM stalls WHERE id IN ($placeholders)") {
            ids.forEachIndexed { i, id -> setString(i + 1, id.value) }
        }.associateBy { it.id }
    }

    override fun findByRegion(world: String, regionId: String): Stall? =
        queryOne("SELECT * FROM stalls WHERE world = ? AND region_id = ?") {
            setString(1, world); setString(2, regionId)
        }

    override fun all(): List<Stall> = queryMany("SELECT * FROM stalls") {}

    override fun byState(state: StallState): List<Stall> =
        queryMany("SELECT * FROM stalls WHERE state = ?") { setString(1, state.name) }

    override fun create(stall: Stall) {
        ds.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO stalls
                   (id, region_id, world, state, owner_type, owner_id, owner_since,
                    winning_bid, rent_mode, rent_pct, rent_flat, members, max_members,
                    next_rent_at, kind, extra_entities, extra_total, moderation_revision)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                bind(ps, stall)
                ps.executeUpdate()
            }
        }
    }

    override fun deleteIfUnowned(id: StallId): StallRepository.DeleteResult = StallDeletionSql.delete(ds, id)

    override fun save(stall: Stall) {
        ds.connection.use { conn -> conn.inMarketTransaction { save(conn, stall) } }
    }

    override fun savePendingSellback(stall: Stall) {
        require(stall.state == StallState.UNOWNED)
        ds.connection.use { updateRow(it, stall) }
    }

    override fun finishSellbackAccess(previous: Stall) {
        ds.connection.use { conn ->
            conn.prepareStatement("DELETE FROM stall_access_settings WHERE stall_id = ? AND ownership_key = ? AND EXISTS (SELECT 1 FROM stalls WHERE id = ? AND state = 'UNOWNED')").use {
                it.setString(1, previous.id.value)
                it.setString(2, StallAccessSettings.ownershipKey(previous))
                it.setString(3, previous.id.value)
                it.executeUpdate()
            }
        }
    }

    override fun saveOpeningRoundGuildAward(stall: Stall, roundId: String) {
        ds.connection.use { conn ->
            conn.inMarketTransaction { saveGuildAward(conn, stall, roundId) }
        }
    }

    internal fun saveGuildAward(conn: Connection, stall: Stall, roundId: String) {
        require(stall.owner.type == OwnerType.GUILD && stall.state == StallState.OWNED)
        conn.prepareStatement(
            "INSERT INTO guild_auction_round_wins (round_id, guild_id, stall_id) VALUES (?, ?, ?)"
        ).use { ps ->
            ps.setString(1, roundId)
            ps.setString(2, stall.owner.id)
            ps.setString(3, stall.id.value)
            check(ps.executeUpdate() == 1)
        }
        save(conn, stall)
    }

    override fun savePaidExtension(stall: Stall, payment: RentPayment) {
        ds.connection.use { conn ->
            conn.inMarketTransaction {
                save(conn, stall)
                conn.recordRentPayment(stall, payment)
            }
        }
    }

    private fun bindStallFields(ps: PreparedStatement, stall: Stall, offset: Int) {
        ps.setString(offset + 1, stall.regionId)
        ps.setString(offset + 2, stall.world)
        ps.setString(offset + 3, stall.state.name)
        ps.setString(offset + 4, stall.owner.type.name)
        ps.setString(offset + 5, stall.owner.id)
        ps.setInstant(offset + 6, stall.ownerSince)
        ps.setLong(offset + 7, stall.winningBid)
        ps.setString(offset + 8, stall.rentTerms.mode.name)
        ps.setDouble(offset + 9, stall.rentTerms.pct)
        ps.setLong(offset + 10, stall.rentTerms.flatAmount)
        ps.setString(offset + 11, StallSqlCodec.encodeMembers(stall.members))
        ps.setInt(offset + 12, stall.maxMembers)
        ps.setInstant(offset + 13, stall.nextRentAt)
        ps.setString(offset + 14, stall.kind)
        ps.setString(offset + 15, StallSqlCodec.encodeExtraEntities(stall.extraEntities))
        ps.setInt(offset + 16, stall.extraTotal)
    }

    internal fun save(conn: Connection, stall: Stall) {
        updateRow(conn, stall)
        if (stall.state == StallState.MODERATION_HOLD) return
        val keepCurrent = stall.state == StallState.OWNED || stall.state == StallState.GRACE
        val predicate = if (keepCurrent) " AND ownership_key <> ?" else ""
        conn.prepareStatement("DELETE FROM stall_access_settings WHERE stall_id = ?" + predicate).use {
            it.setString(1, stall.id.value)
            if (keepCurrent) it.setString(2, StallAccessSettings.ownershipKey(stall))
            it.executeUpdate()
        }
    }

    private fun updateRow(conn: Connection, stall: Stall) {
        conn.prepareStatement(
            """UPDATE stalls SET
                region_id = ?, world = ?, state = ?, owner_type = ?, owner_id = ?,
                owner_since = ?, winning_bid = ?, rent_mode = ?, rent_pct = ?, rent_flat = ?,
                members = ?, max_members = ?, next_rent_at = ?, kind = ?,
                extra_entities = ?, extra_total = ?
               WHERE id = ? AND moderation_revision = ?
                 AND NOT EXISTS (
                     SELECT 1 FROM market_moderation_locks WHERE stall_id = ?
                 )"""
        ).use { ps ->
            bindStallFields(ps, stall, offset = 0)
            ps.setString(17, stall.id.value)
            ps.setLong(18, stall.moderationRevision)
            ps.setString(19, stall.id.value)
            if (ps.executeUpdate() != 1) {
                throw MarketModerationConflictException(
                    "Stall ${stall.id.value} changed or is reserved for moderation"
                )
            }
        }
    }

    private fun bind(ps: PreparedStatement, stall: Stall) {
        ps.setString(1, stall.id.value)
        bindStallFields(ps, stall, offset = 1)
        ps.setLong(18, stall.moderationRevision)
    }

    private fun queryOne(sql: String, prep: PreparedStatement.() -> Unit): Stall? {
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.prep()
                ps.executeQuery().use { rs ->
                    return if (rs.next()) mapRow(rs) else null
                }
            }
        }
    }

    private fun queryMany(sql: String, prep: PreparedStatement.() -> Unit): List<Stall> {
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.prep()
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<Stall>()
                    while (rs.next()) out.add(mapRow(rs))
                    return out
                }
            }
        }
    }

    private fun mapRow(rs: ResultSet): Stall {
        val ownerType = OwnerType.valueOf(rs.getString("owner_type"))
        val ownerId = rs.getString("owner_id")
        val owner = if (ownerType == OwnerType.NONE) OwnerRef.unowned() else OwnerRef(ownerType, ownerId)
        val ownerSinceMs = rs.getLong("owner_since").takeIf { !rs.wasNull() }
        val rentMode = RentTerms.Mode.valueOf(rs.getString("rent_mode"))
        val rentTerms = RentTerms(rentMode, rs.getDouble("rent_pct"), rs.getLong("rent_flat"))
        return Stall(
            id = StallId(rs.getString("id")),
            regionId = rs.getString("region_id"),
            world = rs.getString("world"),
            state = StallState.valueOf(rs.getString("state")),
            owner = owner,
            ownerSince = ownerSinceMs?.let { Instant.ofEpochMilli(it) },
            winningBid = rs.getLong("winning_bid"),
            rentTerms = rentTerms,
            members = StallSqlCodec.decodeMembers(rs.getString("members")),
            maxMembers = rs.getInt("max_members"),
            nextRentAt = rs.getLong("next_rent_at").takeIf { !rs.wasNull() }?.let { Instant.ofEpochMilli(it) },
            kind = rs.getString("kind"),
            extraEntities = StallSqlCodec.decodeExtraEntities(rs.getString("extra_entities")),
            extraTotal = rs.getInt("extra_total"),
            moderationRevision = rs.getLong("moderation_revision"),
        )
    }
}

private object StallDeletionSql {
    fun delete(ds: DataSource, id: StallId): StallRepository.DeleteResult = try {
        ds.connection.use { conn -> conn.inMarketTransaction { deleteUnowned(conn, id) } }
    } catch (failure: java.sql.SQLException) {
        if (failure.sqlState?.startsWith("23") == true) StallRepository.DeleteResult.REFERENCED else throw failure
    }

    private fun deleteUnowned(conn: Connection, id: StallId): StallRepository.DeleteResult {
        val state = conn.prepareStatement("SELECT state FROM stalls WHERE id = ?").use { ps ->
            ps.setString(1, id.value)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        } ?: return StallRepository.DeleteResult.NOT_FOUND
        if (state != StallState.UNOWNED.name) return StallRepository.DeleteResult.NOT_UNOWNED
        if (hasStallReferences(conn, id)) return StallRepository.DeleteResult.REFERENCED
        val removed = conn.prepareStatement("DELETE FROM stalls WHERE id = ? AND state = ? AND NOT EXISTS (SELECT 1 FROM market_moderation_locks WHERE stall_id = ?)").use { ps ->
            ps.setString(1, id.value)
            ps.setString(2, StallState.UNOWNED.name)
            ps.setString(3, id.value)
            ps.executeUpdate()
        }
        return if (removed == 1) StallRepository.DeleteResult.DELETED else StallRepository.DeleteResult.NOT_UNOWNED
    }

    private fun hasStallReferences(conn: Connection, id: StallId): Boolean = STALL_REFERENCE_QUERIES.any { sql ->
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeQuery().use { it.next() }
        }
    }
}

/** Database encodings kept separate from repository orchestration. */
private object StallSqlCodec {
    fun encodeMembers(members: Set<UUID>): String = members.joinToString(",") { it.toString() }

    fun decodeMembers(raw: String?): Set<UUID> {
        if (raw.isNullOrEmpty()) return emptySet()
        return raw.split(',').asSequence().filter { it.isNotBlank() }.map { UUID.fromString(it.trim()) }.toSet()
    }

    fun encodeExtraEntities(map: Map<String, Int>): String =
        if (map.isEmpty()) "" else map.entries.joinToString(",") { "${it.key}:${it.value}" }

    fun decodeExtraEntities(raw: String?): Map<String, Int> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split(',').filter { it.isNotBlank() }.associate { entry ->
            val parts = entry.trim().split(':', limit = 2)
            parts[0] to (parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0)
        }
    }
}

private const val INSERT_RENT_PAYMENT = """INSERT INTO rent_ledger
    (stall_id, charged_at, amount, paid, payer_type, payer_id, covered_from, covered_until)
    VALUES (?, ?, ?, 1, ?, ?, ?, ?)"""

private val STALL_REFERENCE_QUERIES = listOf(
    "SELECT 1 FROM auctions WHERE stall_id = ?",
    "SELECT 1 FROM signs WHERE stall_id = ?",
    "SELECT 1 FROM sales_ledger WHERE stall_id = ?",
    "SELECT 1 FROM rent_ledger WHERE stall_id = ?",
    "SELECT 1 FROM grace_events WHERE stall_id = ?",
    "SELECT 1 FROM favorites WHERE stall_id = ?",
    "SELECT 1 FROM sell_offers WHERE stall_id = ?",
    "SELECT 1 FROM purchase_signs WHERE stall_id = ?",
    "SELECT 1 FROM market_moderation_operations WHERE stall_id = ?",
    "SELECT 1 FROM market_moderation_locks WHERE stall_id = ?",
    "SELECT 1 FROM stall_access_settings WHERE stall_id = ?",
    "SELECT 1 FROM guild_auction_round_wins WHERE stall_id = ?",
)

private fun PreparedStatement.setInstant(index: Int, value: java.time.Instant?) {
    if (value == null) setNull(index, java.sql.Types.INTEGER) else setLong(index, value.toEpochMilli())
}

private fun Connection.recordRentPayment(stall: Stall, payment: RentPayment) {
    prepareStatement(INSERT_RENT_PAYMENT).use { ps ->
        ps.setString(1, stall.id.value)
        ps.setLong(2, payment.paidAt.toEpochMilli())
        ps.setLong(3, payment.amount)
        ps.setString(4, payment.payer.type.name)
        ps.setString(5, payment.payer.id)
        ps.setLong(6, payment.coveredFrom.toEpochMilli())
        ps.setLong(7, requireNotNull(stall.nextRentAt).toEpochMilli())
        check(ps.executeUpdate() == 1)
    }
}
