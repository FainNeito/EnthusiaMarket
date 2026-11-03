# TEST client acceptance follow-up

## Spec

Continue REQ-367/368 (rent warnings), REQ-373/375 (owner settings and effect suppression), REQ-381/382 (finder), and REQ-383/384/385 (guild accounting) against merged Market main `57704f1acfaf8aacfdd65cb4ae74c0f36b55712d`, release 1.0.53. Ordinary-player permission acceptance is explicitly deferred by the user. No production deployment is authorized.

Documented `/stallaccess settings <stall>` and `/stallaccess blacklist <stall> <player> <blocked>` must parse their supplied arguments and reach the existing authorization checks. This repairs REQ-373's adapter entrypoint; it does not expand management permissions.

## Prove

The actual Java client on TEST rejected `/stallaccess settings stall24` with an incorrect-argument error. The same installed command opened the authorized guild stall's menu only when invoked as `/stallaccess settings <stall> stall24`. Nexus's actual `PaperCommandScanner` splits `@Subcommand` text into literal path segments and appends annotated method arguments separately. Including usage placeholders in that annotation created literal nodes.

`StallAccessCommandRegistrationTest` scans the real command definitions and builds a Brigadier tree with the discovered paths and actual Nexus argument resolvers. Before the fix it fails with `CommandSyntaxException` at `settings`; after the fix it must dispatch settings plus both blacklist values with exact arguments. The first test-authoring attempt had an explicit generic-type inference error; that was corrected before recording this behavioral failure.

## Engine and architecture

Remove usage placeholders from the two `@Subcommand` paths. Existing method parameters, permissions, ownership/rank validation, database fencing and GUI behavior remain unchanged. No visual implementation or schema changes are introduced. Project-local EARS/state helpers are absent; this file is the manual SPEAR requirement/task/evidence record.

## Runtime observations

- Purchased a previously vacant TEST stall24 through its registered purchase sign, choosing guild ownership. The client confirmed Enthusiast ownership and a $10 personal payment. No occupied stall was transferred.
- Access menu exposes individual allied-guild capabilities. ENTRY starts false. Enabled only ENTRY for one selected ally on this new fixture for later persistence checks. This is configuration acceptance, not proof of another player's enforcement.
- Invisibility was active outside the stall (4:32 remaining), suppressed inside the actual region, and restored outside with 2:22 remaining. No duration reset was observed. The existing default invisibility block was retained.
- Full Java 25/Paper 26.2 `test shadowJar jacocoTestReport -PuseMavenLocal=true` passes: 1,020 tests, zero failures/errors, 14 optional environment skips. Architecture checks execute. Companion is the verified official LumaGuilds 3.0.39 release (SHA-256 `3ca71cc1adc2b3b69565755d25f0a5a2241b52163e41a08b005f7c50acbb65f1`). Java 22 Detekt and `git diff --check` pass. The new actual-scanner/Brigadier regression is green.
- A fresh client reconnect failed at the proxy with an internal connection error before TEST received a join. This does not prove a Market warning failure. Hosted checks and remaining runtime results are separate. A local source fix is not an installed or production fix.

Private screenshots, player identifiers and copied database records are kept outside Git.
