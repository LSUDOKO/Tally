# 16 — Build plan

**Rule: the on-chain revert is the heart of the demo, so build and test the program first.** Each milestone ends with something demoable. The whitepaper's seven phases map onto the milestones below.

| Whitepaper phase | Milestone |
|---|---|
| 1. Core Anchor program | M1 |
| 2. Introspection & cryptography | M2 |
| 3. Threat-model test suite | M2 (tests land with features) + M3 gate |
| 4. Merchant terminal module | M4 |
| 5. Seeker mobile application | M5 |
| 6. Token integration & telemetry | M6 |
| 7. Production release | M7 |

## Milestones

### M1 — Program core
- [ ] Anchor workspace, program ID, devnet deploy script
- [ ] `Merchant` (seeded by authority), `Capsule` (slot-based cycle, 256-bit categories, flags), `Receipt`
- [ ] `register_merchant` (SKR bond vault), `create_capsule`, `fund`, `withdraw`, `freeze`, `set_agent`, `update_rules`
- [ ] Test USDC mint and mock SKR mint on localnet/devnet
- **Done when:** local-validator tests pass, PDAs derive as specified, and a capsule can be created and funded from a script.

### M2 — `pay_offer` + introspection
- [ ] 144-byte header builder; Ed25519 introspection with **all** offset hardening
- [ ] SHA-256 Merkle leaf/node and proof verification (depth ≤ 16)
- [ ] Freshness tiers (150 / 9,000), caps, categories, cycle reset, Cheapest-Match, bond tier
- [ ] `Receipt` nullifier, SPL transfer, **reload-based post-conditions**, optional Lighthouse requirement
- [ ] Shared vectors `fixtures/menu-v1.json`
- **Done when:** every row in [11-threat-model](11-threat-model.md) that can be tested on-chain has a passing test with an exact error code ([18](18-testing.md)).

### M3 — Gate: compute and devnet
- [ ] Measure CU: target **`pay_offer` < 45,000 CU** without the PoW gate
- [ ] Run the full suite on devnet once
- **Done when:** CU numbers are recorded in the README.

### M4 — Merchant terminal
- [ ] Catalog editor, floor computation, contradiction refusal, Merkle builder, signer
- [ ] Rotating QR (≈20 s re-sign); **HCE service** with APDU chunking
- [ ] Register + bond via MWA
- **Done when:** Phone A rotates payloads over QR **and** NFC inside 150-slot windows, and a test script can pay against each.

### M5 — Seeker app core
- [ ] MWA 2.0 connect + SIWS; capsule provisioning with parsed-rule chips and Seed Vault biometric
- [ ] Scanner + NFC reader + local verification
- [ ] Context firewall (two channels, dictionary tokens) and agent with rule fallback
- [ ] Keystore agent key (Ed25519 API 33+; fallback)
- [ ] Tx builder (instruction order), simulation, gross outflow, Second Look, co-sign
- **Done when:** the honest purchase and the co-sign flow work on a real Seeker, and the firewall unit tests pass.

### M6 — Tokens and telemetry
- [ ] `prove_floor_fraud` + `Slashed` event; bond badge on scan
- [ ] SGT lookup (off-chain) and optional on-chain check in `create_capsule`; `SgtClaim`
- [ ] DrillX gate: JNI solver, on-chain verification, CU measured; hashcash fallback if it's too costly
- [ ] Red-team mode (10 attacks), receipts & forensics, Glance widget, haptics
- [ ] Comparison mode ("plain capped wallet") for the split-screen opening
- **Done when:** bond and PoW are verified on devnet and the red-team list is all green.

### M7 — Release
- [ ] Signed release APK; GitHub Release
- [ ] README per [17](17-repo-structure.md), including pinned versions and reproducible-build notes
- [ ] Demo video per [14](14-demo-script.md); deck PDF per [15](15-pitch-deck.md)
- [ ] Standalone APK verified on **physical Seeker hardware**
- [ ] Submit on the official CLOCK IN site before the deadline

## Cut list (if time runs short, cut in this order)

1. Background ORE mining (roadmap only, already)
2. Home widget
3. On-chain SGT check (keep the off-chain SIWS + SGT lookup)
4. Mode B (menu by URL); keep inline only
5. DrillX gate → SHA-256 hashcash gate (same challenge; loses the ORE angle)
6. NFC HCE (keep camera)
7. Hosted LLM → rule-based agent only
8. SKR bond tiers → keep the registry; show the bond as roadmap (loses the SKR prize angle)

**Never cut:** Ed25519 offset hardening, Merkle membership, registry payout, Cheapest-Match co-sign, in-program post-condition, on-chain reverts in the video.

## Commit hygiene (judged via GitHub)

- Small commits with clear messages, e.g. `program: harden ed25519 offsets (>=16, u16::MAX)`.
- One branch or PR per milestone.
- Tests land with the feature, not at the end.
