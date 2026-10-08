package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.FinderOutlineOptions
import net.badgersmc.em.config.EnthusiaMarketConfig
import org.bukkit.Color
import org.bukkit.Particle

internal object FinderOutlineStyle {
    fun options(config: EnthusiaMarketConfig.FinderOutline): FinderOutlineOptions = FinderOutlineOptions(
        config.enabled, config.revealDistance, config.arrivalSeconds, config.height,
        config.spacing, config.maxParticlesPerPlayer, config.showShopMarker,
    ).normalized()

    fun dust(config: EnthusiaMarketConfig.FinderOutline): Particle.DustOptions {
        val hex = config.color.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#FFC857"
        val size = config.particleSize.takeIf { it.isFinite() }?.coerceIn(0.25, 4.0) ?: 1.0
        return Particle.DustOptions(Color.fromRGB(hex.removePrefix("#").toInt(16)), size.toFloat())
    }
}
