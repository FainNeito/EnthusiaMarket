package net.badgersmc.em.interaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StallAccessUiTest {
    @AfterTest fun reset() { StallAccessUi.reset() }

    @Test fun `safe localized player response retains full diagnostic failure`() {
        val language = mockk<LangService>()
        val logger = mockk<Logger>(relaxed = true)
        val failure = IllegalStateException("database internal revision detail")
        every { language.raw("stall_access.error.setting_failed") } returns "Could not confirm; reopen settings."
        StallAccessUi.configure(language, logger)
        val response = StallAccessUi.failure("menu stall=test actor=fixture", failure)
        assertEquals("Could not confirm; reopen settings.", response)
        assertFalse(response.contains(failure.message!!))
        verify(exactly = 1) { logger.log(Level.WARNING, "Stall setting could not be confirmed: menu stall=test actor=fixture", failure) }
    }

    @Test fun `missing locale key falls back safely and reset drops prior binding`() {
        val language = mockk<LangService>()
        every { language.raw(any()) } answers { firstArg<String>() }
        StallAccessUi.configure(language, mockk(relaxed = true))
        assertEquals(StallAccessLabels().state(true), StallAccessUi.labels.state(true))
        StallAccessUi.reset()
        assertEquals("Allowed", StallAccessUi.labels.state(true))
    }
}
