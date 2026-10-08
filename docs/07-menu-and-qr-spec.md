# 07 — Signed menu, QR and NFC specification

## Goals

- One merchant signature covers a whole menu.
- The full catalog never has to be loaded into SVM memory. Only one leaf and its proof go on-chain.
- The phone can build a Merkle proof for any item without a server.
- Nothing in the QR or NFC payload decides where the money goes. The payout comes from the registry.
- The same payload works over **QR** (CameraX + ML Kit) and **NFC** (HCE, ISO/IEC 7816-4).

## Data model

### Item (leaf)

| Field | Type | Example |
|---|---|---|
| `item_id` | [u8; 16] (UUID / random) | `0x5f1c…a2` |
| `category` | u16 (0–255 used in v1) | 3 = coffee |
| `price` | u64, micro-USDC | 4_500_000 ($4.50) |
| `category_floor` | u64 | 4_500_000 (cheapest active coffee) |

Display-only fields (`name`, `description`, `image`, promo copy) are **not** in the leaf. They go to the **display channel** only and never reach the agent's model context (see [09](09-agent-design.md)).

### Leaf and node hashing

```
leaf = H(0x00 || item_id[16] || category_le16 || price_le64 || floor_le64)     // 35-byte preimage
node = H(0x01 || min(a,b) || max(a,b))                                          // sorted pairs
```

- `H` is chosen by `hash_alg` in the header: **0 = SHA-256** (v1, uses the `sol_sha256` syscall), **1 = BLAKE3** (reserved).
- **Why not BLAKE3 now?** The earlier draft specified BLAKE3. Solana's BLAKE3 syscall is feature-gated and wasn't active on mainnet when this was written. Hashing BLAKE3 in pure BPF would use far more compute. Turn on `hash_alg = 1` only after the feature is active on the target cluster.
- An odd leaf at any level is promoted unchanged. The prefix bytes stop a node from being passed off as a leaf (second-preimage protection).

### Signed header: 144 bytes

| Offset | Size | Field |
|---|---|---|
| 0 | 16 | `b"TALLY_MENU_V1\0\0\0"` |
| 16 | 32 | `program_id` |
| 48 | 32 | `merchant_pda` |
| 80 | 32 | `menu_root` |
| 112 | 8 | `issued_slot` |
| 120 | 8 | `expires_slot` |
| 128 | 8 | `menu_nonce` (64-bit random or counter) |
| 136 | 4 | `item_count` |
| 140 | 1 | `hash_alg` |
| 141 | 1 | `header_version` = 1 |
| 142 | 2 | reserved = 0 |

The merchant signs the **raw 144 bytes** with Ed25519, which hashes internally with SHA-512, so the header is not pre-hashed. `program_id` and `merchant_pda` bind the menu to one deployment and one registry entry.

### Floor rule (merchant side)

When it builds a menu, Merchant mode computes `floor[C] = min(price)` over active items in `C` and writes that value into **every** leaf of category `C`. A menu that breaks this rule can be slashed with `prove_floor_fraud` (see [06](06-onchain-program.md)). The merchant app refuses to sign such a menu, so honest merchants can't trip over it.

## Category table (v1)

| Code | Category | Code | Category |
|---|---|---|---|
| 0 | Food / lunch | 5 | Groceries |
| 1 | Drinks (non-coffee) | 6 | Digital goods |
| 2 | Snacks | 7 | Services |
| 3 | Coffee | 8 | Fees / tips (never autonomous by default) |
| 4 | Transit | 9–255 | Reserved |

The capsule stores a 256-bit bitmap. Codes above 255 are rejected in v1.

## Freshness tiers

| Source | Window | Agent may pay alone? |
|---|---|---|
| **Live terminal** (rotating QR or NFC HCE) | ≤ **150 slots** (~60 s) | **Yes**, if all other invariants hold |
| Printed / long-lived QR | 151–9,000 slots | **No.** The owner must co-sign, and the capsule must allow extended windows. |
| Anything longer | > 9,000 | Rejected (`WindowTooLong`) |

Merchant mode re-signs **every ~20 s** (about 50 slots), so a scan always has at least ~40 s left before it expires. Each rotation increments `menu_nonce`.

## Payload (shared by QR and NFC)

CBOR map, version 1:

```
{
  v: 1,
  m: merchant_pda (32 B),
  r: menu_root (32 B),
  is: issued_slot, ex: expires_slot, n: menu_nonce, c: item_count, h: hash_alg,
  s: signature (64 B),
  i: [ [item_id(16B), category, price, floor, "display name"], ... ],   // mode A
  u: "https://…/menu/<root-hex>.cbor"                                   // mode B (instead of i)
  d: { item_id: "description" ... }                                     // optional, display channel only
}
```

### QR (CameraX + ML Kit)

- URI form: `tally:v1?d=<base45(CBOR)>`. Use Base45 for QR alphanumeric mode, with error correction level **M**.
- **Mode A (inline):** up to about 20 items. Header plus signature is about 200 B, and each item about 40–55 B with a short name.
- **Mode B (by reference):** the QR carries the header, the signature and `u`. The phone downloads the list and **rebuilds the root**, and rejects the list if the root differs. The server can't change prices without breaking the signature.
- Rotating codes are redrawn on each re-sign. ML Kit decodes a high-density code (version 15–25) in about 100 ms on a Seeker-class device. Measure this.

### NFC: Host Card Emulation, ISO/IEC 7816-4

The merchant phone runs a `HostApduService`. The customer's Seeker reads it in **reader mode** (`NfcAdapter.enableReaderMode`, `FLAG_READER_NFC_A | FLAG_READER_SKIP_NDEF_CHECK`).

| Step | C-APDU (customer → merchant) | R-APDU |
|---|---|---|
| 1. Select | `00 A4 04 00 07 F0 54 41 4C 4C 59 01` (proprietary AID `F0 'TALLY' 01`) | `90 00` + version byte |
| 2. Get length | `80 CA 00 00 02` | total CBOR length (u16) + `90 00` |
| 3. Get chunk *k* | `80 B0 HI LO Le` (offset = `HI<<8 | LO`, Le ≤ 240) | chunk + `90 00` |
| 4. Done | — | the customer verifies locally; no further APDUs |

- The AID is registered in `apduservice.xml` with `android:category="other"` (not payment), so it doesn't conflict with tap-to-pay wallets.
- Chunks are ≤ 240 bytes, which keeps them within short-APDU limits. A 2 KB menu takes about 9 round trips, which is under 300 ms.
- The merchant serves whatever **signed** payload is current, so a tap always gets a fresh window.
- NFC carries nothing a QR doesn't, and the same verification applies.

## Phone-side verification (before any transaction)

1. Decode; check `v`, domain, `header_version`, `hash_alg`.
2. Fetch the `Merchant` PDA `m`. Reject it if it's missing or inactive. Show the name, **SKR bond**, slashed total and PoW mode.
3. Rebuild the 144-byte header and verify the Ed25519 signature against `merchant.signing_key`.
4. Rebuild the Merkle root from the items and reject on mismatch. Check `item_count`.
5. Check the slot window against the current slot (RPC `getSlot`). Label the menu **Live** or **Extended**.
6. Recompute each category's floor and reject the menu if a signed floor disagrees. This is the same contradiction `prove_floor_fraud` checks. Offer **"Report"**, which submits the fraud proof.
7. Split into channels: display fields → UI; structured fields → firewall (see [09](09-agent-design.md)).

## Test vectors

Commit `fixtures/menu-v1.json` containing: a test merchant keypair and PDA, 8 items across 3 categories, leaves, root, header bytes (hex), signature, a proof for every item, one deliberately inconsistent "fraud" menu, and an HCE transcript. **Rust, TypeScript and Kotlin must reproduce every byte.** This catches endianness bugs early.
