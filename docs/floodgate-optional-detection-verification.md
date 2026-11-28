# Optional Floodgate detection — REQ-412

## Spec and proof

Build's 2026-11-27 log reports `NoClassDefFoundError: org/geysermc/floodgate/api/FloodgateApi` from `PlatformDetectionService.isBedrockPlayer` while a player clicks a purchase sign. The plugin declares Floodgate optional, so a Java-only server must fall back to its existing Java menu.

## Engine and architecture

Catch `LinkageError` at the optional platform-detection boundary, alongside ordinary API exceptions. This keeps the existing menu-selection adapter and Bedrock behavior unchanged, while allowing the purchase-sign route to continue with Java menus when the optional API is absent.

## Refine

`PlatformDetectionServiceTest` injects the exact missing-class failure at the detection boundary and asserts the result is false; a second case proves successful Bedrock detection is preserved. The clean Java 25 `test shadowJar` run passed 1,275 tests, zero failures/errors, 17 environment skips. Java 21 Detekt and `git diff --check` passed. The shaded artifact is `build/libs/EnthusiaMarket-1.0.0.jar`, SHA-256 `8e4017f5632a75abc1ad47d372837830894c1a5fe724b2fdaf118592f76979c5`, built from local branch `fix/optional-floodgate-purchase` based on fork main `a1873dd4adc3884487036575222a570af5ff4380`.

This is source-level regression evidence; hosted review and Build activation remain separate delivery gates. The build used the staged LumaGuilds 3.0.29 runtime JAR from the existing Build deployment workspace. No production files or configuration were changed.

Project-local SPEAR EARS/state helpers are absent. Manual requirement, task and evidence records are maintained; no validator pass is claimed.
