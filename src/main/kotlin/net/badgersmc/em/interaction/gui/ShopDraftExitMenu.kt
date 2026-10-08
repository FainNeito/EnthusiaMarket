package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Slot
import net.badgersmc.em.interaction.Menu
import net.badgersmc.em.interaction.blockItemTheft
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Back navigation makes the choice explicit without treating inventory close as a save. */
class ShopDraftExitMenu(
    private val lang: LangService,
    private val onSave: () -> Unit,
    private val onDiscard: () -> Unit,
    private val onContinue: () -> Unit,
) : Menu {
    private var finished = false

    override fun open(player: Player) {
        val gui = ChestGui(3, ComponentHolder.of(lang.msg("gui.shop.edit.unsaved_title")))
        val pane = StaticPane(9, 3)
        fun choice(x: Int, material: Material, key: String, action: () -> Unit) {
            val item = ItemStack(material).apply { itemMeta = itemMeta?.apply { displayName(lang.msg(key)) } }
            pane.addItem(GuiItem(item) {
                it.isCancelled = true
                if (!finished) { finished = true; action() }
            }, x, 1)
        }
        choice(2, Material.LIME_STAINED_GLASS_PANE, "gui.shop.edit.save", onSave)
        choice(4, Material.RED_CONCRETE, "gui.shop.edit.discard", onDiscard)
        choice(6, Material.ARROW, "gui.shop.edit.continue", onContinue)
        gui.addPane(Slot.fromXY(0, 0), pane)
        gui.blockItemTheft()
        gui.show(player)
    }
}
