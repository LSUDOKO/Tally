//! Gate G1 (docs/20, Part B): when a merchant enables congestion mode, every `pay_offer` must carry
//! a proof-of-work nonce over
//! `SHA256("TALLY_POW_V1" || menu_nonce || capsule || most recent SlotHashes entry)`.
//!
//! The work function is SHA-256 hashcash, not DrillX: verifying one DrillX (ORE v2) solution
//! on-chain measured ~448k CU in this program (Task 19 spike), far above our 100k budget, while
//! hashcash verification is a single `sol_sha256` call. The challenge binding is identical.
use anchor_lang::prelude::*;
use tally_core::pow::{pow_challenge, verify_hashcash};

use crate::{errors::TallyError, instructions::pay_offer::PowSolution};

/// Read the most recent slot hash directly from the SlotHashes sysvar bytes
/// (`u64 len` then `(slot u64, hash [u8; 32])` entries), avoiding a full deserialisation.
pub fn recent_slot_hash(slot_hashes: &AccountInfo) -> Result<[u8; 32]> {
    let d = slot_hashes.try_borrow_data()?;
    require!(d.len() >= 48, TallyError::PowInvalid);
    let mut h = [0u8; 32];
    h.copy_from_slice(&d[16..48]);
    Ok(h)
}

#[inline(never)]
pub fn verify_pow(
    slot_hashes: &AccountInfo,
    capsule: &Pubkey,
    menu_nonce: u64,
    pow: Option<&PowSolution>,
    difficulty: u8,
) -> Result<()> {
    let pow = pow.ok_or(TallyError::PowRequired)?;
    let challenge = pow_challenge(
        menu_nonce,
        &capsule.to_bytes(),
        &recent_slot_hash(slot_hashes)?,
    );
    require!(
        verify_hashcash(&challenge, &pow.nonce, difficulty),
        TallyError::PowInvalid
    );
    Ok(())
}
