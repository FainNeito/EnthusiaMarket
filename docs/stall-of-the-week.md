# Stall of the Week

The website price tracker consumes authenticated, generation-fenced Market sales. The current export covers eligible solo shop sales paid in raw gold. A recorded sales total cannot establish complete coverage for a weekly award.

Market provides `%enthusiamarket_stall_of_the_week%` when PlaceholderAPI is loaded. The expansion refreshes the public website award once a minute off the server thread. The award applies to the last closed Monday–Sunday week in America/New_York. During incomplete coverage the placeholder reads `Awaiting verified sales`. A feed error shows `Results unavailable`.

On TEST, set `ENTHUSIA_MARKET_STALL_WEEK_URL` to the rehearsal website's HTTPS
`/api/community/insights?view=stall-week` endpoint before starting Market.
For an Access-protected rehearsal website, also set
`ENTHUSIA_MARKET_STALL_WEEK_ACCESS_CLIENT_ID` and
`ENTHUSIA_MARKET_STALL_WEEK_ACCESS_CLIENT_SECRET` from a service token
authorized for that endpoint. Configure both values together.
To exercise sales as well, enable `shop.archiveCaptureEnabled` and
`website-sales.yml` separately. Configure the TEST raw-gold currency, sales
token, and generation file as described in `website-sales.yml`. Bind the
rehearsal worker to the matching TEST generation and its own database.
Buy a vanilla item from another player's solo SELL shop and compare the
tracker's trade count before and after the poll. A repeat poll should leave
the count unchanged.

Stand at the intended hologram position and run:

```text
/hologram create text market_week
/hologram edit market_week setline 1 <gold><bold>Stall of the Week
/hologram edit market_week addline <yellow>%enthusiamarket_stall_of_the_week%
/hologram edit market_week updatetextinterval 30s
/papi parse me %enthusiamarket_stall_of_the_week%
```

Verify the label after the first refresh and across a week boundary. The official result stays pending until coverage can be proved. Delete a temporary TEST display with `/hologram remove market_week`.

For paired player and guild holograms and NPCs, see [Weekly stall displays](weekly-stall-displays.md). The guild placeholder is `%enthusiamarket_guild_stall_of_the_week%`.
