# Staff auction lot import

Put a UTF-8 CSV in `plugins/EnthusiaMarket/auction-lots/`. Enter the exact registered stall IDs and whole raw-gold minimums. Each file accepts 1–50 unique stalls. The server's configured bid floor and duration bounds apply.

```csv
stall_id,min_bid
stall01,10
stall02,40
```

1. Run `/em auction preview opening.csv new-market-opening PT24H` as staff or from the server console.
2. Review every stall ID, world, region, minimum, and the shared round ID and duration. Use the same round ID for any follow-up lots that should share the guild opening-round win limit.
3. Click the suggested command or copy it, then submit `/em auction confirm <token>` from the same account or console within five minutes. Start a new preview to review an edited CSV.
4. Check the receipt for the number of lots, round ID and scheduled close. The timer starts at confirmation. Individual auctions can still extend under the configured anti-snipe rules.

The preview has no auction or stall writes. Confirmation revalidates the reviewed plan and opens all rows in one database transaction. A changed or unavailable stall stops the batch. An unsettled auction remains a conflict even after its scheduled close. Both commands require `enthusiamarket.admin`.

## SPEAR evidence

- REQ-402 requires staff to review exact imported stall IDs, minimum bids and opening round before committing the complete batch.
- The existing bulk command takes one price and skips conflicts. The new workflow has its own preview and confirmation; the existing command retains its behavior.
- The preview holds one five-minute, actor-scoped snapshot and confirmation consumes it. A guarded SQL transaction writes the lots and changes stall state together. The application publishes normal stall state change events only after commit.
- Verification covers 50 distinct prices, malformed CSV, actor and expiry checks, and a conflict on the last lot rolling back the whole batch.
