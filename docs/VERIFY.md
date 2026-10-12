# Verify Tally yourself

This page is for judges and reviewers. Every claim in the README maps to something you can run, open on the explorer, or read in the code. It also says plainly what is shipped, what is research motivation, and what is not done yet.

**Short on time?** Do these three things (≈ 5 minutes):

1. Open the **[latest CI run](https://github.com/LSUDOKO/Tally/actions/workflows/ci.yml)**: every push builds the program (SBF) and runs all four test suites.
2. Open the **[program on Solana Explorer](https://explorer.solana.com/address/3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk?cluster=devnet)**: a deployed, executable upgradeable program on devnet.
3. Open two transactions from the [evidence table](#3-on-chain-evidence-what-to-look-for): one **Paid**, one **Reverted**, and read the program's log line.

---

## 1. Shipped vs. research vs. not done

| | Status | Where to check |
|---|---|---|
| Anchor program `tally` (24 instructions) on devnet | **Shipped, deployed** | [Explorer](https://explorer.solana.com/address/3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk?cluster=devnet) · [`programs/tally`](../programs/tally) |
| `pay_offer` with ten invariants (~20.4k CU) | **Shipped, tested** | [code map below](#4-code-map-the-ten-invariants) · [`tests/litesvm`](../tests/litesvm) |
| Android app (Kotlin + Compose): wallet sign-in (MWA), capsule, scan/NFC, agent, Second Look, red team, receipts, rewards, merchant mode, App lock | **Shipped**, APK [v1.2.0](https://github.com/LSUDOKO/Tally/releases/tag/v1.2.0) | [`app/`](../app) · [demo video](https://youtu.be/Bj0FDTXTpZA) |
| SKR merchant bond, trust tiers, fraud-proof slashing, disputes, bond unlock delay | **Shipped in the program**; on devnet the SKR is a **mock mint** from our faucet | [`instructions/merchant.rs`](../programs/tally/src/instructions/merchant.rs) · [`slashing.rs`](../programs/tally/src/instructions/slashing.rs) · [`dispute.rs`](../programs/tally/src/instructions/dispute.rs) |
| Seeker Genesis Token check | **Shipped**; devnet uses a **mock SGT collection** | [`sgt.rs`](../programs/tally/src/instructions/sgt.rs) |
| Tally cloud: demo counter, MCP server, Telegram bot, Helius alerts, Places | **Shipped, live** (free tier, cold start 30–50 s) | [counter](https://tally-cloud-b792.onrender.com/counter) · [`mcp/`](../mcp) |
| 73.3% steering, 140 → 0, simulation drift, gross outflow, witness freshness | **Research motivation**, not our measurements. These are other teams' published results that shaped the design. | [docs/03](03-research-foundation.md) · [docs/19](19-sources.md) |
| Security audit | **Not done.** The program has not been audited. | [Security, honestly](../README.md#security-honestly) |
| Mainnet | **Not deployed.** Devnet only. | — |
| Merchant pilots | **Not started.** No real café uses Tally yet. | [§6](#6-merchant-adoption-honest-status) |

## 2. Run the tests (expected counts)

All four suites run in CI on every push ([workflow](../.github/workflows/ci.yml)). To run them locally:

```bash
# Rust: core vectors + LiteSVM threat suite against the compiled .so   → 155 passed
cargo build-sbf --manifest-path programs/tally/Cargo.toml
cargo test --workspace

# TypeScript SDK: byte parity with Rust                                 → 19 passed
pnpm install && pnpm -C sdk test

# Cloud: MCP policy, Telegram bot, payment alerts, Places proxy         → 16 passed
pnpm -C mcp test

# Kotlin core (the app's engine): byte parity, agent, Second Look, …    → 97 passed
cd app && ./gradlew :core:test
```

Requires Rust 1.97.1 (pinned) and Solana CLI 4.0.x. Each on-chain attack in the [threat model](11-threat-model.md) has a test that asserts its **exact** error code: see the [threat suite map](../tests/litesvm/THREAT_SUITE.md).

**No devnet SOL needed:** the [local end-to-end](../README.md#local-end-to-end-no-devnet-sol-needed) runs the whole flow (honest pay, payee swap, phantom add-on, stale menu, upsell) against `solana-test-validator` with the real compiled program.

## 3. On-chain evidence: what to look for

Program: [`3ofJTv8x…djqk`](https://explorer.solana.com/address/3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk?cluster=devnet) (devnet).

| Transaction | Expected on Explorer |
|---|---|
| [Honest purchase, $4.50 oat latte](https://explorer.solana.com/tx/5X3yy6tRD3nVwSWK5vFBf3shBMN8hJDksrby8kk1PUyeykkkGZCKY73i16VJta9RfpDA2piYZz7tsFoqYtWZwcF?cluster=devnet) | **Success.** Instructions: Ed25519 precompile, then `pay_offer`. USDC moves vault → the merchant's registered payout. |
| [Payee swap](https://explorer.solana.com/tx/4eCo3iQviYdcfnjLxqyETCoWZTCqFskQX3uBieXeEeR9vNfVPVwK2wcQnEQ75As8p7Qw6TkyvmejuAtLN6XBvPCE?cluster=devnet) | **Failed, custom error 2001** (`has_one` payout constraint). No token movement. |
| [Phantom $2.99 add-on](https://explorer.solana.com/tx/4oot9a8F18zqqAFXjK6ntL7uKEh8s93LuaMMBqqb5shNwtqbUE8GUdsQpaghyAjH7sosj2wdoRm6yPDwbsxDx8NS?cluster=devnet) | **Failed, 6009 NotOnMenu**: the leaf isn't under the signed Merkle root. |
| [Stale menu replay](https://explorer.solana.com/tx/3wmMJMoqVCwAM2kgjktfyZssyCCWhYDTmLzr9o9kjP9EgkTirBhX15gFQVShuKCZCJ9amTtsCbv3rQJcijYHJkzH?cluster=devnet) | **Failed, 6007 Stale**: outside the 150-slot window. |
| [Whisper upsell, agent alone](https://explorer.solana.com/tx/8PMbmycaGYKJr17VPQCEkqyn7qaR7uapAWH6pT5oXEGG45wor122am1M7RR8tEkPiXdATcRdRSkbroHRrXcyszd?cluster=devnet) | **Failed, 6013 OwnerCosignRequired**: pricier than the cheapest match. |
| [Same upsell, owner co-signed](https://explorer.solana.com/tx/56gBBA1r2CyJZ12f9udR8Mt9NJcrw11M84R2JBCc5qehkKo33VqSL4qRKcRV8g5LDSXpk5s6js6o3AX7C7rrmMH7?cluster=devnet) | **Success** with two signers: agent + owner. |
| [Real phone: Second Look approved in Solflare](https://explorer.solana.com/tx/58ePPqG3hK7TWYzXYtsoJXn5iSaNYX2tN8R67uzRhs2TA3esUx8Nb1CQPNqoAag7bxhcTwg2fgNWKLtzBbaHEGyD?cluster=devnet) | **Success**: agent key (Android Keystore) + owner (Solflare via MWA) in one transaction. |
| [Real phone: capsule created with a Seeker Genesis Token](https://explorer.solana.com/tx/5C5pkK2Q48DkbNoPLcDBQB1vLNtBMAL1HhbEqpkCR3DAaSf2eHrM838J7Hpixk2pZqxiMfPiaTX962eE67ABrBax?cluster=devnet) | **Success**: the program reads the token's group (mock collection on devnet). |

Error codes are defined in [`programs/tally/src/errors.rs`](../programs/tally/src/errors.rs) (6000 + position); the app translates them into sentences in `ErrorText` ([`Chain.kt`](../app/core/src/main/kotlin/app/tally/core/Chain.kt)).

## 4. Code map: the ten invariants

All in [`programs/tally/src/instructions/pay_offer.rs`](../programs/tally/src/instructions/pay_offer.rs) unless noted.

| # | Invariant | Where |
|---|---|---|
| 1 | Agent signer, capsule not frozen/expired, merchant active | accounts struct (`has_one = agent`) and lines 86–88 |
| 2 | Previous instruction is the Ed25519 precompile (sysvar introspection) | `verify_prev_ed25519` in [`checks/introspection.rs`](../programs/tally/src/checks/introspection.rs), called at line ~101 |
| 3 | Offset hardening: one signature, every `*_instruction_index == u16::MAX`, message == rebuilt header | `parse_single` in [`crates/tally-core/src/ed25519.rs`](../crates/tally-core/src/ed25519.rs) (line 66); header rebuilt with `build_header` |
| 4 | Freshness ≤ 150 slots (≤ 9,000 only with owner co-sign) | lines 109–119 |
| 5 | Payout = the merchant's registered account | accounts struct: `#[account(has_one = payout)]` on `merchant` + mint constraint |
| 6 | Program hashes the leaf itself; Merkle inclusion | `leaf_hash` ([`menu.rs`](../crates/tally-core/src/menu.rs)) and line 132 |
| 7 | Category bitmap, per-purchase cap, rolling daily cap | lines 151–161 |
| 8 | Cheapest match, bond tier, long window → owner co-sign | lines 167–169 |
| 9 | Receipt PDA `init`: no double charge | accounts struct (line 64) |
| 10 | `transfer_checked`, then exact vault/payout deltas | lines 176–204 ([`post.rs`](../crates/tally-core/src/post.rs)) |

Each invariant has negative tests in [`tests/litesvm/tests`](../tests/litesvm/tests) (introspection, menu, policy, settlement, capsule, merchant, slashing, pow, sgt, compute).

## 5. Watch it uncut

- **Demo video:** <https://youtu.be/Bj0FDTXTpZA>
- **Real-device recordings** (Moto G34 + Solflare, devnet): the GIFs in the [README](../README.md#see-it) are cut only to speed up waits; nothing is staged.
- **Try it on your phone:** install [`tally-v1.2.0-devnet.apk`](https://github.com/LSUDOKO/Tally/releases/download/v1.2.0/tally-v1.2.0-devnet.apk), switch Solflare to Devnet, request test funds through the [issue template](https://github.com/LSUDOKO/Tally/issues/new?template=devnet-test-funds.yml), scan the [live counter](https://tally-cloud-b792.onrender.com/counter), and open **Red team**.

## 6. Merchant adoption: honest status

- **Today:** no real merchant uses Tally. The counter you scan is our demo café, signed by Tally cloud with a registered devnet key.
- **What exists for a merchant:** the same APK has a merchant mode (register with an SKR bond, sign the menu, show QR / NFC, Solana Pay fallback). Onboarding takes about 5 minutes on a phone they already own.
- **Who we'd pilot with first:** 5 cafés near one campus, then pop-up counters at Seeker community events ([plan](12-skr-and-merchants.md#launch-plan-post-hackathon)).
- **What we would measure:** share of agent purchases that clear autonomously vs. need Second Look, blocked attempts per 100 purchases, and repeat use (weekly active capsules).
- **Why a merchant would join:** agents will route spend to merchants they can verify; signed menus give every order an on-chain receipt, which cuts disputes; no new hardware; zero platform fee in v1.

## 7. Scope, deliberately

Tally is one mechanism, "pay only for what tallies", with the surfaces it needs to be usable: the program (the mechanism), the Android app (the owner), merchant mode (the other side of the trade), and the cloud (so agents outside the phone can propose, never pay). Rewards, alerts and the directory are small conveniences on top of the same on-chain receipts.
