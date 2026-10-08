package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Slot
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.interaction.Menu
import net.badgersmc.em.interaction.blockItemTheft
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Separate confirmation; the application service rechecks the current actor at confirmation time. */
class ShopDeleteConfirmMenu(
    private val shop: Shop,
    private val repository: ShopRepository,
    private val management: ShopManagementService,
    private val lang: LangService,
    private val onCancel: () -> Unit,
) : Menu {
    private var finished = false

    override fun open(player: Player) {
        val current = repository.findById(shop.id)
        if (current == null || (!admin(player) && !management.canDelete(current, player.uniqueId))) {
            player.sendMessage(lang.msg("shop.edit.not_owner"))
            return
        }
        show(player, current)
    }

    private fun show(player: Player, current: Shop) {
        val gui = ChestGui(3, ComponentHolder.of(lang.msg("gui.shop.edit.delete_confirm_title")))
        val pane = StaticPane(9, 3)
        pane.addItem(GuiItem(preview(current)) { it.isCancelled = true }, 4, 1)
        pane.addItem(GuiItem(named(Material.LIME_CONCRETE, "gui.shop.edit.delete_keep")) {
            it.isCancelled = true
            cancel()
        }, 2, 1)
        pane.addItem(GuiItem(named(Material.RED_CONCRETE, "gui.shop.edit.delete_confirm")) {
            it.isCancelled = true
            confirm(player)
        }, 6, 1)
        gui.addPane(Slot.fromXY(0, 0), pane)
        gui.blockItemTheft()
        gui.show(player)
    }

    private fun preview(current: Shop): ItemStack {
        val item = ItemStackSerializer.deserialize(current.sellItem) ?: ItemStack(Material.BARRIER)
        item.itemMeta = item.itemMeta?.apply {
            lore(listOf(lang.msg("gui.shop.edit.delete_location", "world" to current.signWorld,
                "x" to current.signX, "y" to current.signY, "z" to current.signZ),
                lang.msg("gui.shop.edit.delete_details", "amount" to current.sellAmount, "cost" to current.costAmount)))
        }
        return item
    }

    private fun cancel() {
        if (finished) return
        finished = true
        onCancel()
    }

    private fun confirm(player: Player) {
        if (finished) return
        finished = true
        val deleted = if (admin(player)) management.adminDelete(shop.id)
            else management.delete(player.uniqueId, shop.id)
        player.closeInventory()
        player.sendMessage(lang.msg(if (deleted) "shop.delete.done" else "shop.edit.not_owner"))
    }

    private fun admin(player: Player) =
        player.hasPermission("enthusiamarket.admin") || player.hasPermission("enthusiamarket.admin.shop")

    private fun named(material: Material, key: String): ItemStack = ItemStack(material).apply {
        itemMeta = itemMeta?.apply { displayName(lang.msg(key)) }
    }
}
