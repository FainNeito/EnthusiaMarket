package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.em.interaction.blockItemTheft
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Slot
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.em.interaction.Menu
import net.badgersmc.em.interaction.ShopMenuPage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** Stable pages with a second click to confirm deletion. */
class DeleteShopsMenu(
    private val owner: UUID,
    private val management: ShopManagementService,
    private val lang: LangService,
) : Menu {
    private val armed = mutableSetOf<Long>()
    private var pageIndex = 0

    override fun open(player: Player) {
        if (!ShopMenuNavigation.allowed(player, owner)) return
        val eligible = management.shopsOwnedBy(owner).filter { management.canDelete(it, player.uniqueId) }
        armed.retainAll(eligible.map { it.id }.toSet())
        val page = ShopMenuPage.of(eligible, pageIndex) { it.id }
        pageIndex = page.index
        val gui = ChestGui(6, ComponentHolder.of(lang.msg("gui.shop.delete.title")))
        val pane = StaticPane(9, 6)
        page.items.forEachIndexed { idx, shop ->
            val base = ItemStackSerializer.deserialize(shop.sellItem) ?: ItemStack(Material.CHEST)
            val meta = base.itemMeta
            if (meta != null) {
                val key = if (shop.id in armed) "gui.shop.delete.icon_armed" else "gui.shop.delete.icon"
                meta.displayName(lang.msg(key, "world" to shop.signWorld, "x" to shop.signX, "y" to shop.signY, "z" to shop.signZ))
                base.itemMeta = meta
            }
            pane.addItem(GuiItem(base) {
                it.isCancelled = true
                if (!ShopMenuNavigation.allowed(player, owner)) return@GuiItem
                val current = management.shopsOwnedBy(player.uniqueId).find { it.id == shop.id }
                if (current == null || !management.canDelete(current, player.uniqueId)) {
                    armed.remove(shop.id)
                    player.sendMessage(lang.msg("shop.delete.failed"))
                    open(player)
                    return@GuiItem
                }
                if (shop.id in armed) {
                    if (management.delete(player.uniqueId, shop.id)) {
                        player.sendMessage(lang.msg("shop.delete.done"))
                    } else {
                        player.sendMessage(lang.msg("shop.delete.failed"))
                    }
                    armed.remove(shop.id)
                } else {
                    armed.add(shop.id)
                }
                open(player)
            }, idx % 9, idx / 9)
        }
        ShopMenuNavigation.add(pane, page, lang) { requested ->
            pageIndex = requested
            open(player)
        }
        gui.addPane(Slot.fromXY(0, 0), pane)
        gui.blockItemTheft()
        gui.show(player)
    }
}
