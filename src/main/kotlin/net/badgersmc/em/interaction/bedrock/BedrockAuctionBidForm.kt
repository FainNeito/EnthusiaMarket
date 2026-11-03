package net.badgersmc.em.interaction.bedrock

import net.badgersmc.em.interaction.AuctionBidUi
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import java.util.logging.Logger

/** Same payer/amount/review actions as Java; only presentation differs. */
internal class BedrockAuctionBidForm(player: Player, private val title: String,
    private val content: String, private val actions: List<Pair<String, () -> Unit>>,
    lang: LangService,
) : BedrockMenuBase(player, Logger.getLogger(BedrockAuctionBidForm::class.java.name), lang) {
    override fun buildForm(): SimpleForm {
        val builder = SimpleForm.builder().title(title).content(content)
        actions.forEach { builder.button(it.first) }
        return builder.validResultHandler { response ->
            actions.getOrNull(response.clickedButtonId())?.second?.let { action ->
                AuctionBidUi.onMain { if (player.isOnline) action() }
            }
        }.build()
    }
}
