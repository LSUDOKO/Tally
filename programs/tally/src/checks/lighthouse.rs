//! Invariant 10, defence in depth: when a capsule sets `FLAG_REQUIRE_LIGHTHOUSE`, the last
//! instruction of the transaction must be exactly a Lighthouse
//! `AssertTokenAccount { Amount == vault_after }` over the vault. The program cannot make the
//! client *include* an assertion, but it refuses to pay without that precise one, so a
//! trivially-true assertion (e.g. `amount >= 0`) does not satisfy the requirement.
use anchor_lang::{compat::solana_instructions_sysvar as ix_sysvar, prelude::*};

use crate::{constants::LIGHTHOUSE_ID, errors::TallyError};

const ASSERT_TOKEN_ACCOUNT: u8 = 9;
const MAX_LOG_LEVEL: u8 = 6;
const ASSERTION_AMOUNT: u8 = 2;
const OP_EQUAL: u8 = 0;

pub fn require_terminal_lighthouse(
    ix_sysvar_account: &AccountInfo,
    vault: &Pubkey,
    vault_after: u64,
) -> Result<()> {
    let count = {
        let data = ix_sysvar_account.try_borrow_data()?;
        require!(data.len() >= 2, TallyError::LighthouseMissing);
        u16::from_le_bytes([data[0], data[1]]) as usize
    };
    let cur = ix_sysvar::load_current_index_checked(ix_sysvar_account)? as usize;
    require!(count > cur + 1, TallyError::LighthouseMissing);
    let last = ix_sysvar::load_instruction_at_checked(count - 1, ix_sysvar_account)?;
    require_keys_eq!(
        last.program_id,
        LIGHTHOUSE_ID,
        TallyError::LighthouseMissing
    );
    require!(
        last.accounts.first().is_some_and(|a| a.pubkey == *vault),
        TallyError::LighthouseMissing
    );
    let d = &last.data;
    require!(
        d.len() == 12
            && d[0] == ASSERT_TOKEN_ACCOUNT
            && d[1] <= MAX_LOG_LEVEL
            && d[2] == ASSERTION_AMOUNT
            && d[3..11] == vault_after.to_le_bytes()
            && d[11] == OP_EQUAL,
        TallyError::LighthouseMissing
    );
    Ok(())
}
