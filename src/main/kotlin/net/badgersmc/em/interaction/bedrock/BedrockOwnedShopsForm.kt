package net.badgersmc.em.interaction.bedrock

import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import java.util.logging.Logger

class BedrockOwnedShopsForm(player: Player, private val shops: ShopRepository,
    private val management: ShopManagementService, logger: Logger, lang: LangService,
) : BedrockMenuBase(player, logger, lang) {
    override fun buildForm(): SimpleForm {
        val editable = management.shopsOwnedBy(player.uniqueId).take(45)
        val builder = SimpleForm.builder().title(lang.legacy("gui.shop.owned.title"))
        editable.forEach { builder.button("${it.stallId}: ${it.sellAmount}x / ${it.costAmount}") }
        return builder.validResultHandler { response ->
            val selected = editable.getOrNull(response.clickedButtonId()) ?: return@validResultHandler
            val current = shops.findById(selected.id) ?: return@validResultHandler
            if (management.canEdit(current, player.uniqueId) || management.canDelete(current, player.uniqueId))
                BedrockShopEditForm(player, current, shops, logger, lang, management).open(player)
        }.build()
    }
}
