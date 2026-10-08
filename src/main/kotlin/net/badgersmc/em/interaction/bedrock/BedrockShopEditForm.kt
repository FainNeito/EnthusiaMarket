package net.badgersmc.em.interaction.bedrock

import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.response.CustomFormResponse
import java.util.logging.Logger

/**
 * Bedrock Cumulus CustomForm for editing a shop's settings (REQ-023).
 * Allows toggling freeze, hopper input, and hopper output via toggles.
 * Index 0 = label, index 1+ = toggles.
 */
class BedrockShopEditForm(
    player: Player,
    private val shop: Shop,
    private val shopRepository: ShopRepository,
    logger: Logger,
    lang: LangService,
    private val management: net.badgersmc.em.application.ShopManagementService? = null,
) : BedrockMenuBase(player, logger, lang) {

    override fun buildForm(): CustomForm {
        return CustomForm.builder()
            .title(lang.legacy("bedrock.edit.title"))
            .label(lang.legacy("bedrock.edit.label"))
            .toggle(lang.legacy("bedrock.edit.toggle_freeze"), shop.frozen)
            .toggle(lang.legacy("bedrock.edit.toggle_hopper_in"), shop.hopperAllowIn)
            .toggle(lang.legacy("bedrock.edit.toggle_hopper_out"), shop.hopperAllowOut)
            .input(lang.legacy("gui.shop.edit.sell_amount", "amount" to shop.sellAmount), "1", shop.sellAmount.toString())
            .input(lang.legacy("gui.shop.edit.cost", "cost" to shop.costAmount), "1", shop.costAmount.toString())
            .validResultHandler { response: CustomFormResponse ->
                val frozen = response.asToggle(1) // index 1 = first toggle
                val hopperIn = response.asToggle(2)
                val hopperOut = response.asToggle(3)
                val quantity = response.asInput(4)?.toIntOrNull()?.takeIf { it > 0 } ?: return@validResultHandler
                val price = response.asInput(5)?.toIntOrNull()?.takeIf { it > 0 } ?: return@validResultHandler

                val updated = shop.copy(
                    frozen = frozen,
                    hopperAllowIn = hopperIn,
                    hopperAllowOut = hopperOut,
                    sellAmount = quantity,
                    costAmount = price,
                )
                val authorised = management?.saveEdits(player.uniqueId, updated, player.hasPermission("enthusiamarket.admin.shop"))
                    ?: (player.uniqueId == shop.owner && shopRepository.findById(shop.id)?.owner == player.uniqueId).also {
                        if (it) shopRepository.upsert(updated)
                    }
                if (!authorised) {
                    player.sendMessage(lang.legacy("shop.edit.not_owner"))
                    return@validResultHandler
                }
                player.sendMessage(lang.legacy("shop.edit.updated"))
            }
            .build()
    }
}
