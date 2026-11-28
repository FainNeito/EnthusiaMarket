package net.badgersmc.em.infrastructure.bedrock

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformDetectionServiceTest {

    @Test
    fun `missing Floodgate API falls back to Java menu detection`() {
        assertFalse(
            PlatformDetectionService().detectFloodgatePlayer {
                throw NoClassDefFoundError(
                    "org/geysermc/floodgate/api/FloodgateApi",
                )
            },
        )
    }

    @Test
    fun `available Floodgate detection result is preserved`() {
        assertTrue(PlatformDetectionService().detectFloodgatePlayer { true })
    }
}
