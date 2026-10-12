# Contributing to Tally

Thanks for your interest. Tally is a devnet-only, unaudited project, so the bar for a change is simple: it must keep every payment check on-chain, and it must come with a test that proves it.

By taking part you agree to the [Code of Conduct](CODE_OF_CONDUCT.md). Security issues go through [SECURITY.md](SECURITY.md), never a public issue.

## Repository map

| Path | What | Language |
|---|---|---|
| `programs/tally` | Anchor program, 24 instructions | Rust |
| `crates/tally-core` | Byte-level primitives shared by the program and tests | Rust |
| `tests/litesvm` | Threat-model suite against the compiled `.so` | Rust |
| `fixtures` | Cross-language vectors every client must reproduce | JSON |
| `sdk` | TypeScript SDK (`@solana/kit` + Codama client) | TypeScript |
| `scripts` | Deploy, initialise, seed, faucet, attack replay | TypeScript |
| `mcp` | Tally cloud: MCP server, Telegram bot, alerts, demo counter | TypeScript |
| `app` | Android app, user and merchant modes in one APK | Kotlin |
| `docs` | Whitepaper, specs, threat model, research | Markdown |

## Set up

| Tool | Version |
|---|---|
| Rust | 1.97.1 (pinned in `rust-toolchain.toml`) |
| Solana CLI | 4.0.x |
| Anchor | 1.2.1 |
| Node.js | 22 |
| pnpm | 10.19.0 (pinned in `package.json`) |
| JDK | 21, with the Android SDK (platform 37, build-tools 36.0.0) |

```bash
pnpm install
cargo build-sbf --manifest-path programs/tally/Cargo.toml
```

Secrets (Groq, Helius, Google Places, Telegram) live in a gitignored `.env` and are optional. Never commit one, and never paste a key into an issue, a pull request or a screenshot.

## Run the checks CI runs

Every pull request runs these in [CI](.github/workflows/ci.yml). Run them locally first:

```bash
# Rust
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace

# TypeScript
pnpm -C sdk typecheck && pnpm -C sdk test
pnpm -C scripts typecheck
pnpm -C mcp typecheck && pnpm -C mcp test

# Android
cd app && ./gradlew :core:test :app:assembleDebug
```

[docs/VERIFY.md](docs/VERIFY.md#2-run-the-tests-expected-counts) lists the expected test counts.

## Rules for changes

- **On-chain checks stay on-chain.** The app and the cloud may explain or pre-check a payment, but the program is the only thing that decides. A change that moves a check off-chain will not be merged.
- **Every attack has a test with its exact error code.** A new check needs a negative test in `tests/litesvm` that asserts the precise `TallyError` (or Anchor) code, and a row in [THREAT_SUITE.md](tests/litesvm/THREAT_SUITE.md).
- **Bytes match across languages.** If you change a header, leaf, Merkle or QR encoding, update `fixtures/` and make the Rust, TypeScript and Kotlin tests pass against the same vectors.
- **The IDL drives the SDK.** After changing an instruction or account, rebuild the program and run `pnpm -C sdk codegen`; commit the generated client with the change.
- **Errors read as sentences.** A new error code needs a plain-language explanation in the SDK (`sdk/src/errors.ts`) and the app (`ErrorText`).
- **Keep it on devnet.** Do not add mainnet endpoints, keys or addresses.

## Commits and pull requests

- One logical change per commit. Messages use a short area prefix and the imperative mood, as in the history: `app: ...`, `program: ...`, `sdk: ...`, `cloud: ...`, `docs: ...`, `ci: ...`.
- Open the pull request against `main` and fill in the template. Link the issue it closes.
- For an app change that is visible on screen, attach a screenshot or a short recording from a device or emulator.
- Add a line under `[Unreleased]` in [CHANGELOG.md](CHANGELOG.md).

## Reporting bugs and asking for features

Use the [issue forms](https://github.com/LSUDOKO/Tally/issues/new/choose). For a failed payment, the transaction signature on devnet is the most useful thing you can include. To try Tally on your phone, request devnet test funds with the [test funds form](https://github.com/LSUDOKO/Tally/issues/new?template=devnet-test-funds.yml).

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
