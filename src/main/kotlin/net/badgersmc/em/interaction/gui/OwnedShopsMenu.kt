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
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.em.interaction.Menu
import net.badgersmc.em.interaction.ShopMenuPage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** Stable pages of editable shops; click an ID-bound snapshot to open current settings. */
class OwnedShopsMenu(
    private val owner: UUID,
    private val shopRepository: ShopRepository,
    private val management: ShopManagementService,
    private val lang: LangService,
) : Menu {
    private var pageIndex = 0

    override fun open(player: Player) {
        if (!ShopMenuNavigation.allowed(player, owner)) return
        val page = ShopMenuPage.of(management.shopsOwnedBy(owner).filter {
            management.canEdit(it, player.uniqueId) || management.canDelete(it, player.uniqueId)
        }, pageIndex) { it.id }
        pageIndex = page.index
        val gui = ChestGui(6, ComponentHolder.of(lang.msg("gui.shop.owned.title")))
        val pane = StaticPane(9, 6)
        page.items.forEachIndexed { idx, shop ->
            pane.addItem(GuiItem(icon(shop)) {
                it.isCancelled = true
                if (!ShopMenuNavigation.allowed(player, owner)) return@GuiItem
                val current = shopRepository.findById(shop.id)
                if (current == null || !(management.canEdit(current, player.uniqueId) ||
                        management.canDelete(current, player.uniqueId))) {
                    open(player)
                    return@GuiItem
                }
                ShopEditMenu(current, shopRepository, management, lang).open(player)
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

    private fun icon(shop: Shop): ItemStack {
        val base = ItemStackSerializer.deserialize(shop.sellItem) ?: ItemStack(Material.CHEST)
        val meta = base.itemMeta
        val dirLabel = ShopDisplay.directionLabel(shop.direction)
        val tradesAvailable = ShopDisplay.tradesAvailable(shop)
        val frozenLabel = if (shop.frozen)
            lang.msg("gui.shop.owned.status_frozen")
        else
            lang.msg("gui.shop.owned.status_active")
        if (meta != null) {
            meta.displayName(lang.msg("gui.shop.owned.icon", "sell_amt" to shop.sellAmount, "cost" to shop.costAmount, "world" to shop.signWorld, "x" to shop.signX, "y" to shop.signY, "z" to shop.signZ))
            meta.lore(listOf(
                lang.msg("gui.shop.owned.dir_lore", "direction" to dirLabel),
                lang.msg("gui.shop.owned.stock_lore", "trades" to tradesAvailable),
                lang.msg("gui.shop.owned.frozen_lore", "frozen" to frozenLabel),
            ))
            base.itemMeta = meta
        }
        return base
    }
}
