package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.em.interaction.ShopMenuPage
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** Common footer leaves slot 53 available for bulk confirmation. */
internal object ShopMenuNavigation {
    fun allowed(player: Player, owner: UUID = player.uniqueId): Boolean {
        if (player.uniqueId == owner && player.hasPermission("enthusiamarket.shop.use")) return true
        player.closeInventory()
        return false
    }

    fun add(pane: StaticPane, page: ShopMenuPage<*>, lang: LangService, navigate: (Int) -> Unit) {
        fun button(x: Int, material: Material, key: String, action: () -> Unit = {}) {
            val item = ItemStack(material)
            item.itemMeta = item.itemMeta?.apply {
                displayName(lang.msg(key, "page" to page.index + 1, "pages" to page.pages, "count" to page.count))
            }
            pane.addItem(GuiItem(item) { event ->
                event.isCancelled = true
                action()
            }, x, 5)
        }
        if (page.index > 0) button(0, Material.ARROW, "gui.shop.search.prev") { navigate(page.index - 1) }
        button(4, Material.PAPER, "gui.shop.search.page")
        if (page.index + 1 < page.pages) button(6, Material.ARROW, "gui.shop.search.next") { navigate(page.index + 1) }
    }
}
