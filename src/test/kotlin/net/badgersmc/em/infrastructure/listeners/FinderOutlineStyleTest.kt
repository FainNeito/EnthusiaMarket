package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.config.EnthusiaMarketConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class FinderOutlineStyleTest {
    @Test fun `configured color and size are used while malformed settings use safe bounds`() {
        val config = EnthusiaMarketConfig.FinderOutline()
        config.color = "#00FF00"
        config.particleSize = 2.0
        assertEquals(0x00FF00, FinderOutlineStyle.dust(config).color.asRGB())
        assertEquals(2f, FinderOutlineStyle.dust(config).size)
        config.color = "not a color"
        config.particleSize = Double.NaN
        assertEquals(0xFFC857, FinderOutlineStyle.dust(config).color.asRGB())
        assertEquals(1f, FinderOutlineStyle.dust(config).size)
        config.particleSize = -100.0
        assertEquals(0.25f, FinderOutlineStyle.dust(config).size)
    }
}
