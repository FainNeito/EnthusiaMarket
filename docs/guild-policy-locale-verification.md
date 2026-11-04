# Guild policy notification fix

The production screenshot shows `guildpolicy.announce.tariff` in chat. Inspection of the current canonical main and the official 1.0.52 locale found that the three announcement and two entry-warning keys are absent. The existing listener unit test mocks localization by deliberately echoing keys, so it does not prove those messages render.

The regression exercises the real `LangService` with an existing partial server locale, checks tariff, embargo and clear announcements and both entry warnings, and verifies that an existing override remains in effect. Test and hosted CI results must be recorded separately. A real client and the server's actual locale/configuration remain separate acceptance checks.

No project-local EARS validator or SPEAR state helper was identified in this checkout. No production deployment or mutation is part of this fix.
