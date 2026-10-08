# 10 — Advanced features

Build **Core** for the submission. **Next** and **Later** go on the roadmap slide. Each feature names the finding or criterion that justifies it.

## Core (in the demo)

| Feature | What it does | Why | Effort |
|---|---|---|---|
| Signed-menu payments | Agent pays only for a Merkle-proven item from a merchant-signed 144-byte header | Display binding (2609.11757) | High |
| Hardened introspection | Ed25519 `u16::MAX` indices, offsets ≥ 16, bounds, single sig | Known Solana sig-verify bug class | Medium |
| Registry payout | Money goes to the merchant's registered account, never a payload field | Entity binding (2609.11757) | Low |
| Cheapest-Match Invariant | Above-floor choice needs owner co-sign, enforced on-chain | Spending surface (2609.11757) | Medium |
| 150-slot freshness tiers | Live menus are autonomous; extended windows need co-sign | Stale replay; witnessing zones (2608.04957) | Low |
| **Exact-outflow post-conditions** | In-program `vault Δ = −P`, `payout Δ = +P`; Lighthouse terminal assertion | State drift + receipt check (2610.00354) | Medium |
| Capsule caps + freeze | Per-tx, rolling cycle, 256 categories, expiry slot, kill switch | Bounded loss if the agent key leaks | Medium |
| Context firewall | Two channels; agent sees structured fields and dictionary tokens only | Factual-premise injection (2609.11757) | Low |
| Second Look sheet | Gross outflow, cheaper alternative, extra-instruction alarm | Simulation phishing (2607.28747) | Medium |
| QR + **NFC HCE** ingest | CameraX/ML Kit and ISO 7816-4 APDU tap | Mobile-native UX (25%) | Medium |
| Red-team mode | Ten attacks, live results with explorer links | Presentation (25%) | Low |
| Merchant terminal mode | Build, sign, rotate, broadcast in the same APK | Answers "who signs the menu?" | Medium |
| SKR bond tiers + **fraud-proof slashing** | Bond needed for autonomy; contradictory floors slashable by anyone | SKR prize, accountability | Medium |
| SGT gate | SIWS + SGT check; on-chain `SgtClaim` for promos | Anti-sybil for merchant promos | Low–Medium |
| ORE DrillX PoW gate | Merchant-enabled anti-flood work requirement | DoS on terminals; ORE angle | Medium |
| Glance widget | Balance, 24 h use, attacks blocked | Stickiness | Low |

## Next (post-submission, first month)

| Feature | What it does | Why |
|---|---|---|
| Receipt disputes + arbiter slashing | Non-provable disputes (delivery) resolved by multisig | Real commerce |
| Bond unlock delay | Withdraw after N days, blocked during disputes | No bond-cheat-leave |
| Order-ahead links | Same signed menu as a Solana Pay link / Blink | Daily use beyond the counter |
| Recurring capsules | "My usual, weekdays, under $5" | Stickiness |
| Quantity / multi-item carts | Several leaves, one receipt; batch Merkle multiproof | Real orders |
| On-device model | Private, offline agent | Seeker-native story |
| External agent API | Other agent apps pay through Tally | Becomes infrastructure |
| BLAKE3 leaves | Switch `hash_alg` once the syscall is active | Cheaper hashing |

## Later

| Feature | What it does | Why |
|---|---|---|
| x402 capsules | Same rules for agents paying per API call | Big agent-payments market |
| Cross-agent intent settlement | One agent's signed intent fulfilled by another merchant agent | Agent-to-agent commerce |
| Refunds | Merchant refunds against a `Receipt` | Real commerce |
| Merchant reputation | Receipt count, dispute rate, slashed total on scan | Trust at a glance |
| Privacy mode | Hide which items a user buys (commitments) | Shopping privacy |
| Background ORE mining → fee offset | Idle Seekers mine to cover priority fees (if current ORE supports it) | Self-funding micropayments |
| Seeker Witness | Nearby Seekers co-attest presence for high-value buys | Addresses the relay limit |

## Stickiness design

- **Daily loop:** morning coffee capsule → tap → done in 3 seconds.
- **Weekly loop:** "This week: 9 purchases, $38, 2 attempts blocked." A shareable card.
- **Social:** a "Tally-verified · 5k SKR bonded" sticker for counters. Users see the badge on scan.
- **Metrics for the deck:** weekly active capsules, and attacks blocked per user per week.
