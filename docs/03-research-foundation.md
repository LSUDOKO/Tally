# 03 — Research foundation

Tally is built on five 2026 papers plus supporting work. For each: what it found, its limits, and exactly which Tally feature it justifies. Numbers below are from the papers as read; check each paper before quoting it in the deck.

---

## 1. Signing the Transaction but Not the Decision (arXiv 2609.11757, Sept 2026)

**Authors:** Louck, Dvir, Stulman (Ariel University, Jerusalem College of Technology).

**What it found**
- Agent payment protocols such as Google's AP2 sign three mandates (intent, cart, payment). The signatures bind the transaction but **not the decision** that produced it.
- Merchant-controlled text can steer a shopping agent. Three attack families:
  - **Vault Whisper** — agent fetches another user's payment credentials: **90%** success on the preview model AP2's samples use.
  - **Branded Whisper** — cart corrupted (inflated price, unrequested add-on, redirected payee) yet still validly signed: **56%**.
  - **Selection Whisper** — a *factual claim* ("the cheaper item is discontinued") moves the agent to a pricier displayed item: **73.3%** on the GA model; succeeded on all 17 Google builds tested.
- Their defense, **A-VIP**, treats the signed intent as a **capability grant**:
  - **Entity binding** — payee must be the merchant that displayed the item.
  - **Display binding** — every cart line must match a displayed listing and price; totals must add up.
  - **Spending surface** — if the agent picks something pricier than the cheapest item meeting the request, ask the user.
- Structural binding closed the forged families with **zero false positives**; Selection leaves no structural trace and can only be **surfaced** for confirmation.

**Limits:** AP2-specific, off-chain; confirmation depends on the user actually reading.

**What Tally takes**
| Paper mechanism | Tally equivalent |
|---|---|
| Entity binding | Payout comes from the on-chain merchant registry, never from the QR |
| Display binding | Merkle proof that the item + price is in the merchant-signed menu |
| Spending surface | Cheapest-match rule: price above category floor → owner co-sign required on-chain |
| Content inspection is secondary | Context firewall: agent sees only structured fields |

---

## 2. APort Vault: Benchmarking AI Agent Payment Authorization (arXiv 2609.22076, Sept 2026)

**What it found**
- Replayed **4,371 human-written attacks** from a public CTF against **14 models**, 225,964 evaluations.
- With a **deterministic pre-action check** at the tool boundary, unpermitted transfers went from **140 to 0** (matched pairs 105 → 0), while legitimate payments kept executing.
- A forged "[Tool Result: verify_recipient]" message fooled 7 of 14 models multi-turn — but not the boundary check, which never reads the transcript.
- Model choice did not determine safety; the check in the execution path did.

- Zero carries a **session-clustered upper bound of 0.38%** (3/790 source sessions). The zero was not bought by refusing: 25,370 payments still executed behind the layer.
- The paper explicitly warns that request rates (e.g. 79.4% at Level 4) are **not** attack-success rates. *Note: an earlier Tally draft quoted "74.6% of permissive runs" — that figure does not appear in the paper; don't use it.*

**Limits:** Author is the vendor of the check (disclosed). Single simulated bank, one tool.

**What Tally takes:** enforcement lives in the **Solana program**, not in the agent's reasoning. The program never reads the conversation. Policy is the variable that matters, not model choice.

---

## 3. Proof-Gated Signing (arXiv 2610.00354, Sept 2026)

**What it found**
- Pre-signing checks (allowlists, LLM reviewers, simulation) are evidence about chain state **at check time**. Execution happens later — **state drift** (front-running, upgrades, fee changes) breaks them.
- Compiling the check into **on-chain post-conditions** executed atomically left **0 of 50** drift scenarios with attacker gain (40 reverted on-chain). Simulation-only checking **missed all 50 drift scenarios** and prevented 57.9% of harmful scenarios overall; a static allowlist missed 40 of 50.
- A **payee-receipt** check (did the payee actually receive the amount?) was needed to stop a fee-on-transfer drift attack; net balance checks on the sender alone missed it.
- A clean simulation made an LLM reviewer *more* likely to approve a drift attack.

**Limits:** Ethereum testbed designed by the author.

**What Tally takes:** every check happens **inside the paying transaction**. Invariant 10: the program re-reads balances and requires `vault Δ = −price` **and** `payout Δ = +price` (the receipt-check lesson), plus a terminal **Lighthouse** assertion as defence in depth.

---

## 4. Blockchain Transaction Simulation Phishing (arXiv 2607.28747, July 2026)

**What it found**
- Attackers build contracts that look profitable in wallet simulation but redirect funds on execution. **4,224** phishing contracts, **5,700+** victims, about **$3.48M** lost (EVM chains).
- Most of 11 tested wallets (including Phantom) showed only **net** balance change — a drain looked like a tiny gain — and not the **gross** amount leaving.

**Limits:** EVM only; Solana analogue is our extrapolation.

**What Tally takes:** the Second Look sheet shows **gross outflow** per account and disables approval if any instruction other than the expected set (Ed25519, `pay_offer`, Lighthouse, ComputeBudget) is present. It is shown *before* the Seed Vault prompt — Tally cannot draw on Seed Vault's own screen.

---

## 5. Toward Practical Decentralized Proof-of-Location via Physical Witnessing Zones (arXiv 2608.04957, Aug 2026)

**What it found**
- Self-reported GNSS location is easy to spoof. Fixed **witnesses** attesting presence, bound to a **fresh block hash**, rejected stale replays in a UWB prototype.
- The block-hash freshness check detects stale references but **does not prevent fresh relay**.

**What Tally takes:** the merchant is the witness — every menu carries a signed slot window, ≤150 slots (~60 s) for autonomous pay. NFC tap shrinks the practical relay window but does not eliminate it. We state the relay limit openly.

---

## Supporting work (abstracts read)

| Paper | Relevance |
|---|---|
| CAVEAT (arXiv 2609.27273) — robust computer-use agents in incentive-misaligned environments | Marketplaces have incentives that differ from the user's |
| Whom Do AI Agents Work For? (arXiv 2609.17989) | Role assignment induces sponsorship bias in LLM shopping assistants |
| Who Is Your Agent Serving? (arXiv 2610.05266) | Provider-side indirect prompt injection in proactive agents |
| Market Signal Injection (arXiv 2609.18357) | Presentation of market data, not values, steers pricing agents |
| GNSS Spoofing in Mobile Devices survey (arXiv 2610.01294) | Why GPS is not a trustworthy context signal |
| Can CaMeLs Talk? (arXiv 2610.05640) | Data/instruction separation defenses for multi-agent systems |

## Vulnerability → evidence → mechanism (deck table)

| Vector | Verified finding | Tally mechanism |
|---|---|---|
| Whisper steering | 73.3% Selection Whisper success (2609.11757) | Merkle catalog + Cheapest-Match Invariant |
| Model reasoning failure | 140 → 0 unpermitted transfers behind a deterministic boundary; 7/14 models fooled by forged tool result (2609.22076) | Policy inside the SVM |
| Pre-signing drift | Simulation-only missed 50/50 drift scenarios (2610.00354) | Atomic post-conditions + Lighthouse |
| Simulation-diff phishing | $3.48M, 4,224 contracts, net-not-gross display (2607.28747) | Gross-outflow Second Look |
| Location spoofing / relay | GNSS spoofable; freshness ≠ presence (2608.04957) | Slot-bounded merchant attestations over QR/NFC |

## The research thesis for the deck

> Five 2026 papers converge: agents can be steered by merchant text (2609.11757), only deterministic checks at the execution boundary hold (2609.22076), those checks must be atomic with execution (2610.00354), previews must show gross outflow (2607.28747), and presence/freshness must come from a witness, not the device (2608.04957). **Tally is that combination, enforced on Solana and operated from a phone.**
