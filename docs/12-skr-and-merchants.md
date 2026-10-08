# 12 — SKR integration and merchant onboarding

## SKR's role: a bond that agents can price

**SKR is the bond a merchant posts to sign menus that agents will trust, and the collateral that pays users when the merchant cheats.** That's a security function, not a decorative reward.

| Mechanism | How it works | Status |
|---|---|---|
| Merchant bond | `register_merchant` transfers SKR into bond vault PDA `["bond", merchant]` | Core |
| **Trust tiers** | Capsule sets `min_merchant_bond`. Below it, **every** purchase needs a Seed Vault co-sign. At or above it, the agent may clear **floor-priced** items alone. | Core |
| Verified badge | Scan screen shows bond size and slashed total next to the merchant name | Core |
| **Fraud-proof slashing** | `prove_floor_fraud`: anyone submits two leaves from one signed menu where `price_a < floor_b` in the same category. Automatic slash; reporter reward plus user pool. | Core (demo-able) |
| Bond unlock delay | `request_bond_unlock` → wait `BOND_UNLOCK_DELAY` (e.g. 7 days) → `withdraw_bond`, blocked while disputes are open | Next |
| Receipt disputes | Capsule owner opens a `Dispute` against a `Receipt` (wrong goods, not delivered). Arbiter multisig upholds or rejects; upheld disputes slash to the claimant. | Next |
| Reputation | Receipts count, dispute rate and slashed total shown on scan | Later |

**What about "duplicate settlement" slashing?** It isn't needed. Duplicate charges already **revert** through the `Receipt` PDA, so there's nothing to slash. Slashing covers what the chain can't prevent up front: **contradictory floors** (provable) and **delivery disputes** (arbitrated).

**Avoid** burns, cash-back and staking yields for the hackathon. They add token mechanics judges may question and don't strengthen the security story.

### Trust tiers in one picture

```
                 merchant bond
     0 ───────────── min_merchant_bond ─────────────►
     │  every purchase → Seed Vault co-sign  │  floor-priced → autonomous
     │                                       │  above floor  → co-sign (cheapest-match)
```

### Pitch line for the SKR prize

> "On Tally, SKR is what makes a merchant trustworthy to an AI agent. No bond, no autonomy, so your agent has to ask you first. Sign a dishonest floor and anyone can slash you with your own signature."

## Merchant onboarding (3 steps, under 5 minutes)

1. **Open Merchant mode** in the same APK. It creates a menu-signing key in Keystore and asks the merchant's wallet (MWA) to `register_merchant` with name, payout USDC account and SKR bond.
2. **Enter the menu.** Item name, category, price. The app computes floors, **refuses to sign contradictory floors**, builds the Merkle tree and signs the 144-byte header.
3. **Show the QR and enable NFC.** A phone or tablet stays at the counter. It re-signs every ~20 s, redraws the QR and serves the same payload over HCE. Small vendors can print a long-window QR, which forces owner co-sign on the user side and is labelled "Extended".

Optional: switch **PoW congestion mode** on during rush hour (see [20](20-sgt-and-ore.md)).

## Go-to-market ("why would a merchant do this?")

1. **Agents route spend to verifiable merchants.** As agents buy more, merchants agents can't verify lose agent-driven orders.
2. **Fewer disputes.** Every order has an on-chain receipt tied to a signed price.
3. **No new hardware.** A phone they already own does QR and NFC.
4. **Zero platform fee** in v1. State this, and revisit later.
5. **Seeker-only promos** reach real devices, not bot farms (SGT gate).

## Launch plan (post-hackathon)

| Phase | Target | Tactic |
|---|---|---|
| Campus pilot | 5 cafés near one campus | Free setup, printed "Tally-verified" stickers |
| Seeker community | Events and hacker houses | Pop-up merchant mode at events; SGT-gated launch promo |
| Agent developers | 3 agent apps integrate the external API | SDK + example |
| dApp Store | Featured placement (if winning) | Weekly "attacks blocked" stats as marketing |
