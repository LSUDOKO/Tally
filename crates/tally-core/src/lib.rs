//! Byte-level primitives shared by the Tally on-chain program, the TypeScript SDK and the Android app.
//! Every layout here is normative: see `docs/06-onchain-program.md` and `docs/07-menu-and-qr-spec.md`.
#![cfg_attr(not(test), no_std)]
extern crate alloc;

/// Base58 program id; must equal the `declare_id!` in `programs/tally`.
pub const PROGRAM_ID_STR: &str = "3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk";
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

pub mod hash;
pub mod header;
pub mod menu;
pub mod merkle;
pub mod ed25519;
pub mod post;
pub mod pow;
