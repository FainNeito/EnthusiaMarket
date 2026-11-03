package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.WebsiteSaleEligibility
import net.badgersmc.em.domain.shop.WebsiteSaleFacts
import net.badgersmc.em.domain.shop.WebsitePublicSale
import net.badgersmc.em.domain.shop.WebsitePublicSalePage
import net.badgersmc.em.domain.shop.WebsitePublicSaleReader
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

/** Public projection only. The caller owns the transaction and must supply sale-time facts. */
internal class WebsiteSalesArchiveSql(private val ds: DataSource) : WebsitePublicSaleReader {
    fun insert(connection: Connection, saleId: Long, recordingId: UUID, facts: WebsiteSaleFacts,
               item: String, guildName: String?, occurredAt: Long) {
        require(WebsiteSaleEligibility.accepts(facts)) { "Sale is not verified for public export" }
        require(saleId > 0 && facts.amount <= 1_000_000_000_000L && facts.quantity <= 1_000_000_000)
        require(facts.stallId!!.matches(Regex("[A-Za-z0-9_.:-]{1,80}")))
        require(item.matches(Regex("[a-z0-9_]{1,80}")))
        require(guildName == null || (facts.guildOwned && guildName.isNotBlank() && guildName.length <= 80))
        require(occurredAt > 0 && occurredAt <= System.currentTimeMillis() + 60_000)
        connection.prepareStatement("""INSERT INTO website_public_sales
            (sale_id,recording_id,stall_id,guild_name,item_key,quantity,raw_gold,occurred_at)
            VALUES (?,?,?,?,?,?,?,?)""".trimIndent()).use { statement ->
            statement.setLong(1, saleId); statement.setString(2, recordingId.toString())
            statement.setString(3, facts.stallId); statement.setString(4, guildName ?: "")
            statement.setString(5, item); statement.setInt(6, facts.quantity)
            statement.setLong(7, facts.amount); statement.setLong(8, occurredAt)
            statement.executeUpdate()
        }
    }

    override fun readAfter(after: Long): WebsitePublicSalePage {
        require(after >= 0)
        return ds.connection.use { connection ->
            val maximum = connection.prepareStatement("SELECT MAX(sale_id) FROM website_public_sales").use { statement ->
                statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else 0L }
            }
            require(after <= maximum) { "Cursor is beyond the retained archive" }
            connection.prepareStatement("""SELECT sale_id,stall_id,guild_name,item_key,quantity,raw_gold,occurred_at
                FROM website_public_sales WHERE sale_id>? ORDER BY sale_id ASC LIMIT 251""".trimIndent()).use { statement ->
                statement.setLong(1, after)
                statement.executeQuery().use { rows ->
                    val items = mutableListOf<WebsitePublicSale>()
                    var more = false
                    while (rows.next()) {
                        if (items.size == 250) { more = true; break }
                        items += WebsitePublicSale(rows.getLong(1), rows.getString(2), rows.getString(3),
                            rows.getString(4), rows.getInt(5), rows.getLong(6), rows.getLong(7))
                    }
                    WebsitePublicSalePage(items, if (more) items.last().id else null, items.lastOrNull()?.id ?: after)
                }
            }
        }
    }
}
