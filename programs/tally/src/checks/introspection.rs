//! Invariants 2 and 3: the instruction immediately before `pay_offer` must be a native Ed25519
//! precompile call carrying exactly one self-contained signature by `key` over `msg`.
use anchor_lang::{compat::solana_instructions_sysvar as ix_sysvar, prelude::*};
use tally_core::{
    ed25519::{parse_single, SigIxError},
    HEADER_LEN,
};

use crate::errors::TallyError;

pub fn verify_prev_ed25519(ix_sysvar_account: &AccountInfo, key: &Pubkey, msg: &[u8; HEADER_LEN]) -> Result<()> {
    let cur = ix_sysvar::load_current_index_checked(ix_sysvar_account)? as usize;
    require!(cur > 0, TallyError::MissingSig);
    let ix = ix_sysvar::load_instruction_at_checked(cur - 1, ix_sysvar_account)?;
    require_keys_eq!(ix.program_id, solana_sdk_ids::ed25519_program::ID, TallyError::MissingSig);
    require!(ix.accounts.is_empty(), TallyError::BadSigIx);
    let view = parse_single(&ix.data, HEADER_LEN).map_err(|e| match e {
        SigIxError::BadMessageLen => TallyError::WrongMessage,
        _ => TallyError::BadSigIx,
    })?;
    require!(view.pubkey == key.as_ref(), TallyError::WrongSigner);
    require!(view.message == &msg[..], TallyError::WrongMessage);
    Ok(())
}
