# 04 — Competitive landscape and the gap

**Every piece of Tally exists somewhere. No project we found puts them together: a merchant-signed offer, checked against the user's signed rules, inside an on-chain Solana program, with exact-outflow post-conditions, driven from a phone at a physical counter.** Claim that slice and credit everything else.

## Perimeter vs decision

Existing systems are built for **account-level perimeter security**. They control how fast capital can leave and how much can be withdrawn in total. They can't tell whether a single payment is for the product the user actually authorised.

| Project | Primary enforcement layer | Verification granularity | What it misses in agentic commerce |
|---|---|---|---|
| Squads spending limits | Smart-account PDA | Amount per window, destinations, signers | Can't see items, markups or add-ons |
| Fuse Wallet | Smart account | Daily allowances, allowlists | An inflated price at an approved counter |
| Cloaked Agent | Program session key | Daily caps, per-token limits, expiry, freeze, ZK mode, x402 | Can't tell which item the agent chose |
| Praxis (HackQuest) | Account policy engine | Spend cap per agent key | Substitutions under the cap |
| AgentGuard Protocol | Program-level guard | Policy vault, merchant allowlist, pause | Upsell or surcharge from an allowlisted merchant |
| wormhole-x402 | Off-chain client SDK | Quote signature; scans quote text | Skipped if the client or harness is compromised; no intent rules |
| Google AP2 | Card and crypto rails, off-chain | Signed intent, cart and payment mandates | No chain enforces it; the Whisper paper shows the gaps |
| Mastercard Verifiable Intent | Card networks | After-the-fact proof of authorisation | Audit, not prevention |
| Lighthouse (Jac0xb) | SVM assertion program | Arbitrary balance and data assertions | General-purpose; no notion of offers or catalogs. It's a building block, **and Tally uses it.** |
| Blowfish / Phantom simulation | Wallet | Preview before signing | Pre-sign only; drift; net-vs-gross |
| SeekerClaw (Monolith winner) | App | AI agent on Seeker | Not a payment-binding layer; a candidate **client** of Tally |
| **Tally** | **Native SVM program + Seed Vault** | **Merkle offer proofs, cheapest-match, exact gross outflow** | Binds intent on-chain, atomically |

## Tally's differentiators

1. **Decision binding on-chain.** The program verifies the *item* (Merkle proof) and the *price* (signed leaf), not just the amount and recipient.
2. **Cheapest-Match Invariant enforced by the chain.** A choice above the floor needs the owner's signature in the same transaction.
3. **Payee from a registry, never from the QR.** This removes the whole payee-swap class of attacks.
4. **Hardened introspection.** Ed25519 offset sanitisation closes a known Solana signature-verification bug class.
5. **Exact-outflow post-conditions.** The check is in-program, plus Lighthouse. It holds under drift and fee-bearing tokens.
6. **Mobile-native at the counter.** Camera and NFC HCE ingest, Seed Vault co-sign, Second Look gross-outflow sheet, Glance widget.
7. **Crypto-economic accountability.** SKR bond tiers, **trustless fraud-proof slashing**, SGT anti-sybil, and an ORE-PoW anti-flood gate.

## How to talk about competitors in the deck

> "Cloaked, Praxis, AgentGuard and Squads answer *how much* and *to whom*. Lighthouse answers *did the balance change correctly*. Tally answers *which item, at which signed price, from which registered merchant*, and asks you when the agent picks the pricier one."

Credit them by name. Judges know these projects, and naming them shows you did the homework.

## Risks to the novelty claim

- Another CLOCK IN team may build something similar. The space is hot.
- AP2 or Verifiable Intent may add a Solana settlement path.
- **Don't say "first ever".** Say "No Solana project we found enforces a merchant-signed offer against user rules on-chain."
- Mitigation: lead with the **demo** (on-chain reverts) and the **research thesis**.
