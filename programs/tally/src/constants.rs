pub use tally_core::{CYCLE_SLOTS, HEADER_LEN, MAX_AUTONOMOUS_WINDOW, MAX_EXTENDED_WINDOW, MAX_PROOF_DEPTH};

pub const CONFIG_SEED: &[u8] = b"config";
pub const MERCHANT_SEED: &[u8] = b"merchant";
pub const CAPSULE_SEED: &[u8] = b"capsule";
pub const RECEIPT_SEED: &[u8] = b"receipt";
pub const DISPUTE_SEED: &[u8] = b"dispute";
pub const BOND_SEED: &[u8] = b"bond";
pub const SGT_SEED: &[u8] = b"sgt";
pub const POOL_SEED: &[u8] = b"pool";

/// Capsule flag: the transaction must end with a Lighthouse assertion over the vault.
pub const FLAG_REQUIRE_LIGHTHOUSE: u8 = 1 << 0;
/// Capsule flag: owner proved Seeker Genesis Token ownership (set only by `create_capsule_with_sgt`).
pub const FLAG_SGT_VERIFIED: u8 = 1 << 1;
/// Capsule flag: menus with windows above 150 slots are allowed (still need owner co-sign).
pub const FLAG_ALLOW_EXTENDED: u8 = 1 << 2;
pub const KNOWN_FLAGS: u8 = FLAG_REQUIRE_LIGHTHOUSE | FLAG_SGT_VERIFIED | FLAG_ALLOW_EXTENDED;

/// Lighthouse assertion program (Jac0xb/lighthouse), verified by dumping mainnet into
/// `fixtures/programs/lighthouse.so` and executing real assertions in the test suite.
pub const LIGHTHOUSE_ID: anchor_lang::prelude::Pubkey =
    anchor_lang::prelude::Pubkey::from_str_const("L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95");
pub const FRAUD_SEED: &[u8] = b"fraud";
