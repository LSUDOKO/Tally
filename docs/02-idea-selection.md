# 02 — How we picked the idea

Every candidate considered during research, scored against the hackathon's four equal criteria plus buildability. Scores are our judgement on a 1–5 scale.

## Scorecard

| Idea | Stickiness | UX | Innovation | Demo | Buildable | Verdict |
|---|---|---|---|---|---|---|
| **Tally** (signed-menu decision binding) | 4 | 4 | 5 | 5 | 4 | **Build this** |
| Seeker Witness (phones vouch for presence) | 5 | 4 | 4 | 4 | 2 | Strong, but hard to make secure |
| Second Look (pre-sign transaction check) | 2 | 4 | 3 | 4 | 5 | Fallback; folded into Tally as a feature |
| ChronoDrop (ZK location airdrops) | 3 | 4 | 3 | 4 | 2 | ZK protects a spoofable GPS input |
| ProofPulse (private participation proofs) | 3 | 3 | 3 | 2 | 2 | Abstract demo, crowded space |
| ContextKey (context-bound agent capability) | 4 | 4 | 2 | 4 | 3 | Core already exists; became Tally |
| GhostKey / AegisMWA (agent wallet firewall) | 3 | 4 | 1 | 4 | 3 | Crowded: Cloaked, Praxis, AgentGuard |
| OreForge (phone ORE mining swarm) | 3 | 3 | 3 | 2 | 1 | Weakest — see below |

## Why each runner-up lost

### OreForge
- *Revisited:* ORE is now used **inside** Tally as a DrillX PoW anti-flood gate (see 20-sgt-and-ore), which is security-relevant. Phone mining as a product remains rejected for the reasons below.
- Phones are unlikely to contribute meaningful hashing inside ORE's rounds; thermal throttling and battery drain are real.
- ORE awards **one** matched prize across the entire Top 10, milestone-paid. The "$70k stacking" math in the original write-up was wrong.
- Highest build complexity: Rust hashing core, attestation, nonce sharding, SKR staking.

### ContextKey (the starting point)
- Its core — a capped, expiring, revocable key for an agent — already exists on Solana (Cloaked Agent, Praxis, AgentGuard, Squads spending limits).
- Its unique part, GPS context, is spoofable on Android (see the GNSS-spoofing survey and proof-of-location papers in 03-research).
- **What we kept:** capsules, natural-language rules, phone approval. **What we replaced:** GPS context → merchant-signed offers.

### Second Look
- Real problem, grounded in the transaction-simulation-phishing paper, easy to build.
- Weak stickiness: security scanners are opened rarely.
- **Kept inside Tally** as the approval screen (gross outflow + unexpected-instruction warnings).

### Seeker Witness
- Fun and sticky, and it would upgrade Echo Protocol (a past winner whose GPS rewards are spoofable).
- Phone Bluetooth cannot do the precise distance bounding the witnessing-zone paper used; colluding phones can lie; a convincing demo needs many devices.
- Good **post-hackathon** idea.

## Evolution of the idea

```
GhostKey / AegisMWA  ──(crowded)──►  ContextKey
ContextKey           ──(core exists, GPS spoofable)──►  OfferLock
OfferLock            ──(agent did nothing if user scans one item)──►  signed-menu version
signed-menu version  ──(renamed)──►  Tally
Tally v1             ──(whitepaper hardening)──►  Tally v2: ten invariants, 144-B header,
                                                   150-slot windows, exact-outflow post-conditions,
                                                   NFC HCE, fraud-proof slashing, SGT + ORE PoW
```

## The judge question that shaped the final design

> "If the user scans the exact item and approves it, what does the agent do?"

Early OfferLock bound the capsule to **one** item — the agent added nothing. Tally binds to a **signed menu** and **user rules**; the agent chooses among items, and the program verifies the choice. That keeps the agent useful and the protection real.
