# 06 — On-chain program (Anchor)

> **Reference sketch.** This was written for clarity and review. It has not been compiled, tested or audited. Treat every line as a starting point.

## Design summary

- **One program**, four account types (`Merchant`, `Capsule`, `Receipt`, `Dispute`), and **one payment instruction**, `pay_offer`. It enforces **ten invariants** before and after moving USDC.
- Payments are in **USDC (SPL token)**, in micro-USDC base units (6 decimals).
- The merchant signature is checked with the **native Ed25519 precompile** and **instructions sysvar introspection**. The offset checks are hardened (invariant 3).
- Menu items are proven with a **Merkle proof**: SHA-256 in v1, with domain-separated leaves and nodes. The header's `hash_alg` byte reserves BLAKE3 for a later version (see [07](07-menu-and-qr-spec.md)).
- **Post-conditions**: the program re-reads balances after the transfer, which is mandatory. A terminal **Lighthouse** assertion adds a second layer, and a capsule can make it required.
- Optional gates: an **SGT** check at capsule creation, and a **DrillX PoW** check per merchant when that merchant turns on congestion mode.

## Constants

| Name | Value | Meaning |
|---|---|---|
| `DOMAIN` | `b"TALLY_MENU_V1\0\0\0"` (16 B) | Header domain tag |
| `HEADER_LEN` | 144 | Signed header size |
| `MAX_AUTONOMOUS_WINDOW` | 150 slots (~60 s) | Longest menu window an agent may pay alone |
| `MAX_EXTENDED_WINDOW` | 9,000 slots (~1 h) | Longest window at all; anything above 150 needs owner co-sign |
| `CYCLE_SLOTS` | 216,000 (~24 h at 400 ms) | Spending-cycle length |
| `MAX_PROOF_DEPTH` | 16 | Bounds compute (65,536 items) |
| `ED25519_HEADER_LEN` | 16 | `num_sigs u8` + `pad u8` + 7 × u16 offsets |

Slot time varies, so "24 h" and "60 s" are approximate. The UI shows them as approximate.

## Accounts

### `Merchant`, seeds `["merchant", authority]`

| Field | Type | Notes |
|---|---|---|
| `authority` | Pubkey | Merchant master wallet (MWA) |
| `signing_key` | Pubkey | Ed25519 menu key; rotatable |
| `payout` | Pubkey | USDC token account that receives payments |
| `name` | [u8; 32] | Display name (UTF-8, padded) |
| `skr_bonded` | u64 | SKR in bond vault `["bond", merchant]` |
| `bond_unlock_slot` | u64 | 0 = locked; otherwise withdrawable after this slot |
| `pow_difficulty` | u8 | 0 = off; otherwise DrillX difficulty required |
| `slashed_total` | u64 | Running total, shown on scan |
| `active` | bool | Authority can pause |
| `bump` | u8 | |

Seeding by `authority` instead of `signing_key` lets a merchant **rotate** a leaked menu key without losing its registry entry, receipts or bond.

### `Capsule`, seeds `["capsule", owner, capsule_id (u64 LE)]`

| Field | Type | Notes |
|---|---|---|
| `owner` | Pubkey | Seed Vault wallet |
| `agent` | Pubkey | Agent key (Keystore) allowed to call `pay_offer` |
| `mint` | Pubkey | USDC mint |
| `vault` | Pubkey | Token account owned by this capsule PDA |
| `per_tx_cap` | u64 | Max price per item |
| `daily_cap` | u64 | Max spend per cycle |
| `spent_in_cycle` | u64 | Running total |
| `cycle_start_slot` | u64 | Start of current ~24 h cycle |
| `allowed_categories` | [u64; 4] | 256-bit bitmap; bit *c* = category *c* |
| `min_merchant_bond` | u64 | SKR bond needed for autonomous pay |
| `expiry_slot` | u64 | Capsule unusable at or after this slot |
| `flags` | u8 | bit0 `REQUIRE_LIGHTHOUSE`, bit1 `SGT_VERIFIED`, bit2 `ALLOW_EXTENDED_WINDOW` |
| `sgt_mint` | Pubkey | Default if not SGT-verified |
| `frozen` | bool | Owner kill switch |
| `capsule_id` | u64 | |
| `bump` | u8 | |

### `Receipt`, seeds `["receipt", capsule, menu_nonce (u64 LE), item_id ([u8;16])]`

| Field | Type |
|---|---|
| `capsule`, `merchant` | Pubkey |
| `menu_root` | [u8; 32] (evidence for disputes) |
| `item_id` | [u8; 16] |
| `category` | u16 |
| `price`, `category_floor` | u64 |
| `slot` | u64 |
| `cosigned` | bool |
| `disputed` | bool |

Created with `init`. If the address already exists, the transaction fails and the **duplicate settlement is blocked**.

### `Dispute`, seeds `["dispute", receipt]`

| Field | Type |
|---|---|
| `receipt`, `claimant` | Pubkey |
| `evidence_hash` | [u8; 32] |
| `status` | enum { Open, Upheld, Rejected } |
| `slash_amount` | u64 |
| `opened_slot` | u64 |

## Instructions

| Instruction | Signer(s) | Effect |
|---|---|---|
| `register_merchant(name, signing_key, payout, bond)` | merchant authority | Create `Merchant`; transfer SKR into bond vault |
| `rotate_signing_key` / `set_payout` / `set_active` / `set_pow_difficulty` | merchant authority | Registry maintenance |
| `top_up_bond(amount)` | merchant authority | Add SKR |
| `request_bond_unlock()` / `withdraw_bond()` | merchant authority | Withdraw only after `BOND_UNLOCK_DELAY` slots, and only with no open disputes |
| `create_capsule(id, rules, agent, sgt?)` | owner (Seed Vault via MWA) | Create `Capsule` and vault ATA; optional SGT check |
| `update_rules(rules)` | owner | Change caps, categories, expiry, flags |
| `fund(amount)` / `withdraw(amount)` | owner | Move USDC in or out |
| `set_agent(new_agent)` | owner | Rotate agent key |
| `freeze()` / `unfreeze()` | owner | Kill switch |
| `close_capsule()` | owner | Return funds and rent |
| **`pay_offer(args)`** | agent; plus owner when required | **The ten invariants**, then pay |
| `prove_floor_fraud(header, leaf_a, proof_a, leaf_b, proof_b)` | anyone | Ed25519-verified menu shows `price_a < floor_b` with `cat_a == cat_b`. Slashes automatically. |
| `open_dispute(receipt, evidence_hash)` | capsule owner | Opens a `Dispute` |
| `resolve_dispute(upheld, slash_amount)` | arbiter multisig | Slashes bond to the claimant, or rejects |

## `pay_offer`: the ten invariants

| # | Invariant | Check | Error |
|---|---|---|---|
| 1 | **Authority and liveness** | `agent` signs and equals `capsule.agent`; `!frozen`; `slot < expiry_slot`; `merchant.active` | `Frozen`, `Expired`, `MerchantInactive` |
| 2 | **Sysvar introspection** | `load_instruction_at_checked(cur − 1)` has `program_id == ed25519_program::ID` and **no accounts** | `MissingSig`, `BadSigIx` |
| 3 | **Ed25519 offset hardening** | `num_signatures == 1`. All three `*_instruction_index == u16::MAX`. `sig_off`, `pk_off`, `msg_off` are ≥ 16. `sig_off+64`, `pk_off+32` and `msg_off+msg_len` are ≤ `data.len()`. `msg_len == 144`. pubkey == `merchant.signing_key`. msg == **rebuilt** header. | `BadSigIx`, `WrongSigner`, `WrongMessage` |
| 4 | **Temporal freshness** | `issued ≤ slot ≤ expires`. Window ≤ 150 slots, or ≤ 9,000 **and** owner co-signs **and** capsule allows extended windows. | `Stale`, `WindowTooLong` |
| 5 | **Registry payout** | `payout == merchant.payout` (`has_one`); `payout.mint == vault.mint`. There is no recipient argument. | `ConstraintHasOne`, `WrongMint` |
| 6 | **Merkle inclusion** | `verify_proof(leaf(I,C,P,F), proof, root)`, depth ≤ 16 | `NotOnMenu` |
| 7 | **Capsule policy** | Category bit set; `P ≤ per_tx_cap`; cycle reset if `slot − cycle_start ≥ CYCLE_SLOTS`; `spent + P ≤ daily_cap` | `Category`, `OverPerTx`, `OverDaily` |
| 8 | **Cheapest-Match Invariant** | `P > F` **or** `skr_bonded < min_merchant_bond` **or** extended window → `owner.is_signer` | `OwnerCosignRequired` |
| 9 | **Duplicate-settlement nullifier** | `Receipt` `init` | `AccountAlreadyInUse` (runtime) |
| 10 | **Atomic outflow and post-condition** | Transfer, then `reload()`; `vault_before − vault_after == P` and `payout_after − payout_before == P`. If `REQUIRE_LIGHTHOUSE` is set, the **last** instruction must be the Lighthouse program asserting on `vault`. | `PostCondition`, `LighthouseMissing` |

Optional gate **G1 (ORE PoW)**: if `merchant.pow_difficulty > 0`, the args must carry a DrillX `(nonce, digest)` that is valid for `challenge = sha256("TALLY_POW_V1" ‖ menu_nonce ‖ capsule ‖ recent_slot_hash)` with difficulty ≥ the required value. Error: `PowRequired` / `PowInvalid`. See [20-sgt-and-ore.md](20-sgt-and-ore.md).

### Expected transaction layout

```
[0] ComputeBudget::SetComputeUnitLimit         (optional)
[1] ComputeBudget::SetComputeUnitPrice         (optional, Helius fee estimate)
[2] Ed25519SigVerify(header 144B, sig, merchant signing key)   ← must be immediately before [3]
[3] Tally::pay_offer(args)
[4] Lighthouse::AssertTokenAccount(vault, amount == before − P) (required if REQUIRE_LIGHTHOUSE)
```

The app's Second Look sheet treats **anything else** as an unexpected instruction and disables approval.

## Signed header layout (what the merchant signs)

```
offset size field
0      16   domain          = b"TALLY_MENU_V1\0\0\0"
16     32   program_id
48     32   merchant PDA     (binds to the registry entry, not just the key)
80     32   menu_root
112    8    issued_slot      (u64 LE)
120    8    expires_slot     (u64 LE)
128    8    menu_nonce       (u64 LE)
136    4    item_count       (u32 LE)  — client sanity; proof depth ≤ ceil(log2(count))
140    1    hash_alg         (0 = SHA-256, 1 = BLAKE3 reserved)
141    1    header_version   (= 1)
142    2    reserved         (= 0)
total 144 bytes
```

The program **never trusts these bytes from the client**. It rebuilds them from `pay_offer` arguments and its own `crate::ID` and merchant account key, then compares the result byte for byte with the signed message.

## Reference code (sketch)

```rust
use anchor_lang::prelude::*;
use anchor_lang::solana_program::{
    ed25519_program, hash::hashv,
    sysvar::instructions::{load_current_index_checked, load_instruction_at_checked},
};
use anchor_spl::token::{self, Token, TokenAccount, Transfer};

declare_id!("Ta11y11111111111111111111111111111111111111"); // replace after deploy

const DOMAIN: [u8; 16] = *b"TALLY_MENU_V1\0\0\0";
const HEADER_LEN: usize = 144;
const MAX_AUTONOMOUS_WINDOW: u64 = 150;
const MAX_EXTENDED_WINDOW: u64 = 9_000;
const CYCLE_SLOTS: u64 = 216_000;
const MAX_PROOF_DEPTH: usize = 16;
const ED25519_HEADER_LEN: usize = 16;
const LEAF_PREFIX: u8 = 0x00;
const NODE_PREFIX: u8 = 0x01;
const FLAG_REQUIRE_LIGHTHOUSE: u8 = 1 << 0;
const FLAG_ALLOW_EXTENDED: u8 = 1 << 2;
// Lighthouse program id — verify against the current deployment before use.
pub const LIGHTHOUSE_ID: Pubkey = pubkey!("L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95");

#[program]
pub mod tally {
    use super::*;

    pub fn pay_offer(ctx: Context<PayOffer>, a: PayOfferArgs) -> Result<()> {
        let m = &ctx.accounts.merchant;
        let clock = Clock::get()?;
        let slot = clock.slot;

        // ── 1. authority & liveness (agent signer via `has_one = agent` + Signer)
        {
            let cap = &ctx.accounts.capsule;
            require!(!cap.frozen, TallyError::Frozen);
            require!(slot < cap.expiry_slot, TallyError::Expired);
            require!(m.active, TallyError::MerchantInactive);
        }

        // ── 2 + 3. introspection + hardened Ed25519 offsets
        let header = build_header(&crate::ID, &m.key(), &a);
        verify_prev_ed25519(&ctx.accounts.ix_sysvar, &m.signing_key, &header)?;

        // ── 4. freshness
        require!(a.issued_slot <= slot && slot <= a.expires_slot, TallyError::Stale);
        let window = a.expires_slot - a.issued_slot;
        let extended = window > MAX_AUTONOMOUS_WINDOW;
        require!(window <= MAX_EXTENDED_WINDOW, TallyError::WindowTooLong);
        if extended {
            require!(ctx.accounts.capsule.flags & FLAG_ALLOW_EXTENDED != 0, TallyError::WindowTooLong);
        }

        // ── 5. registry payout: enforced by `has_one = payout` + mint constraint (accounts struct)

        // ── 6. Merkle inclusion
        let leaf = leaf_hash(&a.item_id, a.category, a.price, a.category_floor);
        require!(verify_proof(leaf, &a.proof, a.menu_root), TallyError::NotOnMenu);

        // ── G1. optional ORE DrillX gate
        if m.pow_difficulty > 0 {
            let pow = a.pow.as_ref().ok_or(TallyError::PowRequired)?;
            verify_drillx(&ctx.accounts.slot_hashes, &ctx.accounts.capsule.key(), a.menu_nonce, pow, m.pow_difficulty)?;
        }

        // ── 7. capsule policy
        let cap = &mut ctx.accounts.capsule;
        require!(a.category < 256, TallyError::Category);
        let (w, b) = ((a.category / 64) as usize, a.category % 64);
        require!(cap.allowed_categories[w] & (1u64 << b) != 0, TallyError::Category);
        require!(a.price <= cap.per_tx_cap, TallyError::OverPerTx);
        if slot.saturating_sub(cap.cycle_start_slot) >= CYCLE_SLOTS {
            cap.cycle_start_slot = slot;
            cap.spent_in_cycle = 0;
        }
        let new_total = cap.spent_in_cycle.checked_add(a.price).ok_or(TallyError::Overflow)?;
        require!(new_total <= cap.daily_cap, TallyError::OverDaily);

        // ── 8. Cheapest-Match Invariant (+ bond tier + extended window)
        let needs_owner = a.price > a.category_floor
            || m.skr_bonded < cap.min_merchant_bond
            || extended;
        if needs_owner {
            require!(ctx.accounts.owner.is_signer, TallyError::OwnerCosignRequired);
        }

        // ── 9. duplicate nullifier: Receipt `init` in accounts struct

        // ── 10. atomic outflow + in-program post-condition
        let vault_before = ctx.accounts.vault.amount;
        let payout_before = ctx.accounts.payout.amount;
        let id_bytes = cap.capsule_id.to_le_bytes();
        let seeds: &[&[u8]] = &[b"capsule", cap.owner.as_ref(), &id_bytes, &[cap.bump]];
        token::transfer(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.to_account_info(),
                Transfer {
                    from: ctx.accounts.vault.to_account_info(),
                    to: ctx.accounts.payout.to_account_info(),
                    authority: cap.to_account_info(),
                },
                &[seeds],
            ),
            a.price,
        )?;
        ctx.accounts.vault.reload()?;
        ctx.accounts.payout.reload()?;
        require!(vault_before - ctx.accounts.vault.amount == a.price, TallyError::PostCondition);
        require!(ctx.accounts.payout.amount - payout_before == a.price, TallyError::PostCondition);
        if cap.flags & FLAG_REQUIRE_LIGHTHOUSE != 0 {
            require_terminal_lighthouse(&ctx.accounts.ix_sysvar, &ctx.accounts.vault.key())?;
        }
        cap.spent_in_cycle = new_total;

        let r = &mut ctx.accounts.receipt;
        r.capsule = cap.key(); r.merchant = m.key(); r.menu_root = a.menu_root;
        r.item_id = a.item_id; r.category = a.category; r.price = a.price;
        r.category_floor = a.category_floor; r.slot = slot; r.cosigned = needs_owner;

        emit!(Paid { capsule: cap.key(), merchant: m.key(), item_id: a.item_id, price: a.price, cosigned: needs_owner });
        Ok(())
    }
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone)]
pub struct PowSolution { pub nonce: [u8; 8] } // as built: hashcash nonce only

#[derive(AnchorSerialize, AnchorDeserialize, Clone)]
pub struct PayOfferArgs {
    pub menu_root: [u8; 32],
    pub issued_slot: u64,
    pub expires_slot: u64,
    pub menu_nonce: u64,
    pub item_count: u32,
    pub item_id: [u8; 16],
    pub category: u16,
    pub price: u64,
    pub category_floor: u64,
    pub proof: Vec<[u8; 32]>,          // ≤ MAX_PROOF_DEPTH
    pub pow: Option<PowSolution>,
}

#[derive(Accounts)]
#[instruction(a: PayOfferArgs)]
pub struct PayOffer<'info> {
    #[account(mut, has_one = agent, has_one = owner, has_one = vault)]
    pub capsule: Account<'info, Capsule>,
    #[account(mut)]
    pub agent: Signer<'info>,
    /// CHECK: equals capsule.owner; must sign only when invariant 8 requires
    pub owner: UncheckedAccount<'info>,
    #[account(has_one = payout)]
    pub merchant: Account<'info, Merchant>,
    #[account(mut)]
    pub vault: Account<'info, TokenAccount>,
    #[account(mut, constraint = payout.mint == vault.mint @ TallyError::WrongMint)]
    pub payout: Account<'info, TokenAccount>,
    #[account(
        init, payer = agent, space = 8 + Receipt::SIZE,
        seeds = [b"receipt", capsule.key().as_ref(), &a.menu_nonce.to_le_bytes(), &a.item_id],
        bump
    )]
    pub receipt: Account<'info, Receipt>,
    /// CHECK: address-checked instructions sysvar
    #[account(address = anchor_lang::solana_program::sysvar::instructions::ID)]
    pub ix_sysvar: UncheckedAccount<'info>,
    /// CHECK: address-checked SlotHashes sysvar (PoW challenge freshness)
    #[account(address = anchor_lang::solana_program::sysvar::slot_hashes::ID)]
    pub slot_hashes: UncheckedAccount<'info>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

fn build_header(program: &Pubkey, merchant_pda: &Pubkey, a: &PayOfferArgs) -> [u8; HEADER_LEN] {
    let mut h = [0u8; HEADER_LEN];
    h[0..16].copy_from_slice(&DOMAIN);
    h[16..48].copy_from_slice(program.as_ref());
    h[48..80].copy_from_slice(merchant_pda.as_ref());
    h[80..112].copy_from_slice(&a.menu_root);
    h[112..120].copy_from_slice(&a.issued_slot.to_le_bytes());
    h[120..128].copy_from_slice(&a.expires_slot.to_le_bytes());
    h[128..136].copy_from_slice(&a.menu_nonce.to_le_bytes());
    h[136..140].copy_from_slice(&a.item_count.to_le_bytes());
    h[140] = 0; // hash_alg = SHA-256
    h[141] = 1; // header_version
    // 142..144 reserved = 0
    h
}

/// Ed25519 precompile data: [num_sigs u8][pad u8] then per signature 7 × u16 LE:
/// sig_off, sig_ix, pk_off, pk_ix, msg_off, msg_len, msg_ix
fn verify_prev_ed25519(sysvar: &AccountInfo, key: &Pubkey, msg: &[u8; HEADER_LEN]) -> Result<()> {
    let cur = load_current_index_checked(sysvar)? as usize;
    require!(cur > 0, TallyError::MissingSig);
    let ix = load_instruction_at_checked(cur - 1, sysvar)?;
    require_keys_eq!(ix.program_id, ed25519_program::ID, TallyError::MissingSig);
    require!(ix.accounts.is_empty(), TallyError::BadSigIx);

    let d = &ix.data;
    require!(d.len() >= ED25519_HEADER_LEN && d[0] == 1, TallyError::BadSigIx);
    let u = |i: usize| u16::from_le_bytes([d[i], d[i + 1]]);
    let (sig_off, sig_ix) = (u(2) as usize, u(4));
    let (pk_off, pk_ix)   = (u(6) as usize, u(8));
    let (msg_off, msg_len, msg_ix) = (u(10) as usize, u(12) as usize, u(14));

    // every argument must live inside THIS instruction's data
    require!(sig_ix == u16::MAX && pk_ix == u16::MAX && msg_ix == u16::MAX, TallyError::BadSigIx);
    // no offset may point back into the 16-byte offsets header
    require!(sig_off >= ED25519_HEADER_LEN && pk_off >= ED25519_HEADER_LEN && msg_off >= ED25519_HEADER_LEN, TallyError::BadSigIx);
    // strict bounds (checked_add avoids wrap on 32-bit targets)
    let end = |o: usize, n: usize| o.checked_add(n).filter(|e| *e <= d.len()).is_some();
    require!(end(sig_off, 64) && end(pk_off, 32) && end(msg_off, msg_len), TallyError::BadSigIx);
    require!(msg_len == HEADER_LEN, TallyError::WrongMessage);

    require!(&d[pk_off..pk_off + 32] == key.as_ref(), TallyError::WrongSigner);
    require!(&d[msg_off..msg_off + msg_len] == msg.as_ref(), TallyError::WrongMessage);
    Ok(())
}

fn require_terminal_lighthouse(sysvar: &AccountInfo, vault: &Pubkey) -> Result<()> {
    // Find the last instruction in the transaction; it must be Lighthouse and reference the vault.
    let cur = load_current_index_checked(sysvar)? as usize;
    let mut last = cur;
    while load_instruction_at_checked(last + 1, sysvar).is_ok() { last += 1; }
    require!(last > cur, TallyError::LighthouseMissing);
    let ix = load_instruction_at_checked(last, sysvar)?;
    require_keys_eq!(ix.program_id, LIGHTHOUSE_ID, TallyError::LighthouseMissing);
    require!(ix.accounts.iter().any(|a| a.pubkey == *vault), TallyError::LighthouseMissing);
    Ok(())
}

fn leaf_hash(item_id: &[u8; 16], category: u16, price: u64, floor: u64) -> [u8; 32] {
    hashv(&[&[LEAF_PREFIX], item_id, &category.to_le_bytes(), &price.to_le_bytes(), &floor.to_le_bytes()]).to_bytes()
}

fn verify_proof(mut node: [u8; 32], proof: &[[u8; 32]], root: [u8; 32]) -> bool {
    if proof.len() > MAX_PROOF_DEPTH { return false; }
    for sib in proof {
        let (a, b) = if node <= *sib { (node, *sib) } else { (*sib, node) }; // sorted pairs
        node = hashv(&[&[NODE_PREFIX], &a, &b]).to_bytes();
    }
    node == root
}

/// Sketch: challenge from the most recent SlotHashes entry; verify with the `drillx` crate.
/// Measure CU cost before enabling; see 20-sgt-and-ore.md.
fn verify_drillx(_slot_hashes: &AccountInfo, _capsule: &Pubkey, _menu_nonce: u64, _pow: &PowSolution, _difficulty: u8) -> Result<()> {
    // let challenge = hashv(&[b"TALLY_POW_V1", &menu_nonce.to_le_bytes(), capsule.as_ref(), &recent_slot_hash]).to_bytes();
    // require!(drillx::is_valid_digest(&challenge, &pow.nonce, &pow.digest), TallyError::PowInvalid);
    // require!(drillx::difficulty(pow.digest) >= difficulty as u32, TallyError::PowInvalid);
    unimplemented!()
}

#[account] pub struct Capsule { /* fields as in the table above */ }
#[account] pub struct Merchant { /* fields as in the table above */ }
#[account] pub struct Receipt {
    pub capsule: Pubkey, pub merchant: Pubkey, pub menu_root: [u8; 32], pub item_id: [u8; 16],
    pub category: u16, pub price: u64, pub category_floor: u64, pub slot: u64, pub cosigned: bool, pub disputed: bool,
}
impl Receipt { pub const SIZE: usize = 32 + 32 + 32 + 16 + 2 + 8 + 8 + 8 + 1 + 1; }

#[event] pub struct Paid { pub capsule: Pubkey, pub merchant: Pubkey, pub item_id: [u8; 16], pub price: u64, pub cosigned: bool }
#[event] pub struct Slashed { pub merchant: Pubkey, pub amount: u64, pub reason: u8 }

#[error_code]
pub enum TallyError {
    #[msg("Capsule is frozen")] Frozen,
    #[msg("Capsule expired")] Expired,
    #[msg("Merchant inactive")] MerchantInactive,
    #[msg("Merchant signature instruction missing")] MissingSig,
    #[msg("Malformed or unsafe signature instruction")] BadSigIx,
    #[msg("Menu not signed by registered merchant key")] WrongSigner,
    #[msg("Signed menu header does not match")] WrongMessage,
    #[msg("Menu is stale or not yet valid")] Stale,
    #[msg("Menu validity window too long")] WindowTooLong,
    #[msg("Item is not on the signed menu")] NotOnMenu,
    #[msg("Category not allowed by your rules")] Category,
    #[msg("Above your per-purchase cap")] OverPerTx,
    #[msg("Above your daily cap")] OverDaily,
    #[msg("Pricier than the cheapest match: owner must co-sign")] OwnerCosignRequired,
    #[msg("Payout mint mismatch")] WrongMint,
    #[msg("Balance post-condition failed")] PostCondition,
    #[msg("Required Lighthouse assertion missing")] LighthouseMissing,
    #[msg("Merchant requires proof-of-work")] PowRequired,
    #[msg("Invalid proof-of-work")] PowInvalid,
    #[msg("Arithmetic overflow")] Overflow,
}
```

## `prove_floor_fraud`: trustless slashing

A merchant certifies `F_C` as the cheapest price in category `C`. If the **same signed menu** contains a leaf in `C` priced below another leaf's `F_C`, the merchant has signed a contradiction. Anyone can submit:

1. An Ed25519 instruction over the menu header, with the same invariant 2 and 3 checks.
2. `leaf_a = (I_a, C, P_a, F_a)` with its proof, and `leaf_b = (I_b, C, P_b, F_b)` with its proof, both under `R_menu`.
3. The program requires `P_a < F_b`. It then moves `min(SLASH_FRACTION × bond, bond)` from the bond vault, splits it between the reporter and a user-compensation pool, and emits `Slashed`.

No arbiter is involved; the merchant's own signature is the evidence. The limit is that this only catches contradictions **inside** a menu. A merchant who leaves cheaper items off the menu entirely isn't caught, so that case is out of scope (see [11](11-threat-model.md)).

## Design notes and gotchas

- **Compute budget.** The target is **< 45,000 CU for `pay_offer`**, excluding the Ed25519 precompile, which is charged separately. Main costs: up to 16 SHA-256 calls (≈ 100–200 CU each), one SPL transfer CPI (≈ 4–6k), the `Receipt` `init` (system CPI), two `reload`s and the sysvar parsing. **Measure** with `solana-program-test` logs. The DrillX gate is extra and must be measured separately.
- **Ed25519 adjacency.** The check uses `cur − 1`, so ComputeBudget instructions must come **before** the Ed25519 instruction. The app's tx builder enforces this order.
- **Why the post-condition is in-program.** A Lighthouse instruction appended by a compromised client can be left out. The `reload()` check can't be bypassed. With `REQUIRE_LIGHTHOUSE` set, the program also checks that the Lighthouse instruction is present, which gives a second, independent assertion.
- **Token-2022 / transfer fees.** The payout-side delta check means a fee-bearing mint can't silently short the merchant. This is the Proof-Gated Signing receipt-check lesson. USDC is a classic SPL token, but the check is still kept.
- **Who pays receipt rent?** The agent, which holds a small SOL top-up. Alternative: a fee-payer service.
- **Owner as `UncheckedAccount`.** It must equal the stored owner (`has_one = owner`) but only needs to sign when invariant 8 triggers.
- **Category floor honesty.** Contradictions are covered by `prove_floor_fraud`. Omission is out of scope and mitigated by the bond and reputation.
- **Upgrade authority.** Kept for the hackathon. The README says production would freeze it or put it behind a multisig.
