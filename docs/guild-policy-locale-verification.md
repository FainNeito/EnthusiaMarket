# Guild policy notification fix

The production screenshot shows `guildpolicy.announce.tariff` in chat. Inspection of the current canonical main and the official 1.0.52 locale found that the three announcement and two entry-warning keys are absent. The existing listener unit test mocks localization by deliberately echoing keys, so it does not prove those messages render.

The regression exercises the real `LangService` with an existing partial server locale, checks tariff, embargo and clear announcements and both entry warnings, and verifies that an existing override remains in effect. Test and hosted CI results must be recorded separately. A real client and the server's actual locale/configuration remain separate acceptance checks.

The notification presentation follows the user's message-theme selection: the Market label uses the soft copper gradient `#E4AB65` to `#F2B66D`, sentences use cream `#FFF6E0`, names and rates use gold `#FFCF1F`, and embargo emphasis uses the selected crimson `#C0261D`. This applies only to the five new notification strings. The preview is a simulation and does not establish Minecraft client rendering.

No project-local EARS validator or SPEAR state helper was identified in this checkout. No production deployment or mutation is part of this fix.
