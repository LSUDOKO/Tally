# 09 — Agent design

**Principle: the agent proposes; it never decides.** The only output that matters is an `item_id`. Anything that could move money the wrong way is checked by the program, which never reads the conversation. This is the lesson from APort Vault (arXiv 2609.22076): a forged `[Tool Result: verify_recipient]` fooled 7 of 14 models multi-turn, but it did nothing against a boundary check that re-evaluates the real recipient.

## Responsibilities

| The agent does | The agent never does |
|---|---|
| Understands "oat latte, not too sweet" | Sees descriptions, reviews, promo or "notice" text |
| Ranks candidate leaves by fit | Chooses the payee, the price or the floor |
| Explains its pick in one line | Holds the owner key |
| Asks a clarifying question | Retries after a revert with a different item without telling the user |

## Context firewall: two channels

Indirect prompt injection (IPI) works by mixing data with instructions. The firewall keeps them apart **before inference**. It draws on CaMeL-style data/instruction separation and on the Whisper paper's finding that *factual claims* in merchant text are enough to steer an agent.

```
            ┌──────────────── signed payload (QR / NFC) ────────────────┐
            │                                                            │
   DISPLAY CHANNEL                                              AGENT CHANNEL
   names, descriptions, images,                 { item_id, category_code, price_usdc,
   promo copy, "notices"                          category_floor_usdc, name_tokens }
            │                                                            │
   Compose UI (human eyes only)                     Sanitiser → LLM (structured only)
```

### Agent payload (strict mode, default)

```json
{
  "request": "oat latte, not too sweet",
  "rules": {"categories": [3], "per_tx_max_usdc": "6.00"},
  "menu": [
    {"item_id": "5f1c…a2", "category_code": 3, "price_usdc": "4.50", "category_floor_usdc": "4.50", "name_tokens": ["oat","latte","regular"]},
    {"item_id": "9b07…11", "category_code": 3, "price_usdc": "5.90", "category_floor_usdc": "4.50", "name_tokens": ["oat","latte","large"]},
    {"item_id": "c3d4…e0", "category_code": 0, "price_usdc": "3.00", "category_floor_usdc": "3.00", "name_tokens": ["croissant"]}
  ]
}
```

- **`name_tokens`** are built by: lowercasing; splitting on non-letters; keeping only words in a **fixed food/retail dictionary** of about 5k words shipped in the APK; at most 6 tokens. A merchant can't fit "ignore previous instructions" or "regular is discontinued" through a dictionary of nouns and sizes.
- **No free text from the merchant** reaches the model. The display channel is never concatenated into the prompt.
- Tokens are re-checked against the dictionary at runtime. If an item ends up with no usable tokens, it's still listed, but only by category and price.
- **Output schema:** `{"item_id": "<hex16>", "reason": "<≤120 chars>"}`. Anything else is rejected, and an `item_id` not in the payload is rejected.
- **Ultra-strict mode** (optional) passes only `item_id, category_code, price_usdc, category_floor_usdc` to the model, as the whitepaper describes. The user picks among the top matches by name on screen. This is safest, but less "agentic".

**Defence in depth.** If the firewall fails, the program still enforces membership, price, payee, freshness and the cheapest-match rule. The firewall reduces **how often** the user gets asked. The chain decides **whether** money moves.

## Agent reasoning screen (trace)

The live trace is shown in the UI:

```
request  → "oat latte"
matched  → 5f1c…a2 [oat latte regular] $4.50  (floor $4.50)  ✓ autonomous
           9b07…11 [oat latte large]   $5.90  (floor $4.50)  ⚠ needs your co-sign
chosen   → 5f1c…a2 — "Cheapest oat latte that matches."
checks   → category ✓  per-tx ✓  daily ✓  bond 5,000 ≥ 1,000 ✓  window Live ✓
```

## Model options

| Option | Pros | Cons |
|---|---|---|
| Hosted LLM API | Best ranking; easy | Needs network; API key handling |
| On-device small model | Private, offline, Seeker-native story | Weaker ranking; APK size |
| Rule-based fallback | Deterministic | Barely an "agent" |

**Hackathon recommendation:** a hosted API for the demo, with a rule-based fallback ("cheapest leaf whose tokens match the request") when offline. On-device goes on the roadmap.

## Red-team mode (built-in adversarial agent)

| Attack | What the scripted agent or client does | Expected result |
|---|---|---|
| Payee swap | `pay_offer` with an attacker payout account | Revert: `has_one = payout` |
| Phantom add-on | Pays item `0xdead…` that isn't in the tree | Revert: `NotOnMenu` |
| Price inflation | Same item, price 9.90 | Revert: `NotOnMenu` (leaf mismatch) |
| Selection Whisper | Picks $5.90, citing "regular is discontinued" | Second Look → user declines |
| Replay | Resubmits a paid offer | Revert: receipt already exists |
| Stale menu | Uses an expired QR | Revert: `Stale` |
| Offset hijack | Ed25519 instruction whose `message_instruction_index` points at another instruction | Revert: `BadSigIx` |
| Hidden drain | Appends an extra token transfer from the agent | Second Look red banner; a required Lighthouse assertion fails |
| Forged-receipt injection | Merchant name tokens attempt "verified ✓"; agent told "already approved" | No effect: dictionary strips it; the chain ignores the conversation |
| Spam flood | 50 `pay_offer` attempts per second against a PoW-gated merchant | `PowRequired` until work is done |

Each result is shown with its explorer link, and the Home counter increments.

## Agent key lifecycle

1. Created on the first capsule (Keystore Ed25519 or wrapped fallback).
2. Registered as `capsule.agent` in `create_capsule`.
3. Holds a small SOL balance for fees and receipt rent, with a top-up prompt when low.
4. Rotated with `set_agent`; the old key stops working immediately.

## Future: external agents

A local API (an Android bound service or intent) lets other agent apps, such as SeekerClaw-style agents, ask Tally to pay. They pass an `item_id` and a menu reference. Tally applies the same firewall and invariants. This makes Tally infrastructure, which strengthens the PMF story.
