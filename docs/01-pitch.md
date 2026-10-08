# 01 — Pitch and positioning

## One-liner

**Tally lets an AI agent shop for you, but it can only pay for an item the merchant signed and your rules allow — and a Solana program checks that before any money moves.**

## Technical one-liner (for engineers and security judges)

**Tally binds three proofs into one atomic Solana transaction (a merchant-signed catalog leaf, a Seed Vault-governed intent capsule, and hardened introspection with exact-outflow post-conditions) so a manipulated agent hits an on-chain revert instead of moving money.**

## Taglines (pick one per surface)

| Use | Line |
|---|---|
| Deck title slide | *Your agent proposes. The merchant signs. Tally checks it on-chain.* |
| Closing line of the demo | *Spending limits protect your balance. Tally protects your decision.* |
| dApp Store subtitle | *Let your agent pay — only for what tallies.* |
| Tweet / short | *If it doesn't tally, it doesn't pay.* |

## 30-second elevator pitch

> AI agents can now pay on our behalf. Everyone protects them with spending caps and allowlists. But 2026 research shows the real attack isn't stealing a big amount — it's one sentence of merchant text talking the agent into the wrong item (73% of the time in one study), a hidden add-on, or a different payee. Caps and allowlists pass those purchases. Another study across 14 models found only a deterministic check outside the model stopped bad transfers: 140 → 0.
>
> Tally fixes this on Solana. The merchant signs its menu. You sign your rules once in Seed Vault. When your agent buys, a Solana program checks the item is really on the signed menu, at the signed price, paid to the registered merchant, inside your rules — and if the agent picked something pricier than the cheapest match, it waits for your tap. Anything else reverts on-chain.
>
> Tap, say what you want, done. It's built for the Seeker: NFC tap or camera scan, Seed Vault approvals, SKR-bonded merchants, and an ORE proof-of-work gate so agents can't flood a counter.

## 2-minute pitch structure

1. **Hook (15 s).** "Your agent bought you the wrong coffee. Every check passed. Here's why."
2. **Problem (30 s).** Agents sign transactions, not decisions. Whisper-attack numbers.
3. **Why existing tools miss it (20 s).** Caps say *how much*, allowlists say *to whom*; neither says *which item*.
4. **Solution (30 s).** Signed menu + signed rules + on-chain check. Show the flow diagram.
5. **Proof (15 s).** Three attacks, three reverts on the explorer.
6. **Why Seeker (10 s).** Camera at the counter, Seed Vault approvals, one-tap co-sign.

## Who it is for

| Segment | Job to be done | Why Tally |
|---|---|---|
| Seeker owners using AI agents | Let the agent handle small daily purchases without fear | Bounded, explainable, revocable |
| Merchants accepting Solana Pay | Get agent-driven orders without disputes | Signed menus make orders unambiguous |
| Agent developers | Give their agent a safe way to pay | One instruction to call; rules enforced for them |

## Positioning statement

For **Seeker owners who let AI agents spend for them**, Tally is the **on-chain purchase guard** that **binds every agent payment to a merchant-signed offer and the user's signed rules**. Unlike **spending-limit wallets**, Tally **checks the item the agent chose, not just the amount**.

## What not to say (overclaims to avoid)

- ❌ "Nothing like this exists." → ✅ "No Solana project we found enforces a merchant-signed offer against user rules on-chain."
- ❌ "Spending caps fail 90% of the time." → The 90% figure in the paper is a credential-leak attack, not a cap test.
- ❌ "Proves you are physically at the shop." → A fresh signed QR proves freshness, not presence.
- ❌ "Stops all agent attacks." → It blocks eleven on-chain, surfaces three, bounds two, and names four out of scope (see 11-threat-model).
- ❌ "74.6% of attacks bypass LLM safety" (APort Vault) → not in the paper. Use 140 → 0 and the 0.38% bound.
- ❌ "Gross outflow is enforced on the Seed Vault screen." → It's on Tally's Second Look sheet before Seed Vault.
- ❌ "First protocol to…" → "No Solana project we found…"
