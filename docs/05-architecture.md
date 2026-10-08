# 05 — Architecture

## Three proofs, one atomic transaction

```
  MERCHANT PROOF                  INTENT PROOF                     EXECUTION PROOF
  signed 144-B header        +    Capsule PDA (rules)          +   sysvar introspection,
  + Merkle leaf proof             created via Seed Vault           offset hardening,
  (registered key, fresh slot)    (owner-only changes)             exact-outflow post-conditions
            └──────────────────────────────┬──────────────────────────────┘
                                 pay_offer — 10 invariants
                                 pass → USDC moves · fail → whole tx reverts
```

## Components

```
┌──────────────────────────────── SEEKER (user phone) ────────────────────────────────────┐
│  Tally app (Kotlin + Compose, no WebView)                                               │
│   ├─ Onboarding: MWA 2.0 signIn (SIWS) + SGT lookup                                     │
│   ├─ Capsule manager ─────────── MWA 2.0 ──────► Seed Vault (owner key, never leaves)   │
│   ├─ Ingest: CameraX + ML Kit (QR) · NFC reader mode ↔ merchant HCE (ISO 7816-4)        │
│   ├─ Menu verifier (Ed25519 + Merkle + floor-consistency, off-chain pre-check)          │
│   ├─ CONTEXT FIREWALL ─┬─► display channel → Compose (names, descriptions, promos)      │
│   │                    └─► agent channel  → {item_id, category, price, floor, tokens}   │
│   ├─ Agent runtime (hosted LLM / rule fallback) → item_id only                          │
│   ├─ Agent key (Keystore Ed25519, API 33+; wrapped fallback)                            │
│   ├─ DrillX solver (JNI, only when merchant PoW is on)                                  │
│   ├─ Tx builder: [CB] · Ed25519 · pay_offer · Lighthouse   + simulator                  │
│   ├─ Second Look sheet (gross outflow, cheaper alternative, instruction list)           │
│   └─ Glance widget · Receipts & forensics · Red-team mode                               │
└───────────────────────────────────────────┬─────────────────────────────────────────────┘
                                            │ RPC (Helius: tx send + priority-fee estimate)
┌───────────────── SOLANA ──────────────────▼─────────────────────────────────────────────┐
│  Tally program (Anchor)                                                                 │
│   ├─ Merchant  (registry: signing key, payout ATA, SKR bond, PoW difficulty)            │
│   ├─ Capsule   (owner, agent, caps, 256-bit categories, expiry slot, flags, USDC vault) │
│   ├─ Receipt   (capsule × menu_nonce × item_id: nullifier + evidence)                   │
│   ├─ Dispute   (per receipt) · SgtClaim (per device × promo)                            │
│  Ed25519 precompile · SPL Token (USDC, SKR) · Token-2022 (SGT) · Lighthouse · SlotHashes │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                            ▲
┌──────────────── MERCHANT (any Android/Seeker, same APK, Merchant terminal mode) ────────┐
│  Catalog editor → floors → Merkle tree → sign header every ~20 s → rotating QR + HCE    │
│  register_merchant + SKR bond via MWA · PoW congestion toggle                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## Main flow

1. **Onboard:** SIWS through MWA 2.0. SGT ownership is checked, which unlocks Seeker-only perks.
2. **Owner creates a capsule** (once): natural language → structured rules shown as chips → `create_capsule` + `fund`, signed in Seed Vault with a biometric. Optionally includes the SGT account for the on-chain check.
3. **Merchant terminal** shows a signed menu as a rotating QR and over NFC HCE, re-signed every ~20 s within a ≤150-slot window.
4. **User scans or taps.** The app verifies the signature, root, window and floor consistency, then shows the merchant name, SKR bond and Live badge, with a haptic tick.
5. **Context firewall** splits the payload. The agent receives only structured rows and returns an `item_id`. The reasoning trace is shown live.
6. **Tx builder:** `[ComputeBudget] · Ed25519 verify(header, sig, key) · pay_offer(leaf, proof, header fields, pow?) · Lighthouse assert(vault)`. Signed by the agent key; Helius priority fee.
7. **Pre-flight:** simulate and compute gross outflow. If the price is above the floor, the bond is below the minimum, or the window is extended → Second Look → Seed Vault co-sign.
8. **On-chain:** ten invariants (see [06](06-onchain-program.md)). Pass → USDC to the registered payout, `Receipt` created, exact deltas asserted. Fail → full revert.
9. **Receipts & forensics** shows paid and blocked attempts with explorer links. The Home counter and widget update.

## Sequence: above-floor purchase

```
User        App/Agent               Seed Vault          Solana program
 │ "oat latte"  │                        │                     │
 │─────────────►│ firewall → pick (5.90) │                     │
 │              │ build tx, simulate     │                     │
 │              │ P > F_C ───────► Second Look sheet            │
 │◄─────────────┤ "Gross $5.90 · cheaper $4.50"                 │
 │ approve ─────────────────────────────►│ biometric, owner sig │
 │              │ agent sig + owner sig ──────────────────────►│ 10 invariants
 │              │                        │                     │ ✓ transfer, Δ exact, receipt
 │◄─────────────┤ receipt + explorer link ◄────────────────────┤
```

## Why this split

| Decision | Reason |
|---|---|
| Owner key only in Seed Vault | SMS/MWA requirement; hardware-backed approvals; key never in app memory |
| Separate agent key in Keystore | Agent acts without the user; compromise bounded by capsule rules |
| Checks in the program, not the app | APort Vault: the boundary must be deterministic and outside the agent's reasoning |
| Post-condition in the program, Lighthouse as well | PGS: atomic post-conditions survive drift; a client-appended assertion alone can be left out |
| Off-chain pre-check too | Better UX: explain a failure before spending a fee |
| USDC, not SOL | Stable prices a merchant can sign; readable amounts |
| Merkle menu, not per-item signatures | One signature per rotation; QR and NFC payloads stay small; the agent chooses among items |
| Slots, not unix time | Freshness and cycles anchored to consensus; matches signed slot windows |
| SKR bond + fraud proofs | Economic accountability where the chain can't prevent upfront |

## Off-chain services (optional)

None are needed for the core loop. Optional:

- **Menu relay** (Cloudflare Worker or Vercel function) to fetch large menus by root for Mode B.
- **Indexer** for receipts and forensics, or read `Receipt` accounts directly with `getProgramAccounts` filtered by capsule.
- **Helius** for transaction landing and `getPriorityFeeEstimate`.
