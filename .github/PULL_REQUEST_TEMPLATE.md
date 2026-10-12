## What and why

<!-- One or two sentences. Link the issue: "Closes #123". -->

## Area

- [ ] Program (`programs/tally`, `crates/tally-core`)
- [ ] SDK or scripts
- [ ] Android app
- [ ] Tally cloud / MCP / Telegram
- [ ] Docs or CI

## Checklist

- [ ] CI checks pass locally (see [CONTRIBUTING.md](../CONTRIBUTING.md#run-the-checks-ci-runs))
- [ ] Every new on-chain check has a LiteSVM test asserting its exact error code, and a row in `THREAT_SUITE.md`
- [ ] Encoding changes update `fixtures/` and pass in Rust, TypeScript and Kotlin
- [ ] IDL changes regenerate the SDK (`pnpm -C sdk codegen`)
- [ ] New errors have a plain-language sentence in the SDK and the app
- [ ] A line under `[Unreleased]` in `CHANGELOG.md`
- [ ] No keys, `.env` values or recovery phrases in the diff, screenshots or logs

## Evidence

<!-- Test output, a devnet transaction signature, or a screenshot / recording for UI changes. -->
