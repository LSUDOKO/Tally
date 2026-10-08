# 00 — Whitepaper

## Tally: Deterministic Intent-to-Execution Binding and Verifiable Counterpart Settlement for Autonomous Mobile Agents

> **Status.** Design document for CLOCK IN (Radiants × Solana Mobile, 2026). Code in this pack is a reference sketch: not compiled, not audited. Numbers from papers were checked against the papers in October 2026. Where an earlier draft said more than the sources support, the corrected figure is used and the change is listed in [§12](#12-corrections-to-the-earlier-draft).

---

## Abstract

When a user hands transaction building to an AI agent, the security question changes. Today's wallet controls (spending caps, multisig thresholds, destination allowlists) limit **how much** can leave an account and **to whom**. They do not check **whether this particular purchase is the one the user meant**. An agent that reads untrusted merchant text can be talked into a pricier item, an add-on nobody showed, or a different payee. The resulting transaction stays under the cap, is signed by an authorised session key, and executes. We call this the **Decision-to-Transaction Binding Gap**.

Tally closes the gap for in-person and counter commerce on the **Solana Mobile Stack** and **Seeker** hardware. A single atomic Solana transaction binds three proofs:

1. **Merchant proof**: a menu header signed by a registered merchant, plus a Merkle membership proof for the chosen item.
2. **Intent proof**: an on-chain **Capsule** of user rules, created and changed only through the Seeker **Seed Vault**.
3. **Execution proof**: instruction-sysvar introspection with hardened Ed25519 offset checks, followed by post-conditions on the vault and payout balances.

Because the program enforces the user's intent itself, an agent misled by deceptive input runs into an on-chain revert. Money cannot move outside that intent.

**Spending limits protect your balance. Tally protects your decision.**

---

## 1. The problem: signing the transaction, not the decision

In a normal dApp the user reads, simulates and signs each state change. In agentic shopping:

- an LLM chooses what to buy and builds the instruction;
- it reads **untrusted** data: menus, item descriptions, promo copy, QR and NFC payloads;
- it signs with a **session key** that the account perimeter already trusts.

An attacker doesn't need the private key. They only need to **poison the context** the agent reads. The agent then builds a cart that is well-formed and fully signed, and every perimeter control lets it through.

| Perimeter control | What it checks | What it misses |
|---|---|---|
| Spending cap | Total per period | A $5.90 upsell inside a $15 cap |
| Destination allowlist | Payee address | An overpriced item from an allowlisted café |
| Session key / expiry | Who may sign, until when | Which item the key paid for |
| Wallet simulation | State at preview time | State drift; net-vs-gross display tricks |

---

## 2. Empirical foundation

Five 2026 papers show that safeguards inside the model are not enough, and they point to the mechanisms Tally uses. Full notes are in [03-research-foundation.md](03-research-foundation.md).

| Vulnerability vector | Finding (verified) | Tally mechanism |
|---|---|---|
| **Whisper steering** | Louck, Dvir, Stulman (arXiv 2609.11757): a factual claim in merchant text ("the cheaper item is discontinued") steered the agent to a pricier listed item **73.3%** of the time on the GA model AP2's samples use. It worked on all 17 builds tested, and the carts passed every signature check. Branded Whisper (inflated price, add-on, redirected payee) reached 56%. | Merkle-proven catalog leaf plus the **Cheapest-Match Invariant** `P_item ≤ F_category`. Anything above the floor needs owner co-signature. |
| **Model reasoning failure** | Uchibeke, *APort Vault* (arXiv 2609.22076): **4,371** human-written CTF attacks against **14** models, **225,964** evaluations. With a deterministic check at the tool boundary, unpermitted transfers fell from **140 → 0** (105 → 0 on matched pairs), with a session-clustered upper bound of **0.38%**. A forged `[Tool Result: verify_recipient]` fooled **7 of 14** models in multi-turn replay but did nothing against the boundary check. | Policy is evaluated **inside the SVM**. The program never reads the conversation. |
| **Pre-signing state drift** | Ghosh, *Proof-Gated Signing* (arXiv 2610.00354): simulation-only checking missed **all 50** drift scenarios and prevented 57.9% of harmful scenarios overall. On-chain post-conditions left **0 of 50** drift scenarios with attacker gain. A clean simulation made an LLM reviewer *more* likely to approve a drift attack. | Every check is **atomic with the transfer**. In-program balance post-conditions, plus a terminal **Lighthouse** assertion. |
| **Simulation-diff phishing** | arXiv 2607.28747: **4,224** phishing contracts, **5,700+** victims, about **$3.48M** lost on EVM chains. Most wallets displayed **net** change, so a drain looked like a gain. | The Second Look sheet shows **gross outflow** and blocks approval when an unexpected instruction is present. |
| **Location spoofing / relay** | arXiv 2608.04957: self-reported GNSS is spoofable. A witness bound to a fresh block reference rejects stale replays but **not fresh relay**. | The merchant acts as witness. Menus are signed with a **slot window** `S_issue ≤ S_now ≤ S_expire`, ≤150 slots for autonomous pay. The relay limit is stated openly. |

---

## 3. Comparative position

Each existing system works at the **perimeter**. Tally works at the **decision**. A longer table is in [04-competitive-landscape.md](04-competitive-landscape.md).

| Architecture | Enforcement layer | Granularity | Limit in agentic commerce |
|---|---|---|---|
| Squads spending limits | Smart-account PDA | Amount per window, destinations | Can't see items, markups or add-ons |
| Cloaked Agent | Program session key | Key lifetime, token allowance | Can't verify the chosen item |
| Praxis | Account policy engine | Per-agent transaction caps | Allows substitutions under the cap |
| AgentGuard | Program-level guard | Merchant allowlist, pause | Misses price manipulation by allowlisted merchants |
| wormhole-x402 | Off-chain client SDK | Quote signature check | Skipped if the client is compromised |
| AP2 / Mastercard Verifiable Intent | Card-rail APIs | Signed cart and payment mandates | Off-chain; no trustless settlement |
| Lighthouse | SVM assertion framework | Arbitrary account assertions | No notion of merchant offers or catalogs |
| **Tally** | **Native SVM program + Seed Vault** | **Merkle offer proofs, cheapest-match, gross outflow** | Binds intent on-chain, atomically |

Tally does not replace Lighthouse. It **uses** Lighthouse as a general post-condition primitive and adds the offer and catalog semantics on top.

---

## 4. On-chain specification (summary)

Full detail, layouts and reference code are in [06-onchain-program.md](06-onchain-program.md) and [07-menu-and-qr-spec.md](07-menu-and-qr-spec.md).

### 4.1 Signed menu header: 144 bytes

The merchant's registered Ed25519 key signs these raw bytes. Ed25519 hashes internally, so no extra pre-hash is needed.

```
Header = "TALLY_MENU_V1"(16, zero-padded) ‖ ProgramID(32) ‖ MerchantPDA(32) ‖ R_menu(32)
       ‖ S_issue(8) ‖ S_expire(8) ‖ N_menu(8) ‖ Params(8)           = 144 bytes
Params = item_count u32 ‖ hash_alg u8 ‖ header_version u8 ‖ reserved u16
```

`R_menu ∈ {0,1}^256` is the catalog's Merkle root. `[S_issue, S_expire]` is the validity window in slots. `N_menu` is a 64-bit nonce that keeps menu rotations apart. Binding `ProgramID` and `MerchantPDA` prevents the header from being replayed against another deployment or another merchant entry.

### 4.2 Catalog leaf

```
L_i = H(0x00 ‖ I_i[16] ‖ C_i u16 ‖ P_i u64 ‖ F_Ci u64)        internal node: H(0x01 ‖ min ‖ max)
```

- `I_i`: 16-byte item ID.
- `C_i`: 16-bit category, such as Coffee, Lunch or Transit.
- `P_i`: price in micro-USDC (10⁻⁶).
- `F_Ci`: the merchant-certified **category floor**, the lowest price among active items in `C_i`.

**Hash function.** `H` is selected by `hash_alg`. v1 uses **SHA-256** through the `sol_sha256` syscall. The earlier draft named **BLAKE3**, but Solana's BLAKE3 syscall is feature-gated and was not active on mainnet when this was written. v1 therefore ships SHA-256, and `hash_alg = 1` is reserved for BLAKE3 once the syscall is live. Check the feature status before switching.

### 4.3 Accounts (PDAs)

| PDA | Seeds | Holds |
|---|---|---|
| `Merchant` | `["merchant", authority]` | Display metadata, Ed25519 menu-signing key, USDC payout ATA, **SKR bond**, active flag, PoW difficulty, bond unlock slot |
| `Capsule` | `["capsule", owner, capsule_id u64]` | Owner, agent key, USDC vault, `per_tx_cap`, `daily_cap`, `spent_in_cycle`, `cycle_start_slot`, 256-bit category bitmap, `min_merchant_bond`, `expiry_slot`, `frozen`, SGT mint (optional), flags |
| `Receipt` | `["receipt", capsule, menu_nonce, item_id]` | Merchant, cleared price, slot, co-signed flag. Its unique address blocks duplicate settlement. |
| `Dispute` | `["dispute", receipt]` | Evidence hash, status, slash amount |

### 4.4 The ten invariants of `pay_offer`

All ten run in one transaction. If any fails, the whole transaction reverts.

| # | Invariant | Enforcement |
|---|---|---|
| 1 | **Authority and liveness** | `agent_key` signs and matches; `!frozen`; `S_now < expiry_slot`; merchant `active` |
| 2 | **Instruction sysvar introspection** | `load_instruction_at_checked(i − 1)` targets `ed25519_program::ID`, and that instruction has an empty account list |
| 3 | **Ed25519 offset hardening** | `num_signatures == 1`. `signature_instruction_index`, `public_key_instruction_index` and `message_instruction_index` all equal `u16::MAX`. Every offset is ≥ 16, the size of the precompile header, and every slice is inside the buffer. The public key equals the registered key and the message equals the **rebuilt** 144-byte header. |
| 4 | **Temporal freshness** | `S_issue ≤ S_now ≤ S_expire` and `S_expire − S_issue ≤ 150` slots (about 60 s) for autonomous pay. Longer windows, up to `MAX_EXTENDED_WINDOW`, need owner co-signature. |
| 5 | **Registry payout** | The destination is `merchant.payout`, loaded from the PDA. The client cannot supply a recipient. |
| 6 | **Merkle inclusion** | The proof (depth ≤ 16) places `L(I, C, P, F_C)` under `R_menu` |
| 7 | **Capsule policy** | Category bit set in the bitmap; `P ≤ per_tx_cap`; `spent_in_cycle + P ≤ daily_cap`. The cycle resets after 216,000 slots, about 24 h. |
| 8 | **Cheapest-Match Invariant** | `P > F_C` → owner must co-sign. Merchant bond below `min_merchant_bond` → owner must co-sign. |
| 9 | **Duplicate-settlement nullifier** | `Receipt` PDA `init`. A second payment for the same `(capsule, N_menu, I)` fails on address collision. |
| 10 | **Atomic outflow and post-condition** | CPI transfer from vault to payout; then the program **reloads both accounts** and requires `vault Δ = −P` and `payout Δ = +P` exactly. The app also appends a terminal **Lighthouse** assertion. A capsule flag can make the program **require** it by introspection. |

Two optional gates sit on top of the ten:

- **ORE PoW gate** (§6.3): applies when the merchant turns on congestion mode.
- **SGT gate** (§6.2): applies at capsule creation for Seeker-only features.

### 4.5 Why the post-condition lives in the program *and* in Lighthouse

If the post-condition existed only as a Lighthouse instruction appended by the client, a compromised client could leave it out. Tally therefore puts the **mandatory** check in `pay_offer` itself, where the client cannot remove it. The Lighthouse assertion is **defence in depth**: it covers accounts the program doesn't own, such as the agent's SOL fee balance. A capsule can set `REQUIRE_LIGHTHOUSE`, and the program then checks by introspection that the last instruction is a Lighthouse assertion over the vault.

---

## 5. Mobile client: Solana Mobile Stack and Seeker

Detail is in [08-android-app.md](08-android-app.md) and [09-agent-design.md](09-agent-design.md).

### 5.1 Two signing domains

| Domain | Key | Lives in | Signs |
|---|---|---|---|
| **Owner** | Master key | **Seed Vault** secure environment, reached through **Mobile Wallet Adapter 2.0** (`mobile-wallet-adapter-clientlib-ktx` 2.0.x; plan pins 2.0.7) | Create capsule, change rules, fund or withdraw, freeze, co-sign cheapest-match exceptions |
| **Agent** | Ephemeral Ed25519 | **Android Keystore**, hardware-backed where the device's KeyMint supports Ed25519 (Android 13+). Otherwise an AES-GCM-wrapped software key. | Only `pay_offer` against one Capsule |

The master key never enters the app process. If the app sandbox is compromised, the attacker is still limited by the capsule's caps, categories and expiry, and by the owner's freeze switch.

### 5.2 Context firewall against indirect prompt injection

Point-of-sale data is split into **two channels that never meet**:

- **Display channel**: marketing copy, descriptions, images and promo claims go **only** to Compose for the human to see.
- **Agent channel**: a sanitised JSON payload `{item_id, category_code, price_usdc, category_floor_usdc, name_tokens}`. `name_tokens` is a dictionary-filtered, lowercased word list of at most 6 tokens with no punctuation, URLs or free text.

Injected instructions in menus are removed **before inference**. The chain doesn't depend on the firewall. The firewall reduces how often the user is asked to step in. The **program** decides whether money moves.

### 5.3 Counter ingest

- **QR**: CameraX + ML Kit, reading rotating high-density 2D codes.
- **NFC**: the merchant phone runs an **Android Host Card Emulation** service that speaks **ISO/IEC 7816-4** APDUs (`SELECT AID`, then chunked `GET MENU`). The Seeker reads it in NFC reader mode. A tap loads the signed payload straight into memory.

### 5.4 Interface: seven core surfaces

| Surface | Purpose |
|---|---|
| Home dashboard + Glance widget | Capsule balances, rolling 24 h use, **Attacks Blocked This Week** |
| Provisioning | Natural language → structured rules → Seed Vault biometric signature |
| Scan (Camera / NFC) | Haptic confirmation when a signed offer loads; merchant name and bond |
| Agent reasoning | Live decision trace mapping the request to catalog leaves |
| Second Look sheet | Gross outflow in high contrast, cheaper alternatives skipped, extra-instruction alarm |
| Receipts & forensics | Settled orders and blocked attacks, with explorer links |
| Merchant terminal mode | Build the catalog, build the Merkle tree, sign headers, broadcast QR / NFC |

**Correction.** Tally can't change what the **Seed Vault** signing screen shows. The gross-outflow display lives on Tally's Second Look sheet, which **always** opens before the Seed Vault prompt. The earlier draft called this "hardware-enforced gross outflow on Seed Vault screens", which overstated it.

---

## 6. Token and hardware layer: SKR, SGT, ORE

Detail is in [12-skr-and-merchants.md](12-skr-and-merchants.md) and [20-sgt-and-ore.md](20-sgt-and-ore.md).

### 6.1 SKR merchant bond and slashing

- To register a menu-signing key, a merchant posts an **SKR bond** into a bond-vault PDA.
- **Trust tiers.** If a merchant's bond is below the capsule's `min_merchant_bond`, every purchase needs Seed Vault confirmation. Bonded merchants allow autonomous clearance **only** at the category floor.
- **Fraud proofs.** If a signed menu contains an item in category `C` priced **below** the floor `F_C` that the merchant certified in another leaf of the same menu, anyone can submit both Merkle proofs plus the Ed25519-verified header. The program proves the misstatement on-chain and **slashes automatically**, with no arbiter.
- **Disputes.** Other disputes (wrong goods, no delivery) are filed against a `Receipt` and resolved by an arbiter multisig, which can slash part of the bond to compensate the user.
- **Unlock delay.** Bonds unlock only after a delay, so a merchant can't bond, cheat and leave in one step.

### 6.2 Seeker Genesis Token (SGT) anti-sybil

Each Seeker holds a soulbound **SGT** in its hardware wallet. Tally checks for it in two places:

- **Off-chain**, through **Sign-in-with-Solana** at onboarding.
- **On-chain**, optionally, in `create_capsule`: the SGT token account must be owned by `owner` with amount 1, and its mint must be a member of the SGT group. Mint and group addresses come from the Solana Mobile docs.

An `SgtClaim` PDA `["sgt", sgt_mint, promo_id]` limits each device to one subsidised promo. This keeps virtual-device bot farms out of merchant discount programs.

### 6.3 ORE proof-of-work rate limiter

Autonomous agents can be turned against merchants: a flood of signature or settlement requests ties up inventory or knocks over a counter terminal. A merchant under load can set `pow_difficulty > 0`. Each `pay_offer` to that merchant must then carry a **DrillX** solution (the open-source hash from ORE v2) over

```
challenge = SHA256("TALLY_POW_V1" ‖ N_menu ‖ capsule ‖ recent_slot_hash)
```

`pay_offer` verifies the solution in-program, so each settlement attempt costs real work on the client. Optionally, idle Seeker hardware can mine to build up ORE that **offsets priority fees** through Helius. This is treated as a stretch goal: [02-idea-selection.md](02-idea-selection.md) explains why phone mining is weak, and current ORE may no longer use DrillX per-user PoW. Check this before building. **Eligibility** for ORE's matched prize is listed under open questions in [13-hackathon-requirements.md](13-hackathon-requirements.md).

---

## 7. Threat model (summary)

Full matrix in [11-threat-model.md](11-threat-model.md).

| Result | Attacks |
|---|---|
| **Blocked on-chain (revert)** | Payee swap · price tampering · phantom add-on · menu forgery · duplicate settlement · stale-catalog replay · conversational "pre-authorised" social engineering · Ed25519 offset hijack · token-fee / hidden-outflow drain · forged floor (fraud-proof slash) · PoW-gated flood |
| **Surfaced to user (Seed Vault prompt)** | Whisper upsell (cheapest-match) · simulation-diff phishing (Second Look) · under-bonded merchant |
| **Bounded by policy** | Agent key extraction · in-policy drain over many purchases |
| **Out of scope** | Physical relay of a fresh QR · merchant omitting cheaper items entirely · user approving without reading · compromised Seed Vault |

---

## 8. Hackathon alignment

| Criterion (25% each) | Architecture | Observable deliverable |
|---|---|---|
| Stickiness & PMF | Daily capsules for coffee, lunch and transit | Home widget with rolling balance and Attacks Blocked counter |
| User experience | Tap or scan to pay; a human is involved only for exceptions | One scan, one sentence, one tap; Second Look gross-outflow sheet |
| Innovation / X-factor | Binds an agent's choice to a verified merchant offer, on-chain | Sysvar introspection, offset hardening, Merkle leaves, fraud-proof slashing |
| Presentation & demo | Live split-screen: perimeter wallet vs Tally under attack | Reverts, gross-outflow warning, explorer traces |

Demo script: [14-demo-script.md](14-demo-script.md). Deck: [15-pitch-deck.md](15-pitch-deck.md).

---

## 9. Engineering roadmap (seven phases)

| Phase | Deliverables | Done when |
|---|---|---|
| 1. Core Anchor program | Merchant, Capsule, Receipt state; init, fund, withdraw, freeze | Local-validator tests pass; PDAs derive; vault funded |
| 2. Introspection & crypto | Sysvar parsing, hardened Ed25519 offsets, Merkle verify, post-conditions | Offset-manipulation tests revert |
| 3. Threat-model suite | One test per threat row on localnet and devnet | 100% pass with exact error codes; `pay_offer` **< 45,000 CU** measured, excluding the precompile |
| 4. Merchant terminal | Catalog compiler, Merkle builder, signer, rotating QR, HCE NFC service | Payloads rotate within 150-slot windows over QR and NFC |
| 5. Seeker app | Kotlin/Compose, MWA 2.0 + Seed Vault, context firewall, Keystore agent key | Biometric flow works on a device; firewall tests pass |
| 6. Tokens & telemetry | SKR bond, fraud-proof and dispute slashing, SGT gate, DrillX gate, Glance widget | Bond and PoW verified on devnet |
| 7. Release | Signed APK, README, demo video, deck, reproducible build notes | APK verified on physical Seeker hardware |

Mapped to milestones in [16-build-plan.md](16-build-plan.md).

---

## 10. Residual-risk statement

> Tally guarantees that an agent payment goes to a **registered** merchant's **registered** payout, for an item and price that merchant signed in a **fresh** menu, within the owner's caps and categories. The vault loses exactly the signed price and nothing else. Any choice priced above the category floor, any long-lived menu and any under-bonded merchant needs the owner's Seed Vault signature. Tally does **not** prove physical presence, does **not** judge whether prices are fair beyond the merchant's own signed floor, and cannot stop a user who approves without reading.

## 11. Conclusion

Agents can't safely run real commerce on security models built for human-supervised signing. Perimeter controls protect balances. They don't protect decisions. Tally combines the Seeker's hardware (Seed Vault, Keystore, NFC, camera) with the SVM's deterministic execution: merchants sign structured catalogs, untrusted prose stops at an on-device firewall, and price floors, Merkle membership and exact outflow are checked on-chain in the paying transaction. That closes the decision-to-transaction binding gap.

---

## 12. Corrections to the earlier draft

| Earlier claim | Status | What this pack says instead |
|---|---|---|
| "Attackers bypassed conversational constraints in **74.6%** of permissive runs" (APort Vault) | **Not in the paper** | Use 140 → 0 unpermitted transfers, the 0.38% session bound, and 7/14 models fooled by a forged tool result. The paper also warns that request rates such as 79.4% at Level 4 are **not** attack success. |
| "Pre-signing simulation failed across 100% of drift scenarios" | **Correct, with scope** | Simulation-only missed 50/50 **drift** scenarios. Overall it prevented 57.9%. Say "drift". |
| "Hardware-enforced gross outflow on all Seed Vault signing screens" | **Overclaim** | Gross outflow is on Tally's Second Look sheet before Seed Vault. |
| BLAKE3 leaves and header | **Not deployable as an active syscall at time of writing** | SHA-256 in v1; `hash_alg` byte reserved for BLAKE3 |
| "Lighthouse verifies the net balance" as the only post-condition | **Bypassable if client-appended** | Mandatory in-program check plus optional required Lighthouse |
| "First on-chain protocol…" | **Novelty overclaim** | "No Solana project we found enforces a merchant-signed offer against user rules on-chain." |
| Ed25519 keypair "generated within Android Keystore" | **Device-dependent** | Hardware Ed25519 needs KeyMint support (Android 13+); fallback is a wrapped key |
| Merchant slashing for "duplicate settlements" | **Unnecessary** | Duplicates already revert through the Receipt PDA. Slashing targets **floor fraud** and disputes. |
| ORE `$10,000 matched integration prize` | **Eligibility unconfirmed** | Earlier rule reading: ORE must be the primary product. Confirm on the official page. |
| `R_menu ∈ ℝ^32` | Notation | `R_menu ∈ {0,1}^256` (32 bytes) |
