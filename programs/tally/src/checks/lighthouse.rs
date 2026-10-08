//! Invariant 10, defence in depth: when a capsule sets `FLAG_REQUIRE_LIGHTHOUSE`, the last
//! instruction of the transaction must be a Lighthouse assertion that reads the vault. The
//! program cannot make the client *include* an assertion, but it can refuse to pay without one.
use anchor_lang::{compat::solana_instructions_sysvar as ix_sysvar, prelude::*};

use crate::{constants::LIGHTHOUSE_ID, errors::TallyError};

pub fn require_terminal_lighthouse(ix_sysvar_account: &AccountInfo, vault: &Pubkey) -> Result<()> {
    let count = {
        let data = ix_sysvar_account.try_borrow_data()?;
        require!(data.len() >= 2, TallyError::LighthouseMissing);
        u16::from_le_bytes([data[0], data[1]]) as usize
    };
    let cur = ix_sysvar::load_current_index_checked(ix_sysvar_account)? as usize;
    require!(count > cur + 1, TallyError::LighthouseMissing);
    let last = ix_sysvar::load_instruction_at_checked(count - 1, ix_sysvar_account)?;
    require_keys_eq!(last.program_id, LIGHTHOUSE_ID, TallyError::LighthouseMissing);
    require!(last.accounts.first().is_some_and(|a| a.pubkey == *vault), TallyError::LighthouseMissing);
    Ok(())
}
