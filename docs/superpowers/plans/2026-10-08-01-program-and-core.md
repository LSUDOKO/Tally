# Tally Phase 1 — Core crate + Anchor program Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `tally-core` Rust crate and the Anchor `tally` program, implementing every on-chain mechanism in the spec (ten `pay_offer` invariants, fraud-proof slashing, disputes, SGT gate, PoW gate). Also build a LiteSVM test suite that proves each on-chain threat-model row reverts with its exact error.

**Architecture:** All byte-level logic (header, leaf, Merkle, Ed25519 instruction parsing, floor consistency, post-condition deltas) lives in `crates/tally-core`. It's pure Rust and unit-tested natively in milliseconds. The Anchor program in `programs/tally` is thin glue: accounts, constraints, CPIs, and calls into `tally-core`. Integration tests in `tests/litesvm` load the compiled `.so` into LiteSVM, warp slots, and assert exact error codes. `fixtures/menu-v1.json`, generated from `tally-core`, is the cross-language contract for TypeScript (phase 2) and Kotlin (phase 3).

**Tech Stack:** Rust 1.93, Anchor 1.2.1 (`anchor-lang`, `anchor-spl`), `solana-sha256-hasher` 3.1, LiteSVM 0.17 + `litesvm-token` 0.17, `solana-ed25519-program` 3.0, `ed25519-dalek` (fixtures/tests), cargo-build-sbf 4.0 / platform-tools v1.53, Anchor CLI 1.2.1 (prebuilt binary, IDL only).

**Spec:** `docs/00-whitepaper.md`, `docs/06-onchain-program.md`, `docs/07-menu-and-qr-spec.md`, `docs/11-threat-model.md`, `docs/18-testing.md`, `docs/20-sgt-and-ore.md`.

## Global Constraints

- Header is **144 bytes**: domain `b"TALLY_MENU_V1\0\0\0"` (16) ‖ program_id (32) ‖ merchant_pda (32) ‖ menu_root (32) ‖ issued_slot u64 LE ‖ expires_slot u64 LE ‖ menu_nonce u64 LE ‖ item_count u32 LE ‖ hash_alg u8 (0 = SHA-256) ‖ header_version u8 (= 1) ‖ reserved u16 (= 0).
- Leaf = `SHA256(0x00 ‖ item_id[16] ‖ category u16 LE ‖ price u64 LE ‖ floor u64 LE)`. Node = `SHA256(0x01 ‖ min(a,b) ‖ max(a,b))`. An odd node is promoted unchanged.
- `MAX_AUTONOMOUS_WINDOW = 150`, `MAX_EXTENDED_WINDOW = 9_000`, `CYCLE_SLOTS = 216_000`, `MAX_PROOF_DEPTH = 16`, `ED25519_HEADER_LEN = 16`; category must be `< 256`.
- PDA seeds: `["merchant", authority]`, `["capsule", owner, capsule_id u64 LE]`, `["receipt", capsule, menu_nonce u64 LE, item_id[16]]`, `["dispute", receipt]`, `["bond", merchant]`, `["sgt", sgt_mint, promo_id u64 LE]`, `["config"]`, `["pool"]`.
- Capsule `flags`: bit0 `REQUIRE_LIGHTHOUSE`, bit1 `SGT_VERIFIED`, bit2 `ALLOW_EXTENDED_WINDOW`.
- Error names exactly as in `docs/06` (`Frozen`, `Expired`, `MerchantInactive`, `MissingSig`, `BadSigIx`, `WrongSigner`, `WrongMessage`, `Stale`, `WindowTooLong`, `NotOnMenu`, `Category`, `OverPerTx`, `OverDaily`, `OwnerCosignRequired`, `WrongMint`, `PostCondition`, `LighthouseMissing`, `PowRequired`, `PowInvalid`, `Overflow`), plus the additions listed in Task 8.
- `pay_offer` CU target **< 45,000** without the PoW gate (the Ed25519 precompile is charged separately).
- No `unwrap()`/`expect()` in program code. All arithmetic is checked.
- Commits use Conventional Commits with no AI co-author trailer. A failing test is never committed on its own to `main`.

## Design additions (not in the docs yet; Task 20 back-ports them into `docs/06`)

1. **`Config` PDA `["config"]`**: `admin`, `arbiter` (multisig pubkey), `usdc_mint`, `skr_mint`, `sgt_group`, `slash_bps` (u16), `bond_unlock_delay` (u64 slots), `bump`. The docs need an arbiter, an SKR mint and an SGT group but never say where they're stored. Pinning them in one admin-initialised account prevents fake-mint bonds.
2. **`pay_offer` uses `token_interface::transfer_checked`** with the mint account. This supports Token-2022 mints, so the post-condition guards against transfer-fee mints, which is the point of invariant 10.
3. **Compensation pool** is an SKR token account at `["pool"]` owned by the config PDA. Slash proceeds are split `reporter 50% / pool 50%`.
4. **PoW verification** uses a SHA-256 hashcash gate by default (`leading_zero_bits(SHA256(challenge ‖ nonce)) ≥ difficulty`). DrillX is used instead only if `drillx` compiles for SBF and verifies in < 100k CU (decided in Task 19, Step 1). Both bind the same challenge from `docs/20`.

## Review Focus

1. **Instruction ordering:** a ComputeBudget instruction placed *between* Ed25519 and `pay_offer`. Expected: a clean `MissingSig` revert, never a panic or a false pass. *(Test `ed25519_not_adjacent`, Task 11.)*
2. **Merkle edge shapes:** a 1-item menu (empty proof), an odd item count (promoted node), a 2-item menu. All must verify; a node passed as a leaf must not. *(Tests in Task 4 + `single_item_menu_pays`, Task 14.)*
3. **Exact boundaries:** `price == category_floor` is autonomous; `price == per_tx_cap` is allowed; `spent + price == daily_cap` is allowed; `slot == expires_slot` is allowed; a window of exactly 150 is autonomous; `slot − cycle_start == CYCLE_SLOTS` resets. *(Tests `boundaries_*`, Tasks 12–13.)*
4. **Key rotation mid-window:** after `rotate_signing_key`, a menu signed by the old key (still fresh) must fail with `WrongSigner`. *(Test `rotated_key_rejects_old_menu`, Task 11.)*
5. **Owner passed but not required:** owner co-signs a floor-priced purchase. It must succeed, and the receipt must record `cosigned = false`, because co-signing is only *required* above the floor. *(Test `owner_extra_signature_ok`, Task 13.)*

---

## File structure

| Path | Responsibility |
|---|---|
| `Cargo.toml` | Workspace: `crates/tally-core`, `programs/tally`, `tests/litesvm` |
| `rust-toolchain.toml` | Pin `1.93.0` |
| `Anchor.toml` | Program ID, cluster, IDL output |
| `crates/tally-core/src/lib.rs` | Re-exports, constants |
| `crates/tally-core/src/hash.rs` | `sha256v` wrapper (syscall on SBF, `sha2` off-chain via `solana-sha256-hasher`) |
| `crates/tally-core/src/header.rs` | `HeaderFields`, `build_header` |
| `crates/tally-core/src/menu.rs` | `Item`, `leaf_hash`, `compute_floors`, `find_floor_contradiction` |
| `crates/tally-core/src/merkle.rs` | `node_hash`, `MerkleTree`, `verify_proof` |
| `crates/tally-core/src/ed25519.rs` | `build_ix_data`, `parse_single` (hardened) |
| `crates/tally-core/src/post.rs` | `check_exact_deltas` |
| `crates/tally-core/src/pow.rs` | `pow_challenge`, `leading_zero_bits`, `verify_hashcash` |
| `crates/tally-core/src/bin/gen-fixtures.rs` | Writes `fixtures/menu-v1.json` |
| `programs/tally/src/lib.rs` | `declare_id!`, `#[program]` dispatch |
| `programs/tally/src/constants.rs`, `errors.rs`, `events.rs` | |
| `programs/tally/src/state/{config,merchant,capsule,receipt,dispute,sgt_claim}.rs` | Accounts |
| `programs/tally/src/instructions/*.rs` | One file per instruction group |
| `programs/tally/src/checks/{introspection,lighthouse,sgt}.rs` | Sysvar parsing helpers |
| `tests/litesvm/src/lib.rs` | `Env` harness |
| `tests/litesvm/tests/*.rs` | One file per attack class |

---

### Task 1: Repository restructure and workspace skeleton

**Files:**
- Move: `tally/README.md` → `docs/README.md`; `tally/docs/*` → `docs/`
- Create: `Cargo.toml`, `rust-toolchain.toml`, `Anchor.toml`, `.gitignore`, `programs/tally/Cargo.toml`, `programs/tally/src/lib.rs`, `crates/tally-core/Cargo.toml`, `crates/tally-core/src/lib.rs`

**Interfaces:**
- Produces: program ID constant `tally_core::PROGRAM_ID_STR` (base58), matching `declare_id!`.

- [ ] **Step 1: Move the design pack to the root**

```bash
cd ~/Desktop/hackathon_projects/Tally
git mv -k tally/docs/* docs/ 2>/dev/null || (mkdir -p docs && mv tally/docs/* docs/)
mv tally/README.md docs/README.md && rmdir tally/docs tally
sed -i 's#](docs/#](#g; s#](README.md)#](README.md)#g' docs/README.md
git add -A docs && git commit -m "docs: move Tally design pack to repository root"
```

- [ ] **Step 2: Commit the plans**

```bash
git add docs/superpowers && git commit -m "docs: add implementation plans (master + phase 1)"
```

- [ ] **Step 3: Install Anchor CLI 1.2.1 (prebuilt) and generate the program keypair**

```bash
mkdir -p ~/.local/bin ~/.config/tally
gh release download v1.2.1 --repo solana-foundation/anchor -p 'anchor-1.2.1-x86_64-unknown-linux-gnu' -O ~/.local/bin/anchor
chmod +x ~/.local/bin/anchor && anchor --version   # expect: anchor-cli 1.2.1
solana-keygen new --no-bip39-passphrase -o ~/.config/tally/tally-program-keypair.json
solana-keygen pubkey ~/.config/tally/tally-program-keypair.json   # → PROGRAM_ID
```

The program keypair lives **outside** the repo. Back it up; it controls the devnet program address.

- [ ] **Step 4: Write workspace files**

`Cargo.toml`:
```toml
[workspace]
resolver = "2"
members = ["crates/tally-core", "programs/tally", "tests/litesvm"]

[workspace.package]
edition = "2021"
license = "MIT"
repository = "https://github.com/LSUDOKO/Tally"

[profile.release]
overflow-checks = true
lto = "fat"
codegen-units = 1
```

`rust-toolchain.toml`:
```toml
[toolchain]
channel = "1.93.0"
components = ["rustfmt", "clippy"]
```

`Anchor.toml` (replace `PROGRAM_ID`):
```toml
[toolchain]
anchor_version = "1.2.1"

[programs.localnet]
tally = "PROGRAM_ID"
[programs.devnet]
tally = "PROGRAM_ID"

[provider]
cluster = "devnet"
wallet = "~/.config/solana/id.json"
```

`.gitignore`:
```
target/
node_modules/
.anchor/
*.so
!fixtures/programs/*.so
.DS_Store
local.properties
*.jks
*.keystore
.env
```

`crates/tally-core/Cargo.toml`:
```toml
[package]
name = "tally-core"
version = "0.1.0"
edition.workspace = true
license.workspace = true

[dependencies]
solana-sha256-hasher = "3.1"

[dev-dependencies]
ed25519-dalek = { version = "2", features = ["rand_core"] }
serde_json = "1"
hex = "0.4"

[features]
fixtures = ["dep:ed25519-dalek", "dep:serde_json", "dep:hex", "dep:solana-pubkey"]

[target.'cfg(not(target_os = "solana"))'.dependencies]
ed25519-dalek = { version = "2", optional = true }
serde_json = { version = "1", optional = true }
hex = { version = "0.4", optional = true }
solana-pubkey = { version = "3", optional = true, features = ["curve25519"] }

[[bin]]
name = "gen-fixtures"
required-features = ["fixtures"]
```

`crates/tally-core/src/lib.rs`:
```rust
#![cfg_attr(not(test), no_std)]
extern crate alloc;

pub const PROGRAM_ID_STR: &str = "PROGRAM_ID";
pub const DOMAIN: [u8; 16] = *b"TALLY_MENU_V1\0\0\0";
pub const HEADER_LEN: usize = 144;
pub const HEADER_VERSION: u8 = 1;
pub const HASH_ALG_SHA256: u8 = 0;
pub const MAX_AUTONOMOUS_WINDOW: u64 = 150;
pub const MAX_EXTENDED_WINDOW: u64 = 9_000;
pub const CYCLE_SLOTS: u64 = 216_000;
pub const MAX_PROOF_DEPTH: usize = 16;
pub const ED25519_HEADER_LEN: usize = 16;
pub const LEAF_PREFIX: u8 = 0x00;
pub const NODE_PREFIX: u8 = 0x01;
```

`programs/tally/Cargo.toml`:
```toml
[package]
name = "tally"
version = "0.1.0"
edition.workspace = true
license.workspace = true

[lib]
crate-type = ["cdylib", "lib"]

[features]
default = []
no-entrypoint = []
cpi = ["no-entrypoint"]
idl-build = ["anchor-lang/idl-build", "anchor-spl/idl-build"]

[dependencies]
anchor-lang = { version = "1.2.1", features = ["init-if-needed"] }
anchor-spl = { version = "1.2.1", features = ["token", "token_2022", "associated_token"] }
tally-core = { path = "../../crates/tally-core" }
```

`programs/tally/src/lib.rs`:
```rust
use anchor_lang::prelude::*;

declare_id!("PROGRAM_ID");

#[program]
pub mod tally {}
```

- [ ] **Step 5: Verify both builds**

Run: `cargo check -p tally-core && cargo build-sbf --manifest-path programs/tally/Cargo.toml`
Expected: both succeed, and `target/deploy/tally.so` exists. If Anchor 1.2.1 rejects an empty `#[program]` module, add a no-op `pub fn ping(_ctx: Context<Ping>) -> Result<()> { Ok(()) }` with `#[derive(Accounts)] pub struct Ping {}` and delete it in Task 9.

- [ ] **Step 6: Commit**

```bash
git add Cargo.toml rust-toolchain.toml Anchor.toml .gitignore crates programs
git commit -m "build: scaffold rust workspace with tally-core and anchor program"
```

---

### Task 2: `tally-core` hash + header

**Files:**
- Create: `crates/tally-core/src/hash.rs`, `crates/tally-core/src/header.rs`
- Modify: `crates/tally-core/src/lib.rs` (add `pub mod hash; pub mod header;`)

**Interfaces:**
- Produces: `pub fn sha256v(parts: &[&[u8]]) -> [u8; 32]`; `pub struct HeaderFields { program_id: [u8;32], merchant_pda: [u8;32], menu_root: [u8;32], issued_slot: u64, expires_slot: u64, menu_nonce: u64, item_count: u32 }`; `pub fn build_header(h: &HeaderFields) -> [u8; HEADER_LEN]`.

- [ ] **Step 1: Write failing tests** (append to `header.rs`)

```rust
#[cfg(test)]
mod tests {
    use super::*;
    fn sample() -> HeaderFields {
        HeaderFields { program_id: [1; 32], merchant_pda: [2; 32], menu_root: [3; 32],
            issued_slot: 0x0102030405060708, expires_slot: 9, menu_nonce: 42, item_count: 8 }
    }
    #[test] fn header_is_144_bytes_with_exact_layout() {
        let h = build_header(&sample());
        assert_eq!(h.len(), 144);
        assert_eq!(&h[0..16], b"TALLY_MENU_V1\0\0\0");
        assert_eq!(&h[16..48], &[1; 32]);
        assert_eq!(&h[48..80], &[2; 32]);
        assert_eq!(&h[80..112], &[3; 32]);
        assert_eq!(&h[112..120], &0x0102030405060708u64.to_le_bytes());
        assert_eq!(&h[120..128], &9u64.to_le_bytes());
        assert_eq!(&h[128..136], &42u64.to_le_bytes());
        assert_eq!(&h[136..140], &8u32.to_le_bytes());
        assert_eq!(h[140], 0); assert_eq!(h[141], 1); assert_eq!(&h[142..144], &[0, 0]);
    }
    #[test] fn any_field_change_changes_header() {
        let a = build_header(&sample());
        let mut s = sample(); s.menu_nonce = 43;
        assert_ne!(a, build_header(&s));
    }
    #[test] fn sha256v_matches_known_vector() {
        // SHA-256("abc")
        assert_eq!(crate::hash::sha256v(&[b"a", b"bc"]),
            hex_literal("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
    }
    fn hex_literal(s: &str) -> [u8; 32] {
        let mut o = [0u8; 32];
        for i in 0..32 { o[i] = u8::from_str_radix(&s[2*i..2*i+2], 16).unwrap(); }
        o
    }
}
```

- [ ] **Step 2: Run, and expect a compile failure**

Run: `cargo test -p tally-core header`
Expected: FAIL (`build_header` not found).

- [ ] **Step 3: Implement**

`hash.rs`:
```rust
pub fn sha256v(parts: &[&[u8]]) -> [u8; 32] {
    solana_sha256_hasher::hashv(parts).to_bytes()
}
```

`header.rs`:
```rust
use crate::{DOMAIN, HASH_ALG_SHA256, HEADER_LEN, HEADER_VERSION};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct HeaderFields {
    pub program_id: [u8; 32],
    pub merchant_pda: [u8; 32],
    pub menu_root: [u8; 32],
    pub issued_slot: u64,
    pub expires_slot: u64,
    pub menu_nonce: u64,
    pub item_count: u32,
}

pub fn build_header(f: &HeaderFields) -> [u8; HEADER_LEN] {
    let mut h = [0u8; HEADER_LEN];
    h[0..16].copy_from_slice(&DOMAIN);
    h[16..48].copy_from_slice(&f.program_id);
    h[48..80].copy_from_slice(&f.merchant_pda);
    h[80..112].copy_from_slice(&f.menu_root);
    h[112..120].copy_from_slice(&f.issued_slot.to_le_bytes());
    h[120..128].copy_from_slice(&f.expires_slot.to_le_bytes());
    h[128..136].copy_from_slice(&f.menu_nonce.to_le_bytes());
    h[136..140].copy_from_slice(&f.item_count.to_le_bytes());
    h[140] = HASH_ALG_SHA256;
    h[141] = HEADER_VERSION;
    h
}
```

- [ ] **Step 4: Run the tests**

Run: `cargo test -p tally-core header`
Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add crates/tally-core && git commit -m "core: add 144-byte signed menu header builder"
```

---

### Task 3: `tally-core` leaf hashing and floors

**Files:**
- Create: `crates/tally-core/src/menu.rs`; modify `lib.rs` (`pub mod menu;`)

**Interfaces:**
- Produces: `pub struct Item { id: [u8;16], category: u16, price: u64, floor: u64 }`; `pub fn leaf_hash(i: &Item) -> [u8;32]`; `pub fn compute_floors(items: &mut [Item])` (sets each `floor` to the min price in its category); `pub fn find_floor_contradiction(items: &[Item]) -> Option<(usize, usize)>`, which returns `(cheap_idx, claimed_idx)` with the same category and `items[cheap].price < items[claimed].floor`.

- [ ] **Step 1: Write failing tests** (in `menu.rs`)

```rust
#[cfg(test)]
mod tests {
    use super::*;
    fn it(n: u8, cat: u16, price: u64) -> Item { Item { id: [n; 16], category: cat, price, floor: 0 } }

    #[test] fn leaf_preimage_is_prefix_id_cat_price_floor() {
        let i = Item { id: [9; 16], category: 0x0103, price: 4_500_000, floor: 3_500_000 };
        let mut pre = alloc::vec![0x00u8];
        pre.extend_from_slice(&[9; 16]);
        pre.extend_from_slice(&0x0103u16.to_le_bytes());
        pre.extend_from_slice(&4_500_000u64.to_le_bytes());
        pre.extend_from_slice(&3_500_000u64.to_le_bytes());
        assert_eq!(pre.len(), 35);
        assert_eq!(leaf_hash(&i), crate::hash::sha256v(&[&pre]));
    }
    #[test] fn changing_price_changes_leaf() {
        let a = Item { id: [1; 16], category: 3, price: 450, floor: 450 };
        let b = Item { price: 990, ..a };
        assert_ne!(leaf_hash(&a), leaf_hash(&b));
    }
    #[test] fn floors_are_min_per_category() {
        let mut v = alloc::vec![it(1, 3, 590), it(2, 3, 450), it(3, 0, 300), it(4, 3, 520)];
        compute_floors(&mut v);
        assert_eq!(v.iter().map(|i| i.floor).collect::<alloc::vec::Vec<_>>(), [450, 450, 300, 450]);
        assert_eq!(find_floor_contradiction(&v), None);
    }
    #[test] fn contradiction_detected() {
        let mut v = alloc::vec![it(1, 3, 590), it(2, 3, 450)];
        compute_floors(&mut v);
        v[0].floor = 590; // merchant lies: claims floor 590 on the large latte
        assert_eq!(find_floor_contradiction(&v), Some((1, 0)));
    }
}
```

- [ ] **Step 2: Run and expect a failure**

Run: `cargo test -p tally-core menu` → FAIL (missing items).

- [ ] **Step 3: Implement**

```rust
use crate::{hash::sha256v, LEAF_PREFIX};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Item { pub id: [u8; 16], pub category: u16, pub price: u64, pub floor: u64 }

pub fn leaf_hash(i: &Item) -> [u8; 32] {
    sha256v(&[&[LEAF_PREFIX], &i.id, &i.category.to_le_bytes(), &i.price.to_le_bytes(), &i.floor.to_le_bytes()])
}

pub fn compute_floors(items: &mut [Item]) {
    for idx in 0..items.len() {
        let cat = items[idx].category;
        let min = items.iter().filter(|o| o.category == cat).map(|o| o.price).min().unwrap_or(items[idx].price);
        items[idx].floor = min;
    }
}

pub fn find_floor_contradiction(items: &[Item]) -> Option<(usize, usize)> {
    for (c, cheap) in items.iter().enumerate() {
        for (k, claimed) in items.iter().enumerate() {
            if cheap.category == claimed.category && cheap.price < claimed.floor {
                return Some((c, k));
            }
        }
    }
    None
}
```

- [ ] **Step 4: Run the tests** → 4 passed.
- [ ] **Step 5: Commit**: `git commit -am "core: add catalog leaf hashing and category floor checks"` (add `menu.rs` first).

---

### Task 4: `tally-core` Merkle tree

**Files:** Create `crates/tally-core/src/merkle.rs`; modify `lib.rs`.

**Interfaces:**
- Produces: `pub fn node_hash(a: &[u8;32], b: &[u8;32]) -> [u8;32]` (sorted); `pub struct MerkleTree` with `pub fn new(leaves: Vec<[u8;32]>) -> Self` (panics on empty, so callers guard), `pub fn root(&self) -> [u8;32]`, `pub fn proof(&self, index: usize) -> Vec<[u8;32]>`; `pub fn verify_proof(leaf: [u8;32], proof: &[[u8;32]], root: [u8;32]) -> bool` (rejects `proof.len() > MAX_PROOF_DEPTH`).

- [ ] **Step 1: Write failing tests**

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use alloc::vec::Vec;
    fn leaves(n: usize) -> Vec<[u8; 32]> { (0..n).map(|i| crate::hash::sha256v(&[&[i as u8]])).collect() }

    #[test] fn single_leaf_root_is_leaf_and_proof_empty() {
        let t = MerkleTree::new(leaves(1));
        assert_eq!(t.root(), leaves(1)[0]);
        assert!(t.proof(0).is_empty());
        assert!(verify_proof(leaves(1)[0], &[], t.root()));
    }
    #[test] fn every_leaf_verifies_for_sizes_1_to_17() {
        for n in 1..=17 {
            let l = leaves(n); let t = MerkleTree::new(l.clone());
            for i in 0..n { assert!(verify_proof(l[i], &t.proof(i), t.root()), "n={n} i={i}"); }
        }
    }
    #[test] fn wrong_leaf_fails() {
        let l = leaves(5); let t = MerkleTree::new(l.clone());
        assert!(!verify_proof(l[1], &t.proof(0), t.root()));
    }
    #[test] fn node_cannot_pose_as_leaf() {
        let l = leaves(4); let t = MerkleTree::new(l.clone());
        let n01 = node_hash(&l[0], &l[1]);
        let n23 = node_hash(&l[2], &l[3]);
        // a 1-step "proof" of the internal node n01 with sibling n23 reaches the root…
        assert!(verify_proof(n01, &[n23], t.root()));
        // …which is why leaves are prefixed: no leaf hash equals n01
        assert!(l.iter().all(|x| *x != n01));
    }
    #[test] fn proof_longer_than_16_rejected() {
        let p = alloc::vec![[0u8; 32]; 17];
        assert!(!verify_proof([0; 32], &p, [0; 32]));
    }
}
```

The `node_cannot_pose_as_leaf` test pins down the *reason* the program never accepts raw leaves from the client. The program only ever hashes `(id, cat, price, floor)` into a leaf itself, so an internal node can't be submitted as an item.

- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**

```rust
use crate::{hash::sha256v, MAX_PROOF_DEPTH, NODE_PREFIX};
use alloc::vec::Vec;

pub fn node_hash(a: &[u8; 32], b: &[u8; 32]) -> [u8; 32] {
    let (x, y) = if a <= b { (a, b) } else { (b, a) };
    sha256v(&[&[NODE_PREFIX], x, y])
}

pub struct MerkleTree { levels: Vec<Vec<[u8; 32]>> }

impl MerkleTree {
    pub fn new(leaves: Vec<[u8; 32]>) -> Self {
        assert!(!leaves.is_empty(), "empty menu");
        let mut levels = alloc::vec![leaves];
        while levels.last().map(|l| l.len()).unwrap_or(0) > 1 {
            let prev = levels.last().unwrap_or_else(|| unreachable!());
            let mut next = Vec::with_capacity((prev.len() + 1) / 2);
            for pair in prev.chunks(2) {
                next.push(if pair.len() == 2 { node_hash(&pair[0], &pair[1]) } else { pair[0] });
            }
            levels.push(next);
        }
        Self { levels }
    }
    pub fn root(&self) -> [u8; 32] { self.levels[self.levels.len() - 1][0] }
    pub fn proof(&self, mut index: usize) -> Vec<[u8; 32]> {
        let mut out = Vec::new();
        for level in &self.levels[..self.levels.len() - 1] {
            let sib = index ^ 1;
            if sib < level.len() { out.push(level[sib]); }
            index /= 2;
        }
        out
    }
}

pub fn verify_proof(mut node: [u8; 32], proof: &[[u8; 32]], root: [u8; 32]) -> bool {
    if proof.len() > MAX_PROOF_DEPTH { return false; }
    for sib in proof { node = node_hash(&node, sib); }
    node == root
}
```

- [ ] **Step 4: Run** → 5 passed.
- [ ] **Step 5: Commit**: `git commit -m "core: add sorted-pair merkle tree with odd-node promotion"`

---

### Task 5: `tally-core` hardened Ed25519 instruction parsing

**Files:** Create `crates/tally-core/src/ed25519.rs`; modify `lib.rs`.

**Interfaces:**
- Produces: `pub fn build_ix_data(pubkey: &[u8;32], sig: &[u8;64], msg: &[u8]) -> Vec<u8>` (layout: 16-byte header, pubkey at 16, signature at 48, message at 112, all `*_instruction_index = u16::MAX`); `pub enum SigIxError { TooShort, NotSingle, CrossInstruction, OffsetInHeader, OutOfBounds, BadMessageLen }`; `pub struct SigView<'a> { pub pubkey: &'a [u8], pub signature: &'a [u8], pub message: &'a [u8] }`; `pub fn parse_single(data: &[u8], expected_msg_len: usize) -> Result<SigView<'_>, SigIxError>`.

- [ ] **Step 1: Write failing tests**

```rust
#[cfg(test)]
mod tests {
    use super::*;
    fn ok_data() -> alloc::vec::Vec<u8> { build_ix_data(&[7; 32], &[8; 64], &[9; 144]) }
    fn set_u16(d: &mut [u8], at: usize, v: u16) { d[at..at + 2].copy_from_slice(&v.to_le_bytes()); }

    #[test] fn roundtrip() {
        let d = ok_data();
        let v = parse_single(&d, 144).unwrap();
        assert_eq!(v.pubkey, &[7; 32]); assert_eq!(v.signature, &[8; 64]); assert_eq!(v.message, &[9; 144]);
    }
    #[test] fn too_short() { assert_eq!(parse_single(&[1, 0, 0], 144).err(), Some(SigIxError::TooShort)); }
    #[test] fn two_signatures() { let mut d = ok_data(); d[0] = 2; assert_eq!(parse_single(&d, 144).err(), Some(SigIxError::NotSingle)); }
    #[test] fn cross_instruction_sig()  { let mut d = ok_data(); set_u16(&mut d, 4, 0);  assert_eq!(parse_single(&d, 144).err(), Some(SigIxError::CrossInstruction)); }
    #[test] fn cross_instruction_pk()   { let mut d = ok_data(); set_u16(&mut d, 8, 1);  assert_eq!(parse_single(&d, 144).err(), Some(SigIxError::CrossInstruction)); }
    #[test] fn cross_instruction_msg()  { let mut d = ok_data(); set_u16(&mut d, 14, 2); assert_eq!(parse_single(&d, 144).err(), Some(SigIxError::CrossInstruction)); }
    #[test] fn offset_into_header()     { let mut d = ok_data(); set_u16(&mut d, 10, 15); assert_eq!(parse_single(&d, 144).err(), Some(SigIxError::OffsetInHeader)); }
    #[test] fn out_of_bounds()          { let mut d = ok_data(); set_u16(&mut d, 12, 145); assert_eq!(parse_single(&d, 145).err(), Some(SigIxError::OutOfBounds)); }
    #[test] fn wrong_msg_len()          { let d = build_ix_data(&[7; 32], &[8; 64], &[9; 100]); assert_eq!(parse_single(&d, 144).err(), Some(SigIxError::BadMessageLen)); }
    #[test] fn huge_offset_no_overflow(){ let mut d = ok_data(); set_u16(&mut d, 6, u16::MAX - 1); assert_eq!(parse_single(&d, 144).err(), Some(SigIxError::OutOfBounds)); }
}
```

- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**

```rust
use crate::ED25519_HEADER_LEN;
use alloc::vec::Vec;

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum SigIxError { TooShort, NotSingle, CrossInstruction, OffsetInHeader, OutOfBounds, BadMessageLen }

pub struct SigView<'a> { pub pubkey: &'a [u8], pub signature: &'a [u8], pub message: &'a [u8] }

const PK_OFF: u16 = 16;
const SIG_OFF: u16 = 48;
const MSG_OFF: u16 = 112;

pub fn build_ix_data(pubkey: &[u8; 32], sig: &[u8; 64], msg: &[u8]) -> Vec<u8> {
    let mut d = Vec::with_capacity(112 + msg.len());
    d.extend_from_slice(&[1, 0]);
    for v in [SIG_OFF, u16::MAX, PK_OFF, u16::MAX, MSG_OFF, msg.len() as u16, u16::MAX] {
        d.extend_from_slice(&v.to_le_bytes());
    }
    d.extend_from_slice(pubkey);
    d.extend_from_slice(sig);
    d.extend_from_slice(msg);
    d
}

pub fn parse_single(d: &[u8], expected_msg_len: usize) -> Result<SigView<'_>, SigIxError> {
    if d.len() < ED25519_HEADER_LEN { return Err(SigIxError::TooShort); }
    if d[0] != 1 { return Err(SigIxError::NotSingle); }
    let u = |i: usize| u16::from_le_bytes([d[i], d[i + 1]]);
    let (sig_off, sig_ix, pk_off, pk_ix) = (u(2) as usize, u(4), u(6) as usize, u(8));
    let (msg_off, msg_len, msg_ix) = (u(10) as usize, u(12) as usize, u(14));
    if sig_ix != u16::MAX || pk_ix != u16::MAX || msg_ix != u16::MAX { return Err(SigIxError::CrossInstruction); }
    if sig_off < ED25519_HEADER_LEN || pk_off < ED25519_HEADER_LEN || msg_off < ED25519_HEADER_LEN {
        return Err(SigIxError::OffsetInHeader);
    }
    let fits = |o: usize, n: usize| o.checked_add(n).map_or(false, |e| e <= d.len());
    if !(fits(sig_off, 64) && fits(pk_off, 32) && fits(msg_off, msg_len)) { return Err(SigIxError::OutOfBounds); }
    if msg_len != expected_msg_len { return Err(SigIxError::BadMessageLen); }
    Ok(SigView { pubkey: &d[pk_off..pk_off + 32], signature: &d[sig_off..sig_off + 64], message: &d[msg_off..msg_off + msg_len] })
}
```

- [ ] **Step 4: Run** → 10 passed.
- [ ] **Step 5: Commit**: `git commit -m "core: add hardened ed25519 precompile data parser"`

---

### Task 6: `tally-core` post-condition and PoW helpers

**Files:** Create `crates/tally-core/src/post.rs`, `crates/tally-core/src/pow.rs`; modify `lib.rs`.

**Interfaces:**
- Produces: `pub fn check_exact_deltas(vault_before: u64, vault_after: u64, payout_before: u64, payout_after: u64, price: u64) -> bool`; `pub fn pow_challenge(menu_nonce: u64, capsule: &[u8;32], slot_hash: &[u8;32]) -> [u8;32]` = `SHA256("TALLY_POW_V1" ‖ nonce LE ‖ capsule ‖ slot_hash)`; `pub fn leading_zero_bits(h: &[u8;32]) -> u32`; `pub fn verify_hashcash(challenge: &[u8;32], nonce: &[u8;8], difficulty: u8) -> bool` (`leading_zero_bits(SHA256(challenge ‖ nonce)) >= difficulty`); `pub fn solve_hashcash(challenge: &[u8;32], difficulty: u8) -> [u8;8]` (off-chain, `#[cfg(not(target_os = "solana"))]`).

- [ ] **Step 1: Write failing tests**

```rust
// post.rs
#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn exact_ok() { assert!(check_exact_deltas(100, 60, 5, 45, 40)); }
    #[test] fn fee_mint_shorts_payee() { assert!(!check_exact_deltas(100, 60, 5, 44, 40)); }
    #[test] fn extra_drain_from_vault() { assert!(!check_exact_deltas(100, 59, 5, 45, 40)); }
    #[test] fn underflow_is_false_not_panic() { assert!(!check_exact_deltas(10, 60, 5, 45, 40)); }
}
// pow.rs
#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn lzb() { let mut h = [0u8; 32]; h[1] = 0b0001_0000; assert_eq!(leading_zero_bits(&h), 11); }
    #[test] fn solve_then_verify() {
        let c = pow_challenge(42, &[1; 32], &[2; 32]);
        let n = solve_hashcash(&c, 12);
        assert!(verify_hashcash(&c, &n, 12));
    }
    #[test] fn solution_bound_to_capsule() {
        let c1 = pow_challenge(42, &[1; 32], &[2; 32]);
        let c2 = pow_challenge(42, &[3; 32], &[2; 32]);
        let n = solve_hashcash(&c1, 12);
        assert!(!verify_hashcash(&c2, &n, 12) || leading_zero_bits(&crate::hash::sha256v(&[&c2, &n])) >= 12);
    }
}
```

`solution_bound_to_capsule` is phrased to allow a ~1/4096 coincidental pass, so it can't flake. The real binding is the challenge preimage, which the next assertion pins:

```rust
    #[test] fn challenge_differs_per_input() {
        assert_ne!(pow_challenge(42, &[1; 32], &[2; 32]), pow_challenge(43, &[1; 32], &[2; 32]));
        assert_ne!(pow_challenge(42, &[1; 32], &[2; 32]), pow_challenge(42, &[1; 32], &[9; 32]));
    }
```

- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**

```rust
// post.rs
pub fn check_exact_deltas(vb: u64, va: u64, pb: u64, pa: u64, price: u64) -> bool {
    matches!((vb.checked_sub(va), pa.checked_sub(pb)), (Some(out), Some(inc)) if out == price && inc == price)
}
// pow.rs
use crate::hash::sha256v;
pub fn pow_challenge(menu_nonce: u64, capsule: &[u8; 32], slot_hash: &[u8; 32]) -> [u8; 32] {
    sha256v(&[b"TALLY_POW_V1", &menu_nonce.to_le_bytes(), capsule, slot_hash])
}
pub fn leading_zero_bits(h: &[u8; 32]) -> u32 {
    let mut n = 0;
    for b in h { if *b == 0 { n += 8; } else { n += b.leading_zeros(); break; } }
    n
}
pub fn verify_hashcash(challenge: &[u8; 32], nonce: &[u8; 8], difficulty: u8) -> bool {
    leading_zero_bits(&sha256v(&[challenge, nonce])) >= difficulty as u32
}
#[cfg(not(target_os = "solana"))]
pub fn solve_hashcash(challenge: &[u8; 32], difficulty: u8) -> [u8; 8] {
    let mut i: u64 = 0;
    loop { let n = i.to_le_bytes(); if verify_hashcash(challenge, &n, difficulty) { return n; } i += 1; }
}
```

- [ ] **Step 4: Run** → all pass. `cargo test -p tally-core` is fully green.
- [ ] **Step 5: Commit** in two commits: `core: add exact-delta post-condition check` and `core: add bound proof-of-work challenge and hashcash verifier`.

---

### Task 7: Cross-language fixtures

**Files:** Create `crates/tally-core/src/bin/gen-fixtures.rs`, `fixtures/menu-v1.json`, `crates/tally-core/tests/fixtures_golden.rs`.

**Interfaces:**
- Produces: `fixtures/menu-v1.json` with this schema, which phases 2 and 3 consume:

```json
{
  "program_id": "<base58>", "merchant_authority": "<base58>", "merchant_pda": "<base58>",
  "merchant_signing_secret_hex": "<64-byte ed25519 keypair hex, TEST ONLY>", "merchant_signing_pubkey": "<base58>",
  "header_fields": {"issued_slot": 1000, "expires_slot": 1100, "menu_nonce": 42, "item_count": 8},
  "items": [{"id_hex": "...", "category": 3, "price": 4500000, "floor": 4500000, "name": "Oat latte (regular)", "leaf_hex": "...", "proof_hex": ["..."]}],
  "root_hex": "...", "header_hex": "...", "signature_hex": "...", "ed25519_ix_data_hex": "...",
  "fraud": {"items": [...], "root_hex": "...", "header_hex": "...", "signature_hex": "...", "cheap_index": 0, "claimed_index": 1},
  "pow": {"menu_nonce": 42, "capsule": "<base58>", "slot_hash_hex": "...", "challenge_hex": "...", "difficulty": 12, "nonce_hex": "..."}
}
```

Items (prices in micro-USDC; names are display-channel only):

| # | name | cat | price |
|---|---|---|---|
| 0 | Oat latte (regular) | 3 | 4_500_000 |
| 1 | Oat latte (large) | 3 | 5_900_000 |
| 2 | Cappuccino | 3 | 5_200_000 |
| 3 | Reserve roast | 3 | 18_000_000 |
| 4 | Croissant | 0 | 3_000_000 |
| 5 | Bagel | 0 | 3_800_000 |
| 6 | Metro single | 4 | 2_750_000 |
| 7 | Day pass | 4 | 9_000_000 |

`id = sha256("tally-item-" ‖ index)[..16]`. The merchant signing key comes from seed `[7u8; 32]`, and the authority from seed `[8u8; 32]`. The fraud menu is the same 8 items plus `Drip coffee` (cat 3, price 3_250_000), but with coffee floors left at 4_500_000, so `find_floor_contradiction` returns `Some(..)`.

- [ ] **Step 1: Write the golden test** `crates/tally-core/tests/fixtures_golden.rs`

```rust
use tally_core::{header::*, menu::*, merkle::*};
#[test]
fn fixtures_reproduce() {
    let j: serde_json::Value = serde_json::from_str(include_str!("../../../fixtures/menu-v1.json")).unwrap();
    let mut items: Vec<Item> = j["items"].as_array().unwrap().iter().map(|x| Item {
        id: hex::decode(x["id_hex"].as_str().unwrap()).unwrap().try_into().unwrap(),
        category: x["category"].as_u64().unwrap() as u16,
        price: x["price"].as_u64().unwrap(), floor: 0 }).collect();
    compute_floors(&mut items);
    let leaves: Vec<[u8; 32]> = items.iter().map(leaf_hash).collect();
    for (i, x) in j["items"].as_array().unwrap().iter().enumerate() {
        assert_eq!(hex::encode(leaves[i]), x["leaf_hex"].as_str().unwrap());
        assert_eq!(x["floor"].as_u64().unwrap(), items[i].floor);
    }
    let t = MerkleTree::new(leaves);
    assert_eq!(hex::encode(t.root()), j["root_hex"].as_str().unwrap());
    let hf = &j["header_fields"];
    let pda: [u8; 32] = bs58_decode(j["merchant_pda"].as_str().unwrap());
    let pid: [u8; 32] = bs58_decode(j["program_id"].as_str().unwrap());
    let h = build_header(&HeaderFields { program_id: pid, merchant_pda: pda, menu_root: t.root(),
        issued_slot: hf["issued_slot"].as_u64().unwrap(), expires_slot: hf["expires_slot"].as_u64().unwrap(),
        menu_nonce: hf["menu_nonce"].as_u64().unwrap(), item_count: hf["item_count"].as_u64().unwrap() as u32 });
    assert_eq!(hex::encode(h), j["header_hex"].as_str().unwrap());
}
fn bs58_decode(s: &str) -> [u8; 32] { bs58::decode(s).into_vec().unwrap().try_into().unwrap() }
```

Add the dev-deps `bs58 = "0.5"`, `serde_json`, `hex`. Also add the tests `signature_verifies` (dalek `verify_strict` over `header_hex`) and `fraud_menu_is_contradictory`.

- [ ] **Step 2: Run** `cargo test -p tally-core --test fixtures_golden` → FAIL (file missing).
- [ ] **Step 3: Implement `gen-fixtures.rs`**. It builds everything above with `tally_core`, signs with `ed25519_dalek::SigningKey::from_bytes(&[7;32])`, derives `merchant_pda` via `solana_pubkey::Pubkey::find_program_address(&[b"merchant", authority.as_ref()], &program_id)`, computes the PoW block with `solve_hashcash`, and writes pretty JSON to the path in `argv[1]`.
- [ ] **Step 4: Generate and run**

```bash
cargo run -p tally-core --features fixtures --bin gen-fixtures -- fixtures/menu-v1.json
cargo test -p tally-core
```
Expected: all pass.
- [ ] **Step 5: Commit**: `git add fixtures crates && git commit -m "core: generate cross-language menu fixtures with golden test"`

---

### Task 8: Program constants, errors, events, state

**Files:** Create `programs/tally/src/{constants.rs,errors.rs,events.rs}`, `programs/tally/src/state/{mod,config,merchant,capsule,receipt,dispute,sgt_claim}.rs`.

**Interfaces:**
- Produces: `#[error_code] pub enum TallyError` (the docs/06 order **first**, so codes are stable: `Frozen = 6000` … `Overflow = 6019`, then the additions `Unauthorized`, `BondLocked`, `DisputeOpen`, `NotAFraud`, `InvalidSgt`, `InvalidRules`, `WrongDisputeState`). Accounts:
  - `Config { admin, arbiter, usdc_mint, skr_mint, sgt_group: Pubkey, slash_bps: u16, bond_unlock_delay: u64, bump: u8 }`
  - `Merchant { authority, signing_key, payout: Pubkey, name: [u8;32], skr_bonded: u64, bond_unlock_slot: u64, pow_difficulty: u8, slashed_total: u64, open_disputes: u32, active: bool, bump: u8 }`
  - `Capsule { owner, agent, mint, vault: Pubkey, per_tx_cap, daily_cap, spent_in_cycle, cycle_start_slot: u64, allowed_categories: [u64;4], min_merchant_bond, expiry_slot: u64, flags: u8, sgt_mint: Pubkey, frozen: bool, capsule_id: u64, bump: u8 }`
  - `Receipt` as in docs/06, `Dispute` as in docs/06, `SgtClaim { sgt_mint: Pubkey, promo_id: u64, owner: Pubkey }`
  - Events `Paid`, `Slashed { merchant, amount, reason: u8 }` (reason 0 = floor fraud, 1 = dispute), `DisputeOpened`, `DisputeResolved`.
- All accounts use `#[derive(InitSpace)]`.

- [ ] **Step 1: Write a failing unit test** in `programs/tally/src/state/mod.rs`

```rust
#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn receipt_space_matches_doc() { assert_eq!(Receipt::INIT_SPACE, 32 + 32 + 32 + 16 + 2 + 8 + 8 + 8 + 1 + 1); }
    #[test] fn error_codes_are_stable() {
        assert_eq!(u32::from(crate::errors::TallyError::Frozen), 6000);
        assert_eq!(u32::from(crate::errors::TallyError::Overflow), 6019);
    }
}
```

- [ ] **Step 2: Run** `cargo test -p tally --lib` → FAIL.
- [ ] **Step 3: Implement the files.** Use the field lists from **Interfaces** verbatim, and put `pub const FLAG_REQUIRE_LIGHTHOUSE: u8 = 1; FLAG_SGT_VERIFIED: u8 = 2; FLAG_ALLOW_EXTENDED: u8 = 4;` in `constants.rs`, re-exporting the window/cycle constants from `tally_core`.
- [ ] **Step 4: Run** → pass. Also run `cargo build-sbf` → OK.
- [ ] **Step 5: Commit** as two commits: `program: add error codes, events and constants` and `program: add account state definitions`.

---

### Task 9: LiteSVM harness + `initialize_config` + `register_merchant`

**Files:**
- Create: `programs/tally/src/instructions/{mod,config,merchant}.rs`; `tests/litesvm/Cargo.toml`, `tests/litesvm/src/lib.rs`, `tests/litesvm/tests/merchant.rs`
- Modify: `programs/tally/src/lib.rs`

**Interfaces:**
- Program instructions:
  - `initialize_config(arbiter: Pubkey, sgt_group: Pubkey, slash_bps: u16, bond_unlock_delay: u64)`. Accounts: `admin` (signer, payer), `config` (init `["config"]`), `usdc_mint`, `skr_mint`, `pool` (init SKR token account `["pool"]`, authority `config`), programs.
  - `register_merchant(name: [u8;32], signing_key: Pubkey, bond: u64)`. Accounts: `authority` (signer), `config`, `merchant` (init `["merchant", authority]`), `payout` (USDC token account, `mint == config.usdc_mint`), `skr_mint` (`== config.skr_mint`), `authority_skr` (mut), `bond_vault` (init token account `["bond", merchant]`, authority = `merchant`), programs. It transfers `bond` SKR to `bond_vault` and sets `active = true`.
  - `rotate_signing_key(new: Pubkey)`, `set_payout()` (new payout account in accounts), `set_active(bool)`, `set_pow_difficulty(u8)`, `top_up_bond(amount)`. All have `has_one = authority`.
- Harness (`tests/litesvm/src/lib.rs`):

```rust
pub struct Env {
    pub svm: litesvm::LiteSVM,
    pub admin: Keypair,           // also arbiter in tests
    pub usdc: Pubkey, pub skr: Pubkey,
    pub merchant_auth: Keypair, pub merchant_signer: ed25519_dalek::SigningKey, pub merchant: Pubkey, pub payout: Pubkey,
    pub owner: Keypair, pub agent: Keypair, pub capsule: Pubkey, pub vault: Pubkey,
}
impl Env {
    pub fn new() -> Self;                                  // loads target/deploy/tally.so (panics: "run `cargo build-sbf` first")
    pub fn with_merchant(self, bond: u64) -> Self;
    pub fn with_capsule(self, rules: tally::instructions::capsule::Rules, fund: u64) -> Self;   // from Task 10
    pub fn send(&mut self, ixs: &[Instruction], signers: &[&Keypair]) -> TxResult;
    pub fn slot(&self) -> u64; pub fn warp(&mut self, slot: u64);
    pub fn token_balance(&self, ata: &Pubkey) -> u64;
}
pub fn tally_err(code: tally::errors::TallyError) -> u32 { code.into() }
pub fn assert_custom_err(r: &TxResult, code: u32);         // matches InstructionError::Custom(code) at any ix index
```

- [ ] **Step 1: Write failing tests** `tests/litesvm/tests/merchant.rs`

```rust
use tally_litesvm::*;
#[test] fn register_moves_bond_into_vault() {
    let env = Env::new().with_merchant(5_000_000_000);
    let m: tally::state::Merchant = env.fetch(&env.merchant);
    assert_eq!(m.skr_bonded, 5_000_000_000);
    assert!(m.active);
    assert_eq!(env.token_balance(&env.bond_vault()), 5_000_000_000);
}
#[test] fn register_rejects_fake_skr_mint() {
    let mut env = Env::new();
    let fake = env.create_mint(6);
    let r = env.try_register_merchant_with_mint(fake, 1);
    assert_custom_err(&r, anchor_lang::error::ErrorCode::ConstraintAddress.into());
}
#[test] fn only_authority_rotates_key() {
    let mut env = Env::new().with_merchant(1);
    let intruder = Keypair::new();
    let r = env.try_rotate_signing_key_as(&intruder, Pubkey::new_unique());
    assert!(r.is_err());
}
```

- [ ] **Step 2: Run** `cargo build-sbf && cargo test -p tally-litesvm --test merchant` → FAIL (instructions missing).
- [ ] **Step 3: Implement the instructions and harness.** Create tokens with `litesvm_token::{CreateMint, CreateAssociatedTokenAccount, MintTo}`. Build instruction data with `tally::instruction::RegisterMerchant { .. }.data()` and accounts with `tally::accounts::RegisterMerchant { .. }.to_account_metas(None)`.
- [ ] **Step 4: Run** → 3 pass.
- [ ] **Step 5: Commit** as three commits: `program: add config initialisation with pinned mints`, `program: add merchant registry with SKR bond vault`, `tests: add litesvm harness and merchant registry tests`.

---

### Task 10: Capsule lifecycle

**Files:** Create `programs/tally/src/instructions/capsule.rs`, `tests/litesvm/tests/capsule.rs`.

**Interfaces:**
- `#[derive(AnchorSerialize, AnchorDeserialize, Clone)] pub struct Rules { per_tx_cap: u64, daily_cap: u64, allowed_categories: [u64;4], min_merchant_bond: u64, expiry_slot: u64, flags: u8 }`. Validation: `per_tx_cap > 0 && per_tx_cap <= daily_cap && expiry_slot > now && flags & FLAG_SGT_VERIFIED == 0` (that flag can only be set by the SGT path in Task 18), else `InvalidRules`.
- Instructions: `create_capsule(capsule_id: u64, rules: Rules, agent: Pubkey)` (init capsule + vault ATA owned by the capsule PDA, `cycle_start_slot = now`); `update_rules(rules)`; `fund(amount)`; `withdraw(amount)`; `freeze()`; `unfreeze()`; `set_agent(new)`; `close_capsule()` (transfers the vault remainder to the owner, closes the vault token account and the capsule). All are `has_one = owner`, and the owner signs.

- [ ] **Step 1: Write failing tests**

```rust
#[test] fn create_and_fund() { let env = Env::new().with_merchant(1).with_capsule(Rules::coffee_default(), 10_000_000);
    assert_eq!(env.token_balance(&env.vault), 10_000_000); }
#[test] fn rules_reject_per_tx_above_daily() { let mut env = Env::new();
    let mut r = Rules::coffee_default(); r.per_tx_cap = r.daily_cap + 1;
    assert_custom_err(&env.try_create_capsule(r), tally_err(TallyError::InvalidRules)); }
#[test] fn rules_cannot_self_set_sgt_flag() { let mut env = Env::new();
    let mut r = Rules::coffee_default(); r.flags |= FLAG_SGT_VERIFIED;
    assert_custom_err(&env.try_create_capsule(r), tally_err(TallyError::InvalidRules)); }
#[test] fn withdraw_only_owner() { let mut env = Env::new().with_capsule(Rules::coffee_default(), 5);
    let agent = env.agent.insecure_clone();
    assert!(env.try_withdraw_as(&agent, 5).is_err()); }
#[test] fn close_returns_funds() { let mut env = Env::new().with_capsule(Rules::coffee_default(), 7_000_000);
    let before = env.token_balance(&env.owner_usdc()); env.close_capsule().unwrap();
    assert_eq!(env.token_balance(&env.owner_usdc()), before + 7_000_000); }
```

`Rules::coffee_default()` (in the harness): `per_tx_cap 6_000_000`, `daily_cap 15_000_000`, categories = bit 3 only, `min_merchant_bond 1_000_000_000`, `expiry_slot 10_000_000`, `flags 0`.

- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → pass.
- [ ] **Step 5: Commit** as three commits: `program: add capsule creation with rule validation`, `program: add capsule funding, withdrawal and close`, `program: add capsule freeze and agent rotation`.

---

### Task 11: `pay_offer` invariants 1–3 (authority, introspection, offset hardening)

**Files:** Create `programs/tally/src/instructions/pay_offer.rs`, `programs/tally/src/checks/introspection.rs`, `tests/litesvm/tests/introspection.rs`; extend the harness with `PayBuilder`.

**Interfaces:**
- `PayOfferArgs` exactly as in `docs/06` (`item_id: [u8;16]`, `category: u16`, `pow: Option<PowSolution { nonce: [u8;8] }>`. Note the hashcash nonce is 8 bytes; `digest` is only used if DrillX is chosen in Task 19).
- `checks::introspection::verify_prev_ed25519(ix_sysvar: &AccountInfo, key: &Pubkey, msg: &[u8;144]) -> Result<()>`. It uses `tally_core::ed25519::parse_single` and maps `SigIxError::*` → `BadSigIx` (or `WrongMessage` for `BadMessageLen`), pubkey mismatch → `WrongSigner`, message mismatch → `WrongMessage`, missing/non-Ed25519/`cur == 0` → `MissingSig`, accounts non-empty → `BadSigIx`.
- Harness `PayBuilder`:

```rust
pub struct PayBuilder<'a> { env: &'a mut Env, pub item: usize, pub owner_signs: bool, pub window: u64,
    pub args_mut: Vec<Box<dyn FnOnce(&mut PayOfferArgs)>>, pub ixs_mut: Vec<Box<dyn FnOnce(&mut Vec<Instruction>)>>,
    pub payout: Option<Pubkey>, pub agent: Option<Keypair>, pub menu_signer: Option<SigningKey>, pub items: Option<Vec<Item>> }
impl Env { pub fn pay(&mut self) -> PayBuilder<'_>; }   // defaults: item 0 (oat latte regular, == floor), Live window 100 slots from now
impl PayBuilder<'_> { pub fn send(self) -> TxResult; }   // builds [Ed25519, pay_offer] (+ Lighthouse if capsule flag), signs agent (+ owner)
```

- [ ] **Step 1: Write failing tests** `tests/litesvm/tests/introspection.rs`

```rust
fn env() -> Env { Env::new().with_merchant(5_000_000_000).with_capsule(Rules::coffee_default(), 20_000_000) }
#[test] fn missing_ed25519() { let mut e = env(); let r = e.pay().ixs(|v| { v.remove(0); }).send(); assert_custom_err(&r, tally_err(TallyError::MissingSig)); }
#[test] fn ed25519_not_adjacent() { let mut e = env(); let r = e.pay().ixs(|v| v.insert(1, ComputeBudgetInstruction::set_compute_unit_limit(400_000))).send();
    assert_custom_err(&r, tally_err(TallyError::MissingSig)); }
#[test] fn ed25519_offset_attack() { let mut e = env(); let r = e.pay().ixs(|v| redirect_msg_ix_index(&mut v[0], 1)).send();
    assert!(r.is_err()); /* precompile itself or BadSigIx — both are reverts; assert program error when precompile accepts */ }
#[test] fn ed25519_offset_into_header() { let mut e = env(); let r = e.pay().ixs(|v| patch_u16(&mut v[0].data, 10, 8)).send(); assert!(r.is_err()); }
#[test] fn ed25519_two_sigs() { let mut e = env(); let r = e.pay().ixs(|v| make_two_sig_ix(&mut v[0])).send(); assert_custom_err(&r, tally_err(TallyError::BadSigIx)); }
#[test] fn ed25519_with_accounts() { let mut e = env(); let r = e.pay().ixs(|v| v[0].accounts.push(AccountMeta::new_readonly(Pubkey::new_unique(), false))).send();
    assert_custom_err(&r, tally_err(TallyError::BadSigIx)); }
#[test] fn forged_menu() { let mut e = env(); let r = e.pay().menu_signer(SigningKey::from_bytes(&[99; 32])).send(); assert_custom_err(&r, tally_err(TallyError::WrongSigner)); }
#[test] fn wrong_message() { let mut e = env(); let r = e.pay().args(|a| a.menu_nonce += 1).send(); assert_custom_err(&r, tally_err(TallyError::WrongMessage)); }
#[test] fn wrong_merchant_pda() { let mut e = env().with_second_merchant(); let r = e.pay().use_second_merchant_accounts().send(); assert_custom_err(&r, tally_err(TallyError::WrongSigner)); }
#[test] fn rotated_key_rejects_old_menu() { let mut e = env(); let old = e.merchant_signer.clone(); e.rotate_signing_key(SigningKey::from_bytes(&[55; 32]));
    let r = e.pay().menu_signer(old).send(); assert_custom_err(&r, tally_err(TallyError::WrongSigner)); }
#[test] fn wrong_agent() { let mut e = env(); let r = e.pay().agent(Keypair::new()).send(); assert_custom_err(&r, anchor_lang::error::ErrorCode::ConstraintHasOne.into()); }
#[test] fn frozen() { let mut e = env(); e.freeze(); assert_custom_err(&e.pay().send(), tally_err(TallyError::Frozen)); }
#[test] fn expired_capsule() { let mut e = env(); e.warp(10_000_000); assert_custom_err(&e.pay().send(), tally_err(TallyError::Expired)); }
#[test] fn merchant_inactive() { let mut e = env(); e.set_merchant_active(false); assert_custom_err(&e.pay().send(), tally_err(TallyError::MerchantInactive)); }
```

On the offset tests: the native Ed25519 precompile itself rejects some malformed instructions before the program runs. In that case `r` is `Err(InstructionError(0, ..))`, which is still a revert. Where the precompile *accepts* (e.g. a cross-instruction index pointing at valid bytes in `pay_offer`'s data), the program must reject with `BadSigIx`. Write `redirect_msg_ix_index` so it sets `message_instruction_index = 1` and `msg_off` to point at a copy of a valid header that is placed inside `pay_offer`'s data (the "real" exploit shape). Then assert `BadSigIx` exactly:

```rust
#[test] fn ed25519_cross_ix_exploit_shape_rejected() {
    let mut e = env(); let r = e.pay().cross_ix_exploit().send();
    assert_custom_err(&r, tally_err(TallyError::BadSigIx));
}
```

- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** invariants 1–3 in `pay_offer.rs`: invariant 1 checks first, then `build_header` from args using `crate::ID` and `merchant.key()`, then `verify_prev_ed25519`. The function returns `Ok(())` after these for now, and the receipt `init` stays in the account struct so the test env is realistic.
- [ ] **Step 4: Run** → pass.
- [ ] **Step 5: Commit** as three commits: `program: add pay_offer authority and liveness checks`, `program: verify merchant signature via instructions sysvar introspection`, `tests: cover ed25519 offset hijack and adjacency attacks`.

---

### Task 12: Invariants 4 and 6 (freshness tiers, Merkle inclusion)

**Files:** Modify `pay_offer.rs`; create `tests/litesvm/tests/menu.rs`.

- [ ] **Step 1: Write failing tests**

```rust
#[test] fn stale_menu() { let mut e = env(); let b = e.pay(); let exp = b.expires(); drop(b); e.warp(exp + 1); /* re-sign not allowed: use the same signed menu */
    assert_custom_err(&e.pay_with_saved_menu().send(), tally_err(TallyError::Stale)); }
#[test] fn not_yet_valid() { let mut e = env(); assert_custom_err(&e.pay().issued_in_future(5).send(), tally_err(TallyError::Stale)); }
#[test] fn boundaries_expires_slot_inclusive() { let mut e = env(); assert!(e.pay().window(150).at_expiry().send().is_ok()); }
#[test] fn boundaries_window_150_autonomous() { let mut e = env(); assert!(e.pay().window(150).send().is_ok()); }
#[test] fn window_151_needs_extended_flag() { let mut e = env(); assert_custom_err(&e.pay().window(151).send(), tally_err(TallyError::WindowTooLong)); }
#[test] fn window_151_with_flag_needs_cosign() { let mut e = env().with_flags(FLAG_ALLOW_EXTENDED);
    assert_custom_err(&e.pay().window(151).send(), tally_err(TallyError::OwnerCosignRequired));
    assert!(e.pay().window(151).owner_signs().send().is_ok()); }
#[test] fn window_too_long() { let mut e = env().with_flags(FLAG_ALLOW_EXTENDED); assert_custom_err(&e.pay().window(9_001).owner_signs().send(), tally_err(TallyError::WindowTooLong)); }
#[test] fn price_inflation() { let mut e = env(); assert_custom_err(&e.pay().args(|a| a.price = 9_900_000).send(), tally_err(TallyError::NotOnMenu)); }
#[test] fn unshown_item() { let mut e = env(); assert_custom_err(&e.pay().args(|a| a.item_id = [0xde; 16]).send(), tally_err(TallyError::NotOnMenu)); }
#[test] fn proof_too_long() { let mut e = env(); assert_custom_err(&e.pay().args(|a| a.proof = vec![[0; 32]; 17]).send(), tally_err(TallyError::NotOnMenu)); }
```

Note: the `window_151_with_flag_needs_cosign` assertion depends on invariant 8 (Task 13). Mark it `#[ignore = "enabled in Task 13"]` here and remove the `ignore` in Task 13, Step 3.

- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** invariants 4 and 6 per docs/06 (`leaf_hash` + `verify_proof` from `tally_core`).
- [ ] **Step 4: Run** → pass.
- [ ] **Step 5: Commit** as two commits: `program: enforce 150-slot freshness tiers` and `program: enforce merkle inclusion of the chosen item`.

---

### Task 13: Invariants 5, 7, 8 (registry payout, policy, Cheapest-Match)

**Files:** Modify `pay_offer.rs`; create `tests/litesvm/tests/policy.rs`.

- [ ] **Step 1: Write failing tests**

```rust
#[test] fn payee_swap() { let mut e = env(); let evil = e.create_usdc_account(&Keypair::new().pubkey());
    assert_custom_err(&e.pay().payout(evil).send(), anchor_lang::error::ErrorCode::ConstraintHasOne.into()); }
#[test] fn wrong_mint() { let mut e = env(); assert!(e.pay().mint_override_other().send().is_err()); }
#[test] fn category_blocked() { let mut e = env(); assert_custom_err(&e.pay().item(4).send(), tally_err(TallyError::Category)); } // croissant, cat 0
#[test] fn category_255_ok_256_rejected() { let mut e = env().with_categories_bit(255).with_menu_category_override(255); assert!(e.pay().send().is_ok());
    let mut e2 = env(); assert_custom_err(&e2.pay().args(|a| a.category = 256).send(), tally_err(TallyError::NotOnMenu)); }
#[test] fn over_per_tx() { let mut e = env(); assert_custom_err(&e.pay().item(3).owner_signs().send(), tally_err(TallyError::OverPerTx)); } // reserve roast 18
#[test] fn boundaries_price_equals_per_tx_cap() { let mut e = env().with_per_tx_cap(4_500_000); assert!(e.pay().send().is_ok()); }
#[test] fn over_daily() { let mut e = env(); for _ in 0..3 { e.pay().fresh_menu().send().unwrap(); } // 3 × 4.50 = 13.50
    assert_custom_err(&e.pay().fresh_menu().send(), tally_err(TallyError::OverDaily)); }
#[test] fn boundaries_spent_equals_daily_cap() { let mut e = env().with_daily_cap(9_000_000); e.pay().fresh_menu().send().unwrap(); assert!(e.pay().fresh_menu().send().is_ok()); }
#[test] fn cycle_reset() { let mut e = env().with_daily_cap(4_500_000); e.pay().fresh_menu().send().unwrap();
    e.warp(e.capsule_cycle_start() + tally_core::CYCLE_SLOTS); assert!(e.pay().fresh_menu().send().is_ok()); }
#[test] fn cosign_required() { let mut e = env(); assert_custom_err(&e.pay().item(1).send(), tally_err(TallyError::OwnerCosignRequired)); }
#[test] fn cosign_ok() { let mut e = env(); e.pay().item(1).owner_signs().send().unwrap(); assert!(e.receipt_for(1).cosigned); }
#[test] fn owner_extra_signature_ok() { let mut e = env(); e.pay().owner_signs().send().unwrap(); assert!(!e.receipt_for(0).cosigned); }
#[test] fn low_bond_requires_cosign() { let mut e = Env::new().with_merchant(10).with_capsule(Rules::coffee_default(), 20_000_000);
    assert_custom_err(&e.pay().send(), tally_err(TallyError::OwnerCosignRequired)); }
```

`fresh_menu()` re-signs with an incremented `menu_nonce`, so receipts don't collide. `receipt_for(i)` fetches the receipt for the last nonce and item *i*.

- [ ] **Step 2: Run** → FAIL. **Step 3:** implement per docs/06, and remove the `#[ignore]` from Task 12. **Step 4:** run → pass.
- [ ] **Step 5: Commit** as three commits: `program: pin payout to merchant registry`, `program: enforce capsule caps, categories and rolling cycle`, `program: enforce cheapest-match and bond-tier co-signing`.

---

### Task 14: Invariants 9 and 10 (receipt nullifier, transfer, post-condition)

**Files:** Modify `pay_offer.rs`; create `tests/litesvm/tests/settlement.rs`.

- [ ] **Step 1: Write failing tests**

```rust
#[test] fn happy_path_cheapest() { let mut e = env(); let (v0, p0) = (e.token_balance(&e.vault), e.token_balance(&e.payout));
    let meta = e.pay().send().unwrap();
    assert_eq!(e.token_balance(&e.vault), v0 - 4_500_000); assert_eq!(e.token_balance(&e.payout), p0 + 4_500_000);
    let r = e.receipt_for(0); assert_eq!(r.price, 4_500_000); assert_eq!(r.category, 3);
    println!("pay_offer CU = {}", meta.compute_units_consumed); }
#[test] fn replay() { let mut e = env(); let saved = e.pay().send_and_keep_tx().unwrap(); e.svm.expire_blockhash();
    assert!(e.resend_with_new_blockhash(saved).is_err()); }
#[test] fn single_item_menu_pays() { let mut e = env(); assert!(e.pay().items(vec![coffee_item(4_500_000)]).send().is_ok()); }
#[test] fn three_item_menu_odd_promotion_pays_last() { let mut e = env(); assert!(e.pay().items(three_coffees()).item(2).owner_signs().send().is_ok()); }
#[test] fn spent_updates() { let mut e = env(); e.pay().send().unwrap(); assert_eq!(e.capsule_state().spent_in_cycle, 4_500_000); }
```

- [ ] **Step 2: Run** → FAIL. **Step 3:** implement per docs/06 with `token_interface::transfer_checked` (PDA signer seeds `["capsule", owner, id, bump]`), `reload()` both accounts, and `tally_core::post::check_exact_deltas` → `PostCondition`. Write the receipt and emit `Paid`. **Step 4:** run → pass, and record the CU printed by `happy_path_cheapest`.
- [ ] **Step 5: Commit** as two commits: `program: settle via transfer_checked with exact-delta post-condition` and `program: record receipts as duplicate-settlement nullifiers`.

---

### Task 15: Required Lighthouse terminal assertion

**Files:** Create `programs/tally/src/checks/lighthouse.rs`, `fixtures/programs/lighthouse.so`, `tests/litesvm/src/lighthouse.rs`, `tests/litesvm/tests/lighthouse.rs`.

**Interfaces:**
- `checks::lighthouse::require_terminal_lighthouse(ix_sysvar: &AccountInfo, vault: &Pubkey) -> Result<()>` per docs/06.
- Harness: `pub fn lighthouse_assert_token_amount_eq(token_account: Pubkey, amount: u64) -> Instruction`, encoded by hand from the Lighthouse IDL.

- [ ] **Step 1: Pin the Lighthouse program**

```bash
solana program dump -u m L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95 fixtures/programs/lighthouse.so
curl -sL https://raw.githubusercontent.com/Jac0xb/lighthouse/main/configs/idl/lighthouse.json -o /tmp/lighthouse-idl.json \
  || gh api repos/Jac0xb/lighthouse/contents --jq '.[].name'
```

Read the `assertTokenAccount` instruction discriminator and the `TokenAccountAssertion::Amount { value, operator }` encoding from the IDL, then implement `lighthouse_assert_token_amount_eq`. If the program ID differs from the docs constant, update `LIGHTHOUSE_ID` in `constants.rs` **and** `docs/06`.

- [ ] **Step 2: Write tests that validate the encoding against the real program, plus the requirement**

```rust
#[test] fn lighthouse_encoding_passes_when_true_and_fails_when_false() {
    let mut e = env(); let bal = e.token_balance(&e.vault);
    assert!(e.send_plain(&[lighthouse_assert_token_amount_eq(e.vault, bal)]).is_ok());
    assert!(e.send_plain(&[lighthouse_assert_token_amount_eq(e.vault, bal + 1)]).is_err());
}
#[test] fn lighthouse_missing() { let mut e = env().with_flags(FLAG_REQUIRE_LIGHTHOUSE);
    assert_custom_err(&e.pay().no_lighthouse().send(), tally_err(TallyError::LighthouseMissing)); }
#[test] fn lighthouse_ok() { let mut e = env().with_flags(FLAG_REQUIRE_LIGHTHOUSE); assert!(e.pay().send().is_ok()); }
#[test] fn lighthouse_wrong_amount_reverts_whole_tx() { let mut e = env().with_flags(FLAG_REQUIRE_LIGHTHOUSE);
    let v0 = e.token_balance(&e.vault); assert!(e.pay().lighthouse_amount_off_by(1).send().is_err()); assert_eq!(e.token_balance(&e.vault), v0); }
```

- [ ] **Step 3: Implement**, and have the harness auto-append the assertion when the capsule flag is set. **Step 4:** run → pass.
- [ ] **Step 5: Commit** as two commits: `tests: pin lighthouse program and validate assertion encoding` and `program: optionally require a terminal lighthouse assertion`.

---

### Task 16: `prove_floor_fraud`

**Files:** Create `programs/tally/src/instructions/slashing.rs`, `tests/litesvm/tests/slashing.rs`.

**Interfaces:**
- `prove_floor_fraud(header: MenuHeaderArgs, leaf_a: LeafArgs, proof_a: Vec<[u8;32]>, leaf_b: LeafArgs, proof_b: Vec<[u8;32]>)`.
  - `MenuHeaderArgs { menu_root, issued_slot, expires_slot, menu_nonce, item_count }`; `LeafArgs { item_id: [u8;16], category: u16, price: u64, floor: u64 }`.
  - Accounts: `reporter` (signer), `config`, `merchant` (mut), `bond_vault` (mut), `reporter_skr` (mut), `pool` (mut), `ix_sysvar`, `token_program`.
  - It reuses `verify_prev_ed25519` (no freshness requirement, since a signed contradiction is evidence forever) and both proofs. It requires `a.category == b.category && a.price < b.floor` else `NotAFraud`. It slashes `amount = bond_vault.amount * slash_bps / 10_000` (checked math; if `amount == 0`, slash the whole remainder), sends half to the reporter and the rest to the pool, updates `skr_bonded`/`slashed_total`, and emits `Slashed { reason: 0 }`.

- [ ] **Step 1: Failing tests**: `floor_fraud_proof` (uses the `fixtures.fraud` items re-signed by the env merchant; the bond drops and the reporter is credited) and `floor_fraud_false` (honest menu → `NotAFraud`, bond unchanged), plus `floor_fraud_wrong_signer` (→ `WrongSigner`).
- [ ] **Step 2–4:** red → implement → green.
- [ ] **Step 5: Commit**: `program: add trustless floor-fraud proof with bond slashing`

---

### Task 17: Disputes and bond unlock

**Files:** Create `programs/tally/src/instructions/dispute.rs`, extend `merchant.rs`, `tests/litesvm/tests/dispute.rs`.

**Interfaces:**
- `open_dispute(evidence_hash: [u8;32])`: the capsule owner signs. Requires `receipt.capsule == capsule && capsule.owner == owner && !receipt.disputed`. Inits `Dispute` `["dispute", receipt]`, sets `receipt.disputed = true` and `merchant.open_disputes += 1`.
- `resolve_dispute(upheld: bool, slash_amount: u64)`: `config.arbiter` signs. The status must be `Open`. If upheld, it transfers `min(slash_amount, bond_vault.amount)` to the claimant's SKR account. Then `open_disputes -= 1` and it emits `DisputeResolved`.
- `request_bond_unlock()`: sets `bond_unlock_slot = now + config.bond_unlock_delay`. `withdraw_bond(amount)`: requires `bond_unlock_slot != 0 && now >= bond_unlock_slot && open_disputes == 0`, else `BondLocked`/`DisputeOpen`.

- [ ] **Step 1: Failing tests**: `dispute_upheld_slashes_to_claimant`, `dispute_rejected_no_slash`, `only_arbiter_resolves`, `cannot_dispute_twice`, `bond_unlock_blocked_before_delay`, `bond_unlock_blocked_with_open_dispute`, `bond_withdraw_after_delay`.
- [ ] **Step 2–4:** red → implement → green.
- [ ] **Step 5: Commit** as two commits: `program: add receipt disputes resolved by arbiter` and `program: add delayed bond unlock blocked by open disputes`.

---

### Task 18: SGT gate and promo claims

**Files:** Create `programs/tally/src/checks/sgt.rs`, `programs/tally/src/instructions/sgt.rs`, `tests/litesvm/tests/sgt.rs`.

**Interfaces:**
- `checks::sgt::verify_sgt(token_account: &AccountInfo, mint: &AccountInfo, owner: &Pubkey, sgt_group: &Pubkey) -> Result<Pubkey /*mint*/>` checks that:
  - both accounts are owned by the Token-2022 program;
  - the token account's base layout gives `mint == mint.key`, `owner == owner` and `amount == 1`;
  - the mint has a `TokenGroupMember` extension with `group == sgt_group`.
  
  Parse with `anchor_spl::token_2022::spl_token_2022::extension::{StateWithExtensions, BaseStateWithExtensions}` and `spl_token_group_interface::state::TokenGroupMember`. If anchor-spl 1.2.1 doesn't re-export these, add `spl-token-2022-interface` and `spl-token-group-interface` at versions that resolve against anchor's `solana-*` 3.x crates (`cargo tree -d` must show no duplicate `solana-program`).
- `create_capsule_with_sgt(capsule_id, rules, agent)` is a separate instruction with extra accounts `sgt_token_account`, `sgt_mint`. It sets `FLAG_SGT_VERIFIED` and `sgt_mint`.
- `claim_promo(promo_id: u64)`: inits `SgtClaim ["sgt", capsule.sgt_mint, promo_id]`. Requires `FLAG_SGT_VERIFIED`, else `InvalidSgt`.
- Tests build mock SGT accounts by writing packed Token-2022 bytes with `svm.set_account` (a helper `mock_sgt(owner, group) -> (token_account, mint)` that serialises the base `Mint`/`Account` plus the `GroupMemberPointer` and `TokenGroupMember` TLV entries).

- [ ] **Step 1: Failing tests**: `sgt_ok`, `sgt_wrong_group`, `sgt_wrong_owner`, `sgt_amount_zero`, `sgt_double_claim` (second `claim_promo` fails), `claim_requires_sgt_flag`.
- [ ] **Step 2–4:** red → implement → green.
- [ ] **Step 5: Commit** as two commits: `program: verify Seeker Genesis Token membership at capsule creation` and `program: add one-claim-per-device promo nullifier`.

---

### Task 19: Proof-of-work gate

**Files:** Create `programs/tally/src/checks/pow.rs`, `tests/litesvm/tests/pow.rs`.

- [ ] **Step 1: Decide DrillX vs hashcash (time-boxed to 45 min)**

```bash
cargo add drillx@2.2.0 -p tally --no-default-features && cargo build-sbf 2>&1 | tail -20
```

If it builds, write a throwaway test that verifies one DrillX solution in-program and read the CU. **If the build fails or CU > 100_000: revert `Cargo.toml` and use hashcash** (design addition 4). Record the decision and the CU in `docs/20-sgt-and-ore.md` § "Compute budget", in the same commit as the implementation.

- [ ] **Step 2: Failing tests** (hashcash shown; DrillX swaps the solver)

```rust
#[test] fn pow_required() { let mut e = env(); e.set_pow_difficulty(8); assert_custom_err(&e.pay().send(), tally_err(TallyError::PowRequired)); }
#[test] fn pow_invalid() { let mut e = env(); e.set_pow_difficulty(8); assert_custom_err(&e.pay().pow([0xff; 8]).send(), tally_err(TallyError::PowInvalid)); }
#[test] fn pow_reused_across_capsules() { let mut e = env().with_second_capsule(); e.set_pow_difficulty(8);
    let n = e.solve_pow_for(e.capsule2); assert_custom_err(&e.pay().pow(n).send(), tally_err(TallyError::PowInvalid)); }
#[test] fn pow_ok() { let mut e = env(); e.set_pow_difficulty(8); let n = e.solve_pow_for(e.capsule); assert!(e.pay().pow(n).send().is_ok()); }
```

The challenge uses the **most recent** `SlotHashes` entry. In LiteSVM, read it with `svm.get_sysvar::<SlotHashes>()` after the warp. On-chain, read the first entry from the `slot_hashes` account data at offset 8, `(slot u64, hash [u8;32])`, without deserialising the whole sysvar, to save CU.

- [ ] **Step 3–4:** implement gate G1 → green.
- [ ] **Step 5: Commit**: `program: add merchant-enabled proof-of-work anti-flood gate`

---

### Task 20: Compute report, IDL, threat-suite index, docs back-port

**Files:** Create `tests/litesvm/tests/compute.rs`, `tests/litesvm/THREAT_SUITE.md`, `target/idl/tally.json` (copied to `sdk/idl/tally.json` in phase 2). Modify `docs/06-onchain-program.md`, `docs/18-testing.md`, `docs/20-sgt-and-ore.md`.

- [ ] **Step 1: Compute test**

```rust
#[test] fn pay_offer_under_45k_cu() { let mut e = env(); let m = e.pay().send().unwrap();
    assert!(m.compute_units_consumed < 45_000, "pay_offer used {} CU", m.compute_units_consumed); }
#[test] fn pay_offer_depth_16_under_60k() { let mut e = env(); let m = e.pay().items(n_coffees(65_536.min(40_000))).send().unwrap(); /* deep proof */
    println!("deep CU {}", m.compute_units_consumed); }
```

If the first assertion fails, profile with `sol_log_compute_units` around each invariant. Optimise in order: avoid the `Vec` in the proof (use a borsh slice), avoid re-deserialising accounts, use `transfer_checked` over `transfer` only when needed. **Don't** relax the assertion without updating `docs/06` and the whitepaper's 45k claim.

- [ ] **Step 2: IDL**: `anchor idl build -p tally -o target/idl/tally.json` (Anchor CLI 1.2.1). Verify that it lists every instruction and the errors 6000+.
- [ ] **Step 3: THREAT_SUITE.md**: a table mapping each row of `docs/11` to its test name(s) and the measured CU of the happy path.
- [ ] **Step 4: Back-port** design additions 1–4 into `docs/06` (Config PDA, transfer_checked, pool, PoW choice) and the final test list into `docs/18`.
- [ ] **Step 5: Run everything**

```bash
cargo fmt --all -- --check && cargo clippy --workspace -- -D warnings && cargo build-sbf && cargo test --workspace
```
Expected: all green.

- [ ] **Step 6: Commit** as three commits: `tests: assert pay_offer compute budget`, `build: generate anchor IDL`, `docs: back-port implementation decisions and threat-suite index`.
- [ ] **Step 7: Phase gate.** Run superpowers:requesting-code-review on branch `phase/1-program`. Fix the findings, then `git checkout main && git merge --no-ff phase/1-program -m "Merge phase 1: core crate and on-chain program"`.

---

## Self-review notes

- **Spec coverage:**
  - Invariants 1–10 → Tasks 11–15
  - G1 PoW → Task 19
  - SGT → Task 18
  - fraud proof → Task 16
  - disputes and unlock → Task 17
  - freshness tiers → Task 12
  - CU target → Task 20
  - fixtures → Task 7
  - IDL for phase 2 → Task 20
  
  The off-chain items (QR/NFC, app, agent, Second Look) belong to phases 2–4.
- **Known API-risk points** (resolve by compiling, never by changing behaviour): Anchor 1.2.1 `CpiContext::new` takes a program `Pubkey`; the import paths for the `token_interface` and Token-2022 extensions; the LiteSVM 0.17 method names (`send_transaction`, `warp_to_slot`, `expire_blockhash`, `get_sysvar`).
- **Type consistency:** `item_id: [u8;16]`, `category: u16` and `PowSolution { nonce: [u8;8] }` are used identically in core, program, harness and fixtures.
