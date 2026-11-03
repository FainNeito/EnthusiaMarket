# History default-page command registration

## Spec and prove

REQ-394 preserves the advertised optional page for `/shop history`, `all`,
`today` and a complete `range`. During the remaining-work audit, these handlers
still used Kotlin default arguments, like the previously corrected trust route.
The existing registration test checked scanner paths without executing them.
Canonical main was freshly fetched as57704f1; the isolated ongoing source was
clean615fff9 before the evidence reconciliation and new regression.

The new regression invokes the actual pinned Nexus-built Brigadier tree. Before
the adapter fix, four tests ran: one failed with four independently aggregated
unknown-command failures, one for each omitted-page form. Explicit pages,
permission rejection, missing range arguments and excess arguments passed.
No live history failure or historical pre-patch test run is claimed.

## Engine and architecture

Four explicit overloads delegate to their existing reader with page one. Their
page-taking counterparts retain explicit argument registration without Kotlin
defaults. The same permission annotation applies to every route. Date parsing,
timezone, owner-or-buyer scope, pagination bounds, messages, schema and persistence
are unchanged. The console guard remains in the existing reader.

## Refine and delivery

The local Java25 focused suite passed 15 tests, zero failures/errors/skips:
four registrar, five direct history, four date-window and two help-contract cases.
Exact-head canonical checks and native acceptance of the new
artifact are separate gates. TEST remains on the previous verified934279 build
until a separately coordinated deployment. No production operation or merge.
Project-local EARS/state helpers are absent; this record and the requirement/task
entries track SPEAR manually. This command-only correction changes no GUI layout,
controls or visual assets; the existing menu simulations remain applicable.
