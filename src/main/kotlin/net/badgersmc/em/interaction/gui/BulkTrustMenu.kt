package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.em.interaction.blockItemTheft
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Slot
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.em.interaction.Menu
import net.badgersmc.em.interaction.ShopMenuPage
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Pick which of the owner's shops to trust/untrust [targetName] on. Clicking a
 * shop icon toggles its selection; the confirm button applies via
 * [ShopManagementService]. Selection is retained by immutable ID across pages.
 */
class BulkTrustMenu(
    private val owner: UUID,
    private val target: UUID,
    private val targetName: String,
    private val management: ShopManagementService,
    private val lang: LangService,
) : Menu {

    private val selected = mutableSetOf<Long>()

    private var pageIndex = 0

    override fun open(player: Player) {
        if (!ShopMenuNavigation.allowed(player, owner)) return
        val eligible = management.shopsOwnedBy(owner).filter { management.canDelete(it, player.uniqueId) }
        selected.retainAll(eligible.map { it.id }.toSet())
        val page = ShopMenuPage.of(eligible, pageIndex) { it.id }
        pageIndex = page.index
        val gui = ChestGui(6, ComponentHolder.of(lang.msg("gui.shop.trust.title", "name" to targetName)))
        val pane = StaticPane(9, 6)
        page.items.forEachIndexed { idx, shop ->
            pane.addItem(GuiItem(icon(shop, shop.id in selected)) {
                it.isCancelled = true
                if (!ShopMenuNavigation.allowed(player, owner)) return@GuiItem
                val current = management.shopsOwnedBy(player.uniqueId).find { it.id == shop.id }
                if (current == null || !management.canDelete(current, player.uniqueId)) {
                    selected.remove(shop.id)
                    open(player)
                    return@GuiItem
                }
                if (shop.id in selected) selected.remove(shop.id) else selected.add(shop.id)
                open(player) // re-render
            }, idx % 9, idx / 9)
        }
        pane.addItem(GuiItem(named(Material.LIME_STAINED_GLASS_PANE, lang.msg("gui.shop.trust.confirm"))) {
            it.isCancelled = true
            if (!ShopMenuNavigation.allowed(player, owner)) return@GuiItem
            val n = management.trust(player.uniqueId, target, selected.toList())
            player.closeInventory()
            player.sendMessage(lang.msg("shop.cmd.trusted_all", "name" to targetName, "count" to n))
        }, 8, 5)
        ShopMenuNavigation.add(pane, page, lang) { requested ->
            pageIndex = requested
            open(player)
        }
        gui.addPane(Slot.fromXY(0, 0), pane)
        gui.blockItemTheft()
        gui.show(player)
    }

    private fun icon(shop: Shop, sel: Boolean): ItemStack {
        val base = ItemStackSerializer.deserialize(shop.sellItem) ?: ItemStack(Material.CHEST)
        val meta = base.itemMeta ?: return base
        val selection = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(lang.msg(if (sel) "gui.shop.trust.selected" else "gui.shop.trust.not_selected"))
        meta.displayName(lang.msg("gui.shop.trust.icon", "world" to shop.signWorld, "x" to shop.signX, "y" to shop.signY, "z" to shop.signZ, "sel" to selection))
        base.itemMeta = meta
        return base
    }

    private fun named(material: Material, name: Component): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(name)
        item.itemMeta = meta
        return item
    }
}
