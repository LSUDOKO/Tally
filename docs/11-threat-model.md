# 11 — Threat model

The table below has 20 attacks:

- **Eleven are blocked on-chain.**
- **Three are surfaced to the user** for a Seed Vault decision.
- **Two are bounded** by policy.
- **Four are out of scope.**

State the boundary plainly in the deck. The EthelSec judges will respect a clear boundary more than a claim of total protection.

## Assets

- USDC in capsule vaults.
- The owner's Seed Vault key, which is never exposed to the app.
- The agent key, which is exposed to the app and bounded by capsule rules.
- Merchant signing keys and SKR bonds.
- Merchant terminal availability (DoS).

## Trust assumptions

1. Seed Vault and MWA behave as specified.
2. The Tally program is correct and its upgrade authority is honest.
3. The native Ed25519 precompile, SPL Token, and Lighthouse programs are correct.
4. A registered merchant's signing key is controlled by that merchant.
5. The user reads the Second Look sheet when asked. This is the weakest assumption.
6. The dispute arbiter multisig is honest. This only matters for non-provable disputes.

## Adversaries

| Adversary | Can |
|---|---|
| Malicious merchant text | Write any name or description; try to steer the agent |
| Network / payload attacker | Show a fake QR or NFC payload; replay old ones; relay fresh ones |
| Compromised or injected agent | Call `pay_offer` with any arguments, and build any transaction |
| Stolen agent key | Sign as the agent from another device |
| Dishonest registered merchant | Sign misleading menus or floors; omit items |
| Bot farm | Spin up many virtual agents to farm promos or flood terminals |

## Attack matrix

| # | Attack | Mechanism | Countermeasure | Result |
|---|---|---|---|---|
| 1 | **Payee swap** | QR/NFC payload or client changes the recipient | Payout comes only from the `Merchant` PDA (`has_one = payout`); there is no recipient argument | **Blocked (revert)** |
| 2 | **Price tampering** | Inflate the price in transit | Price is hashed into the leaf, so the proof fails | **Blocked** |
| 3 | **Phantom add-on** | Unlisted fee, tip or item | Merkle inclusion against the signed root fails | **Blocked** |
| 4 | **Menu forgery** | Catalog signed by an unregistered key | Introspected Ed25519 key must equal `merchant.signing_key`; header binds `merchant_pda` | **Blocked** |
| 5 | **Duplicate settlement** | Charge the same offer twice | `Receipt` PDA `init` collides on `(capsule, menu_nonce, item_id)` | **Blocked** |
| 6 | **Stale-catalog replay** | Re-broadcast an expired menu | `issued ≤ slot ≤ expires`; window ≤ 150 slots for autonomous pay | **Blocked** |
| 7 | **Conversational "pre-authorised" injection** | "Order already approved", forged tool results | Program checks signatures and rules only; it never reads the transcript | **Blocked** |
| 8 | **Ed25519 offset hijack** | Precompile offsets point at bytes in another instruction, so the program reads spoofed data | All `*_instruction_index == u16::MAX`; offsets ≥ 16; strict bounds; `num_signatures == 1`; message == rebuilt header | **Blocked** |
| 9 | **Hidden outflow / token-fee drain** | Extra transfer, or a fee-on-transfer mint shorts the payee | In-program `vault Δ == −P` and `payout Δ == +P`; required Lighthouse assertion; Second Look disables approval on extra instructions | **Blocked** |
| 10 | **Floor fraud (contradiction)** | Merchant certifies a floor above a cheaper item in the same menu, to make an upsell look autonomous | `prove_floor_fraud` slashes the bond; the app rejects the menu before paying | **Blocked + slashed** |
| 11 | **Settlement flood / DoS** | Agents spam `pay_offer` to lock inventory or stall the terminal | Merchant-enabled **DrillX PoW gate**; `Receipt` uniqueness; priority-fee cost | **Blocked** (rate-limited) |
| 12 | **Whisper upsell (Selection)** | Description text talks the agent into the pricier listed item | **Cheapest-Match Invariant**: `P > F_C` → owner co-sign | **Surfaced** (Seed Vault) |
| 13 | **Simulation-diff phishing** | Net-positive preview hides a gross loss | Second Look shows **gross** outflow per account; extra-instruction alarm | **Surfaced** |
| 14 | **Under-bonded or new merchant** | Low-cost merchant identity | `skr_bonded < min_merchant_bond` → owner co-sign | **Surfaced** |
| 15 | **Agent key extraction** | Rooted device, malware | Caps, categories, expiry, freeze, `set_agent` rotation | **Bounded** |
| 16 | **In-policy drain** | Many cheapest-match purchases up to the daily cap | `daily_cap` per cycle; receipts visible; freeze | **Bounded** |
| 17 | **Physical relay** | Live video or proxy of a fresh QR to a remote victim | Slot window proves freshness, not proximity. NFC helps (centimetre range), but a relay is still possible. | **Out of scope** |
| 18 | **Floor fraud by omission** | Merchant leaves cheaper items off the menu | Bond and reputation only; can't be proven on-chain | **Out of scope** |
| 19 | **Rubber-stamp approvals** | User approves without reading | Cheaper option shown first; large gross amount | **Out of scope** |
| 20 | **Seed Vault / OS compromise** | Attacker controls the secure element or OS | Trust assumption 1 | **Out of scope** |

## Bot farms and sybils (cross-cutting)

The SGT gate (see [20](20-sgt-and-ore.md)) limits promos and Seeker-only features to devices that hold a Seeker Genesis Token, with one `SgtClaim` per device per promo. This isn't a payment-safety control. It protects **merchant economics**.

## Implementation risks (bugs that would break the model)

| Risk | Mitigation | Test |
|---|---|---|
| Ed25519 offsets outside the instruction | `u16::MAX` indices, offsets ≥ 16, checked bounds | `ed25519_offset_attack`, `ed25519_offset_into_header` |
| Multiple signatures in one precompile instruction | `num_signatures == 1` | `ed25519_two_sigs` |
| Ed25519 instruction not adjacent | Builder places ComputeBudget first; program checks `cur − 1` | `ed25519_not_adjacent` |
| Merkle second preimage | Leaf prefix `0x00`, node prefix `0x01` | `node_as_leaf` |
| Proof-length DoS | Depth ≤ 16 | `proof_too_long` |
| Cycle reset abuse | Cycle uses the on-chain slot only | `cycle_reset` |
| Category bitmap overflow | `category < 256`, word/bit split | `category_255`, `category_256` |
| Wrong-mint payout | `payout.mint == vault.mint` | `wrong_mint` |
| Owner co-sign bypass | `has_one = owner` + `is_signer` | `cosign_required` |
| Lighthouse omitted | In-program post-condition always runs; `REQUIRE_LIGHTHOUSE` introspection | `lighthouse_missing` |
| Bond withdrawn before disputes | Unlock delay; no withdrawal while disputes are open | `bond_unlock_blocked` |
| Upgrade-authority abuse | Disclosed; multisig or freeze for production | — |

## Residual-risk statement (for the README and deck)

> Tally guarantees that an agent payment goes to a registered merchant's registered payout, for an item and price that merchant signed in a fresh menu, within the owner's caps and categories, and that the vault loses exactly that price and nothing more. Any choice pricier than the cheapest match in its category, any long-lived menu, and any under-bonded merchant needs the owner's Seed Vault signature. A merchant whose signed menu contradicts its own floor can be slashed by anyone. Tally does not prove the buyer is physically present, does not judge whether prices are fair beyond the merchant's own signed floor, and cannot stop a user who approves a prompt without reading it.
