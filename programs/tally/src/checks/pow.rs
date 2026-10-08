//! Gate G1 (docs/20, Part B): when a merchant enables congestion mode, every `pay_offer` must carry
//! a proof-of-work nonce over
//! `SHA256("TALLY_POW_V1" || menu_nonce || capsule || slot_hash(pow.slot))`,
//! where `pow.slot` is a recent SlotHashes entry the client names (at most `MAX_POW_AGE` slots old),
//! so work mined against one slot still lands a few slots later on a live cluster.
//!
//! The work function is SHA-256 hashcash, not DrillX: verifying one DrillX (ORE v2) solution
//! on-chain measured ~448k CU in this program (Task 19 spike), far above our 100k budget, while
//! hashcash verification is a single `sol_sha256` call. The challenge binding is identical.
use anchor_lang::prelude::*;
use tally_core::pow::{pow_challenge, verify_hashcash};

use crate::{errors::TallyError, instructions::pay_offer::PowSolution};

/// Oldest slot a proof of work may be mined against (~60 s), matching the autonomous menu window.
pub const MAX_POW_AGE: u64 = 150;
const ENTRY_LEN: usize = 40;

/// Find `slot` in the SlotHashes sysvar bytes (`u64 len` then `(slot u64, hash [u8; 32])` entries,
/// newest first) without deserialising the whole sysvar.
pub fn slot_hash(slot_hashes: &AccountInfo, slot: u64) -> Result<[u8; 32]> {
    let d = slot_hashes.try_borrow_data()?;
    require!(d.len() >= 8, TallyError::PowInvalid);
    let n = u64::from_le_bytes(d[0..8].try_into().map_err(|_| TallyError::PowInvalid)?) as usize;
    for i in 0..n {
        let o = 8 + i * ENTRY_LEN;
        require!(o + ENTRY_LEN <= d.len(), TallyError::PowInvalid);
        let s = u64::from_le_bytes(d[o..o + 8].try_into().map_err(|_| TallyError::PowInvalid)?);
        if s == slot {
            let mut h = [0u8; 32];
            h.copy_from_slice(&d[o + 8..o + ENTRY_LEN]);
            return Ok(h);
        }
        if s < slot {
            break; // entries are sorted newest first
        }
    }
    err!(TallyError::PowInvalid)
}

#[inline(never)]
pub fn verify_pow(
    slot_hashes: &AccountInfo,
    capsule: &Pubkey,
    menu_nonce: u64,
    pow: Option<&PowSolution>,
    difficulty: u8,
    now: u64,
) -> Result<()> {
    let pow = pow.ok_or(TallyError::PowRequired)?;
    require!(
        pow.slot <= now && now - pow.slot <= MAX_POW_AGE,
        TallyError::PowInvalid
    );
    let challenge = pow_challenge(
        menu_nonce,
        &capsule.to_bytes(),
        &slot_hash(slot_hashes, pow.slot)?,
    );
    require!(
        verify_hashcash(&challenge, &pow.nonce, difficulty),
        TallyError::PowInvalid
    );
    Ok(())
}
