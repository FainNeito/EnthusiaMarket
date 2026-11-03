# Finder BUY display — 2026-11-01

## Spec and observed failure

REQ-396 follows TEST client evidence `11-search-guild-buy.jpg` from Market native
smoke 3051: a normal BUY entry was labelled In Stock and Unlimited trades even
though transaction capacity and payer funds are checked at purchase time. The
mixed BUY/SELL list used the deployed locale override Shops selling. Existing
default `gui.shop.search.title` is already neutral, so changing its bundled
default alone cannot repair the observed old override.

The authoritative parent was freshly fetched at 57704f1; fork main 2870eb9
contains that parent plus the prior accepted Market work. Worktree and branch
are isolated from both existing checkouts. Roundtable 3106 records source scope;
local JVM memory hold remains. The four new real-LangService MockBukkit GUI
regressions are authored but have not yet been executed; no red/green result is
claimed. Prior native screenshot is evidence of the original issue.

## Engine and architecture

The Java inventory adapter labels non-admin BUY entries Buying and states that
capacity and funds are checked when trading. Finite SELL/TRADE and unlimited
admin display continue using existing stock logic. Search eligibility, sorting,
filtering, navigation, persistence and transaction paths are unchanged.

`gui.shop.search.results_title` is a new separately configurable neutral key;
the old `title` value and all on-disk locale files remain untouched. Server
operators who customized the previous title should configure the new key if
they want a custom title. New BUY labels are also independently configurable.

No project-local EARS validator or SPEAR state helpers were found; this record,
REQ-396 and the task list document spec/prove/engine/arch/refine manually.

## Preview and remaining verification

[Interactive finder preview](https://enthusia-market-native-fixes-review.xxnatetheprogamerxx.chatgpt.site/finder-buy.html)
is a simulation with schematic icons. Local browser clicks checked BUY and SELL
examples; a 390-pixel viewport rendered without observed horizontal overflow.
The published owner-private link reaches a ChatGPT sign-in gate. Authenticated
mobile-device access and native Minecraft acceptance have not been verified.

Hosted CI, independent review, Bedrock coverage and TEST deployment remain
pending. Existing frozen/admin and guild-bank/XP acceptance are separate from
this display fix. FainNeito is disconnected after the weekly network restart;
Staff-session safety must be established before a client reconnection.

## Integration refresh — 2026-11-01

The original isolated change was cherry-picked onto fork main `0e0c771`, after
the auction-size merge. The documentation conflicts retained both requirements;
the original branch was preserved. The finder preview
above remains a simulation, and authenticated mobile access is unverified.

Hosted run `41362955302` ran 1,162 tests with one failure and one skip. The
custom-title assertion attempted to open an empty SELL fixture, which correctly
left the previous inventory open. The fixture now supplies one stocked item;
the original failure and corrected test must be checked on the revised head.
