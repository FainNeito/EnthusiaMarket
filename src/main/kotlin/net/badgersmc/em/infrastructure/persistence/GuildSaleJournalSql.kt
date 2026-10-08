package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.ports.GuildSaleIntent
import net.badgersmc.em.domain.ports.GuildSaleJournal
import java.util.UUID
import javax.sql.DataSource

class GuildSaleJournalSql(private val ds: DataSource) : GuildSaleJournal {
    override fun prepare(sale: GuildSaleIntent) {
        execute("INSERT INTO guild_sale_xp_journal (id, guild_id, buyer_id, shop_id, occurred_at, state) VALUES (?, ?, ?, ?, ?, 'PREPARED')",
            sale.id.toString(), sale.guild.toString(), sale.buyer.toString(), sale.shopId, sale.occurredAt)
    }

    override fun completed(id: UUID) {
        check(execute("UPDATE guild_sale_xp_journal SET state = 'COMPLETED' WHERE id = ? AND state = 'PREPARED'", id.toString()) == 1)
    }

    override fun aborted(id: UUID) {
        execute("UPDATE guild_sale_xp_journal SET state = 'ABORTED' WHERE id = ? AND state = 'PREPARED'", id.toString())
    }

    override fun acknowledged(id: UUID, outcome: String) {
        check(execute("UPDATE guild_sale_xp_journal SET state = 'ACKNOWLEDGED', outcome = ? WHERE id = ? AND state = 'COMPLETED'", outcome, id.toString()) == 1)
    }

    override fun pending(limit: Int): List<GuildSaleIntent> {
        require(limit in 1..1000)
        return ds.connection.use { c ->
            c.prepareStatement("SELECT * FROM guild_sale_xp_journal WHERE state = 'COMPLETED' ORDER BY occurred_at, id LIMIT ?").use { s ->
                s.setInt(1, limit)
                s.executeQuery().use { r ->
                    buildList {
                        while (r.next()) add(GuildSaleIntent(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("guild_id")),
                            UUID.fromString(r.getString("buyer_id")), r.getLong("shop_id"), r.getLong("occurred_at")))
                    }
                }
            }
        }
    }

    private fun execute(sql: String, vararg args: Any?): Int = ds.connection.use { c ->
        c.prepareStatement(sql).use { s ->
            args.forEachIndexed { i, value -> s.setObject(i + 1, value) }
            s.executeUpdate()
        }
    }
}
