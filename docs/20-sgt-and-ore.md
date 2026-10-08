# 20 — Hardware attestation (SGT) and ORE proof-of-work

Tally's token layer has three parts:

- **SKR** (merchant bond and slashing; see [12](12-skr-and-merchants.md)).
- **SGT**, the Seeker Genesis Token: device-level anti-sybil.
- **ORE / DrillX**: proof-of-work rate limiting.

This doc covers the last two, including where each one is solid and where it is still a stretch goal.

---

## Part A — Seeker Genesis Token (SGT)

### What it is

Each physical Seeker mints a **soulbound** Seeker Genesis Token into its hardware wallet at setup. It's a Token-2022 asset that can't be transferred, and it belongs to the SGT collection/group. One device has one SGT, which makes it a cheap, strong **one-device-one-identity** signal.

### Why Tally needs it

Payment safety doesn't depend on SGT. **Merchant economics** do. Promos ("first coffee $1 for agent orders"), fee subsidies and higher default caps are easy to farm with emulators that each run an agent. SGT gating keeps those perks for real Seeker devices.

### How it's checked

| Layer | Check | When |
|---|---|---|
| **Off-chain** | **Sign-in-with-Solana** (MWA 2.0 `signIn`) proves control of `owner`. The app then fetches Token-2022 accounts for `owner` and checks for one whose mint is a member of the SGT group, with amount 1. | Onboarding |
| **On-chain** (optional) | `create_capsule(..., sgt_token_account)`: the program checks that the account's owner is `owner`, amount is 1, the token program is Token-2022, and the mint's **group-member extension** points at the SGT group (or its mint authority matches). It then sets `flags.SGT_VERIFIED` and stores `sgt_mint`. | Capsule creation |
| **Per promo** | `claim_promo(promo_id)` creates `SgtClaim` at `["sgt", sgt_mint, promo_id]` with `init`, so each device claims once | Promo redemption |

> **To confirm before building:** the SGT mint authority, group address and extension layout from the Solana Mobile developer docs. Don't hard-code guesses.

### Limits

- An SGT shows a Seeker **has** been set up. It doesn't show which person is holding it.
- A user with several Seekers has several SGTs. That's acceptable, since the cost per identity is a device.
- SGT is **not** a substitute for the owner signature. It's used only for perks and anti-farming.

---

## Part B — ORE proof-of-work rate limiter

### Threat

Autonomous agents can be turned against merchants. Hundreds of `pay_offer` attempts per second, or many fake HCE sessions, can tie up rotating menus and overwhelm a terminal phone. On-chain fees are low, so cost alone doesn't deter this.

### Mechanism

- The merchant sets `pow_difficulty` (0 = off) through `set_pow_difficulty`. Merchant mode suggests turning it on automatically when attempts spike.
- When it's on, every `pay_offer` must carry a **DrillX** solution. DrillX is the memory-hard, Equihash-based hash from ORE v2, available as the open-source `drillx` crate.

```
challenge = SHA256("TALLY_POW_V1" ‖ menu_nonce ‖ capsule_pubkey ‖ most_recent SlotHashes entry)
valid     = drillx::is_valid_digest(challenge, nonce, digest) && drillx::difficulty(digest) ≥ pow_difficulty
```

- Binding the challenge to `menu_nonce` and `capsule` means work can't be **reused** across menus or capsules. The `SlotHashes` entry stops precomputation.
- The check runs **inside `pay_offer`** (gate G1 in [06](06-onchain-program.md)), so it can't be skipped. The whitepaper describes "an ORE verification instruction". ORE's program doesn't expose a generic verify-arbitrary-challenge instruction, so Tally verifies with the `drillx` crate in-program.
- On the phone, the solver runs through JNI on a background dispatcher. At low difficulty it takes about 1–2 s on a phone, which is enough to make floods expensive and invisible to an honest single purchase. **Measure on a Seeker.**

### Compute budget (measured, Phase 1 Task 19)

| Work function | `pay_offer` total CU | Decision |
|---|---|---|
| None (gate off) | ~20.4k | — |
| **DrillX** (`drillx` 2.2 `Solution::is_valid`, Equi-X verify) | **~468k** (needs a 1.4M CU budget) | Rejected: over 10× the whole-instruction budget |
| **SHA-256 hashcash** (same challenge) | **~22.4k** | **Shipped** |

The on-chain gate is therefore SHA-256 hashcash over the identical challenge
(`SHA256("TALLY_POW_V1" ‖ menu_nonce ‖ capsule ‖ recent slot hash)`), with `difficulty` = required
leading zero bits. DrillX remains usable **off-chain** (e.g. for ORE-branded client work or a
future precompile), and the challenge format is unchanged, so swapping verifiers later does not
change any client binding. Say this plainly in the deck: "we measured DrillX at ~468k CU on-chain,
so the anti-flood gate uses a 2k-CU hashcash with the same binding".

### Background ORE mining → priority-fee offset (stretch)

The idea: idle-and-charging Seekers mine ORE, and the earned ORE is swapped to SOL to pay priority fees through Helius. That makes micropayments self-funding.

Be honest about it:

1. Phones contribute little hashpower and run into thermal limits (see [02](02-idea-selection.md)).
2. **Current ORE may no longer use per-user DrillX PoW.** Later ORE versions moved to a round/board model. Check the live protocol before promising "mine ORE on your phone".
3. Swapping ORE to SOL adds a DEX dependency.

**Plan:** demo the PoW **gate** (solid, security-relevant). Show mining as a **roadmap** card unless it's verified to work on current ORE.

### Prize eligibility (open question)

The earlier rules reading ([13](13-hackathon-requirements.md)) said ORE's matched prize needs ORE to be the **primary user-facing product**, and that one matched prize is shared across the Top 10. The whitepaper targets "$10,000 matched integration prize". **Confirm on the official CLOCK IN page before building for it.** If integrations don't qualify, keep the PoW gate anyway, because it's a real anti-DoS feature, and drop the prize framing.

---

## Demo moment (2:15–2:45)

1. Merchant phone: toggle **Congestion mode** → `pow_difficulty = 8`.
2. Red-team "flood" button fires 20 unsolved attempts → 20 × `PowRequired` in the forensics list.
3. The honest purchase shows "Solving work… 1.4 s" → paid.
4. Open the merchant registry account on the explorer: `skr_bonded = 5,000`, `slashed_total = 0`, `pow_difficulty = 8`.
