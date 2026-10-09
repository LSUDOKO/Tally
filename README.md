<div align="center">

# Tally

**Your agent proposes. The merchant signs. Tally checks it on-chain.**

An AI agent buys coffee, lunch and transit for you from a Solana Seeker phone, but it can only pay for an item a merchant signed, at the signed price, to the merchant's registered account, inside the rules you signed in Seed Vault. If the agent is talked into anything else, the Solana program reverts the transaction.

*Spending limits protect your balance. Tally protects your decision.*

[![CI](https://github.com/LSUDOKO/Tally/actions/workflows/ci.yml/badge.svg)](https://github.com/LSUDOKO/Tally/actions/workflows/ci.yml)
![Solana](https://img.shields.io/badge/Solana-Anchor%201.2-9945FF)
![Android](https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-3DDC84)
![License](https://img.shields.io/badge/license-MIT-blue)

Built for **CLOCK IN, the Solana Mobile Hackathon** (Mobile track)

[Whitepaper](docs/00-whitepaper.md) · [Threat model](docs/11-threat-model.md) · [Test suite map](tests/litesvm/THREAT_SUITE.md) · [Demo script](docs/14-demo-script.md)

</div>

---

## The problem

AI agents can now pay on our behalf, and today they're protected with spending caps and allowlists. 2026 research shows that the real attack is not stealing a large amount. It's **one sentence of merchant text talking the agent into the wrong item**: a pricier listing (steered **73.3%** of the time in *Signing the Transaction but Not the Decision*, arXiv 2609.11757), an add-on nobody showed, or a different payee. Those carts are well-formed, under the cap and signed by an authorised key, so every perimeter check passes.

Caps answer *how much*. Allowlists answer *to whom*. **Tally answers *which item, at which signed price, from which registered merchant*, and asks you when the agent picks the pricier one.**

## How it works

```mermaid
flowchart LR
    subgraph Seeker["Seeker phone (user)"]
      SV[Seed Vault<br/>owner key] -- MWA 2.x --> APP
      APP[Tally app] --> FW[Context firewall<br/>structured fields only]
      FW --> AG[Agent<br/>Groq-hosted model or rule-based]
      AG -->|item_id| TX[pay_offer tx<br/>agent key in Keystore]
    end
    subgraph Counter["Merchant phone (same APK)"]
      MT[Merchant terminal] -->|signed menu<br/>QR · NFC HCE| APP
    end
    TX --> P{{"Tally program<br/>10 invariants"}}
    P -->|all hold| PAY[USDC → registered payout<br/>+ receipt]
    P -->|any fails| REV[Revert — nothing moves]
```

One atomic Solana transaction binds three proofs:

1. **Merchant proof.** A 144-byte menu header signed by the merchant's registered Ed25519 key, plus a Merkle proof for the chosen item.
2. **Intent proof.** A **capsule** PDA holding your rules (caps, categories, bond tier, expiry), which only your Seed Vault key can change.
3. **Execution proof.** Instructions-sysvar introspection with hardened Ed25519 offsets, then exact balance post-conditions.

### `pay_offer`: the ten invariants

| # | Invariant | Blocks |
|---|---|---|
| 1 | Agent signer, capsule not frozen or expired, merchant active | Stolen or rotated keys, kill switch |
| 2 | The previous instruction is the native Ed25519 precompile (sysvar introspection) | Missing or forged signatures |
| 3 | **Offset hardening**: one signature, every `*_instruction_index == u16::MAX`, offsets ≥ 16, strict bounds, message == rebuilt header | The precompile offset-hijack bug class *(mutation-verified: with the pin removed, the exploit transaction succeeds)* |
| 4 | Fresh slot window ≤150 slots (~60 s); longer windows need your co-sign | Stale QR replay |
| 5 | Payout = the merchant's **registered** account (never from the QR) | Payee swap |
| 6 | The program hashes the leaf itself; Merkle inclusion under the signed root | Price tampering, phantom add-ons |
| 7 | 256-category bitmap, per-purchase cap, rolling ~24 h cap | Out-of-policy spend |
| 8 | **Cheapest-Match:** price > category floor, under-bonded merchant, or extended window → owner co-sign | Whisper upsells (surfaced to you) |
| 9 | Receipt PDA `(capsule, merchant, menu_nonce, item_id)` is `init` | Double charges |
| 10 | `transfer_checked`, then `vault Δ == −price` and `payout Δ == +price`; optional exact Lighthouse assertion | Hidden drains, fee-on-transfer mints, state drift |

Also on-chain: an **SKR merchant bond** (no bond means no autonomy), **trustless fraud-proof slashing** (anyone can slash a merchant whose own signed menu contradicts its certified floor), arbiter disputes, a **Seeker Genesis Token** check for Seeker-only promos, and a merchant-enabled **proof-of-work anti-flood gate**. Details are in [docs/06](docs/06-onchain-program.md).

`pay_offer` costs about **20.4k compute units** (about 22.8k with a 4,096-item menu; target < 45k).

## What's in the repo

| Path | What |
|---|---|
| [`programs/tally`](programs/tally) | Anchor 1.2 program: 24 instructions |
| [`crates/tally-core`](crates/tally-core) | Byte-level primitives shared by the program and tests (header, leaves, Merkle, hardened Ed25519 parsing, post-conditions, PoW) |
| [`tests/litesvm`](tests/litesvm) | Threat-model suite: every on-chain attack in [docs/11](docs/11-threat-model.md) asserts its exact revert ([map](tests/litesvm/THREAT_SUITE.md)) |
| [`fixtures`](fixtures) | Cross-language vectors: Rust, TypeScript and Kotlin must reproduce every byte |
| [`sdk`](sdk) | TypeScript SDK (`@solana/kit` + Codama client), menu signing, `pay_offer` builder, error sentences |
| [`scripts`](scripts) | Deploy, initialise a cluster, seed the demo world, replay the attacks with explorer links, faucet |
| [`app`](app) | Android app (Kotlin + Compose): Seeker user mode **and** merchant terminal in one APK |
| [`docs`](docs) | Whitepaper, specs, threat model, research, demo script, deck outline, plans |

## Tests

| Suite | Count | What it proves |
|---|---|---|
| `cargo test --workspace` | **155** | Core vectors + 119 LiteSVM integration tests against the compiled `.so` (exact error codes, real Token-2022 SGT accounts, real mainnet Lighthouse program) |
| `pnpm -C sdk test` | **14** | TS byte parity with Rust; canonical instruction order; generates `fixtures/tx-v1.json` |
| `./gradlew :core:test` (in `app/`) | **39** | Kotlin byte parity with Rust **and** `@solana/kit` (PDAs, instruction data, compiled message); firewall, agent, codec, APDU, Second Look |

The demo flow was also run end to end against `solana-test-validator`. The honest purchase pays; the payee swap, phantom add-on and stale menu revert with the expected codes; the Whisper upsell stops for co-sign; the owner-approved purchase pays.

## Try it

### Program

```bash
cargo build-sbf --manifest-path programs/tally/Cargo.toml
cargo test --workspace
```

Requires Rust 1.97.1 (pinned) and Solana CLI 4.0.x.

### Local end-to-end (no devnet SOL needed)

```bash
solana-test-validator --reset \
  --bpf-program 3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk target/deploy/tally.so \
  --bpf-program L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95 fixtures/programs/lighthouse.so
pnpm install
cd scripts
TALLY_CLUSTER=localnet npx tsx init-devnet.ts   # mints, SGT collection, config
TALLY_CLUSTER=localnet npx tsx seed-demo.ts     # merchant + SKR bond, SGT-verified capsule
TALLY_CLUSTER=localnet npx tsx attack-demo.ts   # honest pay + attacks, with reverts
```

### Devnet

```bash
./scripts/deploy-devnet.sh
cd scripts && npx tsx init-devnet.ts && npx tsx seed-demo.ts && npx tsx export-app-config.ts
npx tsx faucet.ts <your-phone-wallet-address>   # SOL, test USDC, mock SKR for the demo
npx tsx attack-demo.ts                          # writes docs/DEVNET.md
```

### Android

Download the APK from [Releases](https://github.com/LSUDOKO/Tally/releases), or build it yourself:

```bash
cd app && ./gradlew :app:assembleDebug   # app/app/build/outputs/apk/debug/app-debug.apk
```

On a Seeker: **Connect with Seed Vault** → **Create a capsule** ("Coffee, max $6, $15 a day…") → on a second phone open **Merchant mode**, register with an SKR bond and broadcast → **Scan or tap** → "oat latte" → pay. Then open **Red team**.

**One phone or an emulator:** debug builds have a *Demo owner* switch in Settings (a local key in place of Seed Vault; release builds never include it). Fund that address with `npx tsx faucet.ts <address>`, register it in **Merchant mode**, then use **Scan or tap → Pay this phone's merchant menu (demo)**. The phone signs a fresh menu as the merchant and pays it as the customer, so the whole flow runs on real devnet transactions: purchases, the Second Look co-sign, red team, disputes, freeze, rotation, Lighthouse and proof-of-work mode.

Optional: paste your own Groq API key in **Settings** to use a hosted model (`openai/gpt-oss-120b` on Groq) as the agent. The key stays in app-private storage and is never built into the APK, and the app makes at most 30 model calls a day. Without a key, a deterministic rule-based agent is used. Either way the model only ever sees item ids, categories, prices, floors and dictionary-filtered name tokens, never merchant prose.

## Security, honestly

- **Blocked on-chain:** payee swap, price tampering, phantom add-ons, menu forgery, double charges, stale replay, conversational "pre-authorised" injection, Ed25519 offset hijack, hidden outflows and fee drains, contradictory floors (slashed), settlement floods.
- **Surfaced to you:** Whisper upsells, simulation-diff phishing (the Second Look sheet shows **gross** outflow and disables approval on unexpected instructions), under-bonded merchants.
- **Bounded:** a stolen agent key or an in-policy drain is limited by caps, categories, expiry, freeze and rotation.
- **Out of scope:** physical relay of a fresh QR, a merchant omitting cheaper items, rubber-stamped approvals, a compromised OS or Seed Vault.

The program has **not been audited**. A fresh whole-branch review found no critical issues, and its eight important findings were fixed with regression tests ([list](tests/litesvm/THREAT_SUITE.md#hardening-from-the-phase-1-review)). Before mainnet, the upgrade authority should move to a multisig and `initialize_config` should be restricted to it.

## Research

Tally combines what five 2026 papers found ([docs/03](docs/03-research-foundation.md)):

- Merchant text steers agents (2609.11757).
- Only deterministic checks at the execution boundary hold (APort Vault, 2609.22076: 140 → 0 unpermitted transfers).
- Those checks must be atomic with execution (Proof-Gated Signing, 2610.00354: simulation-only missed 50/50 drift attacks).
- Previews must show gross outflow (2607.28747).
- Freshness should come from a witness, not the device (2608.04957).

## License

MIT © LSUDOKO
