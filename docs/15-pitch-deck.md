# 15 — Pitch deck (10 slides)

The whitepaper's 8-slide outline is folded into this 10-slide version. Its slides map to 1, 2, 3, 4, 5, 7, 8 and 10 here. Slides 6 (live attacks) and 9 (research/threat boundary) are added because they score on Presentation and Innovation.

Design notes:

- One idea per slide, large type, dark background.
- Red for "blocked", green for "paid", amber for "surfaced to you".
- Screenshots from the real app, not mockups.

---

### Slide 1 — Title and vision
- **Tally**: an intent-binding settlement engine for autonomous mobile commerce
- *Your agent proposes. The merchant signs. Tally checks it on-chain.*
- Team name · CLOCK IN 2026 · Mobile

**Speaker note:** "Tally lets your AI agent shop, but only for what tallies."

---

### Slide 2 — The decision gap
- Agents now pay for us (AP2, x402, Seeker agents). They sign the **transaction**, not the **decision**.
- One sentence of merchant text steered agents to a pricier item **73.3%** of the time, and the cart still passed every check. *(Louck et al., arXiv 2609.11757)*
- Model choice didn't decide safety. A **deterministic boundary** took unpermitted transfers from **140 → 0** across 14 models and 225,964 runs. *(APort Vault, arXiv 2609.22076)*

---

### Slide 3 — Why perimeter controls miss it

| Tool | Checks |
|---|---|
| Spending caps (Cloaked, Praxis, Squads) | How much |
| Allowlists (AgentGuard) | To whom |
| Lighthouse | Did a balance change as asserted |
| Wallet simulation | What it looks like *now* (missed 50/50 drift attacks in PGS) |
| **Tally** | **Which item, at which signed price, from which registered merchant, on-chain** |

---

### Slide 4 — Protocol architecture
Diagram: **three proofs → one atomic transaction**.

- Merchant proof: signed header + Merkle leaf.
- Intent proof: Capsule via Seed Vault.
- Execution proof: introspection + exact outflow.
- Add the **context firewall** split: display channel vs agent channel.

---

### Slide 5 — `pay_offer`: ten invariants
Authority & liveness · Sysvar introspection · **Ed25519 offset hardening** · 150-slot freshness · Registry payout · **Merkle inclusion** · Caps & categories · **Cheapest-Match** · Receipt nullifier · **Exact outflow + Lighthouse**

**Speaker note:** "All ten run inside the same transaction that pays. If any fails, nothing moves. The offset check closes a known Solana signature-verification bug."

---

### Slide 6 — Live: attacks blocked
Screenshot grid:

- payee swap ❌
- phantom add-on ❌
- price tamper ❌
- stale menu ❌
- replay ❌
- offset hijack ❌
- Whisper upsell → 🟠 Second Look
- flood → PoW ❌

Explorer links for each.

---

### Slide 7 — Built for Seeker
Seed Vault biometrics + MWA 2.0 · Keystore agent key · **NFC tap (HCE, ISO 7816-4)** + camera scan · Second Look gross-outflow sheet · Glance widget · Merchant terminal in the same APK.

---

### Slide 8 — Token mechanics
- **SKR**: merchant bond. No bond means no autonomy. **Anyone can slash** a contradictory floor using the merchant's own signature.
- **SGT**: one Seeker, one identity. Promos can't be bot-farmed.
- **ORE**: DrillX proof-of-work gate stops agent floods at the counter.

---

### Slide 9 — Research and honest limits
- Five 2026 papers → five mechanisms (table from [03](03-research-foundation.md)).
- **Blocks** 11 attacks on-chain · **surfaces** 3 · **bounds** 2 · **out of scope** 4 (physical relay, floor omission, rubber-stamping, OS compromise).

---

### Slide 10 — Roadmap and ask
Counter retail → order-ahead Blinks → recurring capsules → external agent API → **x402 API micropayments** → **cross-agent intent settlement**.

Ask: "Help us put Tally at every Seeker-friendly counter."

**Closing line:** *Spending limits protect your balance. Tally protects your decision.*
