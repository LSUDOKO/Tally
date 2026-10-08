# 13 — Hackathon requirements mapped to Tally

Source: the CLOCK IN rules pasted at the start of planning (Radiants × Solana Mobile, 2026). **Confirm the deadline on the official site** — one page said October 8, the Radiants page said **12 Oct 2026, 5:29 pm IST**.

## Hard requirements

| Rule | How Tally meets it | Evidence for judges |
|---|---|---|
| Android only; functional APK | Native Kotlin app | Signed release APK in GitHub Releases |
| Must integrate Solana Mobile Stack + MWA | MWA 2.0 SIWS onboarding; capsule creation, funding, co-sign via Seed Vault biometrics; SGT check | Demo shows the Seed Vault prompt |
| Mobile from the ground up; no PWA wrappers | Compose UI, CameraX + ML Kit, NFC HCE (ISO 7816-4), Keystore agent key, haptics, Glance widget | Screen recordings on device |
| Interact meaningfully with Solana | Custom Anchor program: ten invariants, hardened Ed25519 introspection, Merkle proofs, SPL transfers, in-program post-conditions + Lighthouse, fraud-proof slashing | Explorer links to paid and reverted txs |
| Runs on a device, not only a simulator | Demo filmed on two physical phones | Video |
| Repo someone else can clone and run | README with exact steps, devnet program ID, fixtures | 17-repo-structure |
| 3-minute demo | Script in 14-demo-script | Video ≤ 3:00 |
| Pitch deck | Outline in 15-pitch-deck | PDF in repo |

## Eligibility

| Rule | Note |
|---|---|
| Project started within 3 months of launch | New project — keep commit history from day one |
| No VC/angel funding for USDC prizes | Confirm for your team |
| One submission per contestant | Don't submit variants |
| No false or misleading info | Don't overclaim novelty or numbers (see 01-pitch) |
| Publish on dApp Store within 30 days of winning | Plan for mainnet-ready build and store listing |

## Judging criteria (25% each)

| Criterion | Tally's answer | Moment in the demo |
|---|---|---|
| Stickiness & PMF | Daily coffee/lunch/transit capsules; recurring orders; attacks-blocked counter; merchant badge; SGT-gated promos | Home + Glance widget with rolling balance and Attacks Blocked |
| User experience | One tap (NFC) or scan, one sentence, zero taps when honest; human only on exceptions; plain-language errors; haptics | Tap → paid in seconds; Second Look gross-outflow sheet |
| Innovation / X-factor | On-chain decision binding: Merkle offer proofs, cheapest-match, offset hardening, exact outflow, trustless floor-fraud slashing. No Solana agent wallet we found does this | Whisper attack → co-sign prompt; offset hijack → revert |
| Presentation & demo | Split-screen vs a capped wallet; live attacks, live reverts, honest limits | Explorer showing failed txs; PoW flood stalled |

## Evaluation process line by line

| Process item | What to make sure of |
|---|---|
| Completion based on demo video | Every core feature visible in the video |
| Technical depth based on GitHub commits | Small, frequent, descriptive commits; tests per attack |
| Mobile-optimised UX and mobile features | Camera, NFC, Seed Vault, widget, haptics all shown |
| Usage and interaction with Solana | Devnet program ID in README; explorer links in video |
| Clarity and vision from the presentation | Research thesis slide + roadmap slide |

## Judges and what to show each

| Judge | Likely interest | Show |
|---|---|---|
| Anatoly Yakovenko | Real-world utility, Solana-native design | Atomic on-chain checks; sub-second UX |
| Mert Mumtaz (Helius) | RPC, landing rate, infra | Simulation pre-check; Helius priority-fee estimates; reliable landing inside 150-slot windows |
| Chase Barker (Solana Foundation) | Ecosystem growth, founders | Merchant GTM; agent-developer API |
| Akshay Rajan, Beeman (Solana Mobile) | Seeker-native UX, dApp Store fit | Seed Vault co-sign, MWA 2.0 SIWS, SGT gate, NFC HCE, Glance widget |
| Voynich, A2nkF (EthelSec) | Security, attack surface | 20-row threat model, Ed25519 offset hardening, post-conditions, honest limits |

## Optional prizes

| Prize | Tally plan |
|---|---|
| $10k SKR integration | Merchant bond tiers + trustless fraud-proof slashing (12-skr-and-merchants) |
| ORE matched prize (whitepaper cites $10k) | **Conditionally pursued.** Tally integrates ORE's DrillX PoW as a merchant anti-flood gate (20-sgt-and-ore). Earlier rule reading said ORE must be the *primary user-facing product* and one matched prize spans the Top 10. **Confirm eligibility on the official page**; keep the PoW gate regardless — it is a real security feature. |
| SGT / Seeker-native | Not a prize line, but strengthens the Solana Mobile judges' read: SIWS + SGT anti-sybil |
