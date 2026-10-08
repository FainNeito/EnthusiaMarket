package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.*
import net.badgersmc.nexus.annotations.Repository
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

@Repository
class StallAccountingRepositorySql(private val ds: DataSource) : StallAccountingRepository {
    @Synchronized override fun apply(observation: StallAccountingObservation) {
        require(observation.before >= 0 && observation.after >= 0 && observation.grossRevenue >= 0)
        require(observation.saleQuantity == 0 || observation.saleQuantity == observation.before - observation.after)
        ds.connection.use { c ->
            c.autoCommit = false
            try {
                if (!hasReceipt(c, observation.recordingId)) applyRecord(c, observation)
                c.commit()
            } catch (failure: Exception) {
                try { c.rollback() } catch (rollback: Exception) { failure.addSuppressed(rollback) }
                throw failure
            }
        }
    }

    private fun applyRecord(c: Connection, o: StallAccountingObservation) {
        update(c, "INSERT INTO guild_accounting_receipts VALUES (?)", o.recordingId.toString())
        var lots = lots(c, o)
        if (lots.sumOf { it.quantity.toLong() } != o.before.toLong()) {
            update(c, "DELETE FROM guild_stock_lots WHERE guild_id = ? AND stall_id = ? AND shop_id = ? AND item_key = ?", o.guildId, o.stallId, o.shopId, o.itemKey)
            if (o.before > 0) addLot(c, o, null, o.before)
            lots = lots(c, o)
        }
        if (o.saleQuantity > 0) applySale(c, o, lots) else applyStockChange(c, o, lots)
        update(c, "DELETE FROM guild_stock_lots WHERE remaining = 0")
    }

    private fun applySale(c: Connection, o: StallAccountingObservation, lots: List<StockLot>) {
        StallSaleAllocation.allocate(lots, o.saleQuantity, o.grossRevenue).forEach { a ->
            consume(c, a)
            update(c, "INSERT INTO guild_sale_attribution VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                o.recordingId.toString(), a.lotId.toString(), o.guildId, o.stallId, o.shopId, o.itemName,
                a.contributor?.toString(), a.quantity, a.grossRevenue, o.createdAt)
        }
    }

    private fun applyStockChange(c: Connection, o: StallAccountingObservation, lots: List<StockLot>) {
        val delta = o.after - o.before
        if (delta > 0) addLot(c, o, o.contributor, delta)
        if (delta < 0) StallSaleAllocation.allocate(lots, -delta, 0).forEach { consume(c, it) }
        if (delta != 0) update(c, "INSERT INTO guild_stock_events VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            o.recordingId.toString(), o.guildId, o.stallId, o.shopId, o.itemName, o.contributor?.toString(), delta, o.createdAt)
    }

    private fun consume(c: Connection, allocation: SaleAllocation) {
        update(c, "UPDATE guild_stock_lots SET remaining = remaining - ? WHERE lot_id = ?", allocation.quantity, allocation.lotId.toString())
    }

    private fun hasReceipt(c: Connection, id: UUID): Boolean = c.prepareStatement("SELECT recording_id FROM guild_accounting_receipts WHERE recording_id = ?").use {
        it.setString(1, id.toString()); it.executeQuery().use { rows -> rows.next() }
    }

    private fun lots(c: Connection, o: StallAccountingObservation): List<StockLot> = c.prepareStatement(
        "SELECT lot_id, contributor, remaining FROM guild_stock_lots WHERE guild_id = ? AND stall_id = ? AND shop_id = ? AND item_key = ? ORDER BY ordinal"
    ).use { statement ->
        listOf(o.guildId, o.stallId, o.shopId, o.itemKey).forEachIndexed { i, v -> statement.setObject(i + 1, v) }
        statement.executeQuery().use { rows -> buildList {
            while (rows.next()) add(StockLot(UUID.fromString(rows.getString(1)), rows.getString(2)?.let(UUID::fromString), rows.getInt(3)))
        } }
    }

    private fun addLot(c: Connection, o: StallAccountingObservation, contributor: UUID?, quantity: Int) {
        update(c, "INSERT INTO guild_stock_lots (lot_id, guild_id, stall_id, shop_id, item_key, contributor, remaining, ordinal) SELECT ?, ?, ?, ?, ?, ?, ?, COALESCE(MAX(ordinal), 0) + 1 FROM guild_stock_lots",
            UUID.randomUUID().toString(), o.guildId, o.stallId, o.shopId, o.itemKey, contributor?.toString(), quantity)
    }

    private fun update(c: Connection, sql: String, vararg values: Any?) = c.prepareStatement(sql).use { statement ->
        values.forEachIndexed { i, v -> statement.setObject(i + 1, v) }; statement.executeUpdate()
    }

    override fun report(guildId: String, stallId: String, window: ShopHistoryWindow): List<ContributorSales> = ds.connection.use { c ->
        c.prepareStatement("""SELECT contributor, item_name, SUM(stocked) AS stocked, SUM(quantity) AS quantity, SUM(revenue) AS revenue FROM (
            SELECT contributor, item_name, 0 AS stocked, quantity, gross_revenue AS revenue FROM guild_sale_attribution WHERE guild_id = ? AND stall_id = ? AND created_at >= ? AND created_at < ?
            UNION ALL
            SELECT contributor, item_name, quantity AS stocked, 0 AS quantity, 0 AS revenue FROM guild_stock_events WHERE guild_id = ? AND stall_id = ? AND created_at >= ? AND created_at < ?
            ) facts GROUP BY contributor, item_name ORDER BY revenue DESC, item_name, contributor LIMIT 100""").use { statement ->
            listOf(guildId, stallId, window.fromMs, window.toMs, guildId, stallId, window.fromMs, window.toMs).forEachIndexed { i, v -> statement.setObject(i + 1, v) }
            statement.executeQuery().use { rows -> buildList {
                while (rows.next()) add(ContributorSales(rows.getString("contributor")?.let(UUID::fromString), rows.getString("item_name"), rows.getLong("stocked"), rows.getLong("quantity"), rows.getLong("revenue")))
            } }
        }
    }
}
