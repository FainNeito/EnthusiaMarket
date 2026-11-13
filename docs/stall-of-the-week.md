# Stall of the Week

The website price tracker consumes authenticated, generation-fenced Market sales. The current export covers eligible solo shop sales paid in raw gold. A recorded sales total cannot establish complete coverage for a weekly award.

Market provides `%enthusiamarket_stall_of_the_week%` when PlaceholderAPI is loaded. The expansion refreshes the public website award once a minute off the server thread. The award applies to the last closed Monday–Sunday week in America/New_York. During incomplete coverage the placeholder reads `Awaiting verified sales`. A feed error shows `Results unavailable`.

On TEST, set `ENTHUSIA_MARKET_STALL_WEEK_URL` to the rehearsal website's HTTPS
`/api/community/insights?view=stall-week` endpoint before starting Market. Stand
at the intended position and run:

```text
/hologram create text market_week
/hologram edit market_week setline 1 <gold><bold>Stall of the Week
/hologram edit market_week addline <yellow>%enthusiamarket_stall_of_the_week%
/hologram edit market_week updatetextinterval 30s
/papi parse me %enthusiamarket_stall_of_the_week%
```

Verify the label after the first refresh and across a week boundary. The official result stays pending until coverage can be proved. Delete a temporary TEST display with `/hologram remove market_week`.
