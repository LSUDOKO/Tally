# 17 — Repository structure

## Monorepo layout

```
tally/
├── README.md                    # judge-facing: what, demo video, APK, program ID, how to run
├── LICENSE                      # e.g. Apache-2.0
├── docs/                        # this pack
├── program/                     # Anchor workspace
│   ├── Anchor.toml
│   ├── programs/tally/src/
│   │   ├── lib.rs
│   │   ├── state.rs             # Capsule, Merchant, Receipt
│   │   ├── instructions/        # one file per instruction
│   │   ├── ed25519.rs           # introspection + offset hardening
│   │   ├── merkle.rs
│   │   ├── postcondition.rs     # reload deltas + Lighthouse introspection
│   │   ├── pow.rs               # DrillX gate (feature-flagged)
│   │   ├── sgt.rs               # Token-2022 SGT checks
│   │   ├── slashing.rs          # prove_floor_fraud, disputes
│   │   └── errors.rs
│   └── tests/                   # TypeScript or Rust tests, one file per attack class
├── fixtures/
│   └── menu-v1.json             # shared test vectors (root, sig, proofs)
├── sdk/                         # optional TS client: menu builder, proof builder, tx builder
├── app/                         # Android Studio project
│   ├── app/src/main/java/.../
│   │   ├── ui/                  # Compose screens
│   │   ├── wallet/              # MWA integration
│   │   ├── chain/               # RPC, tx builder, simulation, error mapping
│   │   ├── menu/                # CBOR decode, Ed25519 + Merkle verify
│   │   ├── agent/               # LLM client, firewall, rule fallback, red-team
│   │   ├── merchant/            # merchant terminal mode, HCE service
│   │   ├── nfc/                 # reader mode, APDU client
│   │   ├── pow/                 # DrillX JNI bridge
│   │   ├── widget/              # Glance widget
│   │   └── keys/                # agent key + Keystore (Ed25519 / fallback)
│   ├── native/drillx-jni/       # Rust → JNI for the PoW solver
│   └── build.gradle.kts
├── scripts/
│   ├── deploy-devnet.sh
│   ├── seed-demo.ts             # creates merchant, menu, capsule, funds them
│   └── make-test-usdc.ts
└── deck/
    └── tally-deck.pdf
```

## Tooling

| Tool | Use |
|---|---|
| Rust + Solana CLI + Anchor | Program |
| Node + TypeScript | Tests, scripts, optional SDK |
| Android Studio (latest stable), JDK 17 | App |
| Devnet RPC (Helius or public) | Demo network |

Pin versions in `Anchor.toml`, `rust-toolchain.toml` and Gradle; list them in the README.

## README template (judge-facing)

```markdown
# Tally — your agent proposes, the merchant signs, Tally checks it on-chain

[Demo video (3 min)](...) · [APK](...) · [Deck](deck/tally-deck.pdf)
Program (devnet): `<PROGRAM_ID>`

## What it does
Two sentences.

## Try it in 5 minutes
1. Install the APK on an Android phone with a Mobile Wallet Adapter wallet (Seed Vault on Seeker).
2. Get devnet SOL; the app's onboarding mints test USDC.
3. Open the sample menu QR in `docs/sample-menu.png` on another screen and scan it.

## Build from source
### Program
anchor build && anchor test
./scripts/deploy-devnet.sh
### App
cd app && ./gradlew assembleDebug

## How it works
Diagram + ten invariants (link to docs/06). Whitepaper: docs/00.

## Compute
`pay_offer`: <measured> CU (target < 45k, excluding the Ed25519 precompile).

## Security
Threat model summary + "reference code not audited" note (link to docs/11).

## Research
Links to the five papers (docs/03).
```
