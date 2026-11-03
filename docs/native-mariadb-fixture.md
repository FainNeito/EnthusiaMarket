# Disposable native MariaDB validation

The three MariaDB container suites can also use one explicitly configured,
Market-owned native MariaDB instance. This profile changes fixture startup only;
the 11 test bodies, assertions, connection pools and internal race threads are
the same as the Docker profile.

## Selection and ownership

With all three variables absent, the default remains a MariaDB 11.8.3
Testcontainer per class, using the existing database names and test credentials.
Unavailable Docker disables those classes. Container startup failures fail.

Any present variable selects native mode, including a blank value. All three
must then be valid; partial configuration, invalid values, authentication errors,
missing grants and unreachable servers fail instead of skipping or using Docker.

| Variable | Required native value |
| --- | --- |
| `MARKET_TEST_MARIADB_PORT` | Decimal port in 1024–65535, excluding 3306 |
| `MARKET_TEST_MARIADB_USER` | Nonblank isolated fixture account |
| `MARKET_TEST_MARIADB_PASSWORD` | Nonblank password for that account |

The helper constructs only `jdbc:mariadb://127.0.0.1:<port>/`; it accepts no host,
JDBC URL or database name. The coordinator must first verify the owned process,
fresh datadir, loopback bind, MariaDB 11.8.3 version and InnoDB default engine.
Do not point this profile at shared TEST or another project's instance.

`MarketMariaDbFixture` creates a unique schema for each class:
`em_fixture_moderation_<uuid>`, `em_fixture_history_<uuid>` and
`em_fixture_history_filter_<uuid>`. The fixture account needs create/drop and
table, index, alter, trigger and DML privileges only within the `em_fixture_`
test-schema prefix. The coordinator owns account provisioning and the native
server lifecycle. The helper owns only schemas it successfully creates, and
drops them after each class, including failed setup/test runs. Existing schemas
are never adopted: creation deliberately omits `IF NOT EXISTS`.

Setup errors retain their original exception if cleanup also fails; the cleanup
error is suppressed onto it. A standalone cleanup error fails the run. Cleanup
is attempted once; a reported cleanup failure or abrupt process termination can
leave a schema behind. The outer runner must inventory and remove only this
run's remaining fixture schemas before stopping its instance.

## Run and evidence

Use the existing Java 25 build environment and immutable `LUMAGUILDS_JAR`. Run
from an isolated checkout/output directory, keeping class/method execution
serial while retaining the test bodies' two-thread races. On Windows use
`gradlew.bat` with these arguments:

```sh
./gradlew test \
  --tests 'net.badgersmc.em.infrastructure.moderation.JdbcMarketModerationMariaDbTest' \
  --tests 'net.badgersmc.em.infrastructure.persistence.ShopHistoryMariaDbTest' \
  --tests 'net.badgersmc.em.infrastructure.persistence.ShopHistoryFilterMariaDbTest' \
  --offline --no-daemon --rerun-tasks --no-build-cache
```

Require the three JUnit reports to contain **7, 3 and 1 cases**, respectively,
with zero failures, errors and skips. Record source revision/tree, command,
Java/database binary identity, owned instance identity, companion hash and XML.
Native configuration disables test up-to-date reuse and build-cache reuse;
credentials are not declared as cache-key inputs.

The two existing native suites remain unchanged and can share this owned
instance when separately configured:

| Suite | Configuration and behavior |
| --- | --- |
| `GuildSaleJournalMariaDbTest` | `GUILD_SHOP_XP_TEST_MARIA_PORT`; precreated `market_shop_xp_test`, user/password `xp_test`; drops and recreates its journal table. |
| `StallAccountingMariaDbTest` | `MARKET_ACCOUNTING_TEST_MARIA_PORT`, `MARKET_ACCOUNTING_TEST_MARIA_USER`, `MARKET_ACCOUNTING_TEST_MARIA_PASSWORD`; creates and finally drops a random `em_accounting_` schema. |

Set both existing port variables to the same owned port and add their fully
qualified class selectors under `net.badgersmc.em.infrastructure.persistence`
for **13 cases total: 7 + 3 + 1 + 1 + 1**. Their users/grants must be provisioned
for their distinct schemas. Never reuse the Guilds project's instance merely
because the journal port variable contains `GUILD_SHOP_XP`.

These contracts cover native SQL migrations, moderation concurrency, history
receipts/filtering, contributor accounting and the reward journal. They do not
establish live payment/stock behavior, real companion XP delivery, or native
MariaDB coverage of the separate SQLite `CHAPTER_FROZEN` delivery regressions.
Docker startup and hosted non-skip gates remain separate validation results.
