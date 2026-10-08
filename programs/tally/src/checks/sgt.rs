//! Seeker Genesis Token verification by direct, minimal parsing of Token-2022 account bytes:
//! the holder's token account (base layout) and the mint's `TokenGroupMember` TLV extension.
//! Kept dependency-free and explicit so it is easy to audit; tested against accounts produced
//! by the real Token-2022 program.
use anchor_lang::prelude::*;

use crate::errors::TallyError;

pub const TOKEN_2022_ID: Pubkey =
    Pubkey::from_str_const("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb");
/// `ExtensionType::TokenGroupMember` in spl-token-2022.
pub const TOKEN_GROUP_MEMBER_EXT: u16 = 23;
const BASE_ACCOUNT_LEN: usize = 165;
const ACCOUNT_TYPE_MINT: u8 = 1;

/// Returns the SGT mint when `token_account` is a Token-2022 account owned by `owner` holding
/// exactly 1 token of `mint`, and `mint` is a member of `sgt_group`.
pub fn verify_sgt(
    token_account: &AccountInfo,
    mint: &AccountInfo,
    owner: &Pubkey,
    sgt_group: &Pubkey,
) -> Result<Pubkey> {
    require_keys_eq!(*token_account.owner, TOKEN_2022_ID, TallyError::InvalidSgt);
    require_keys_eq!(*mint.owner, TOKEN_2022_ID, TallyError::InvalidSgt);
    {
        let d = token_account.try_borrow_data()?;
        require!(d.len() >= BASE_ACCOUNT_LEN, TallyError::InvalidSgt);
        require!(&d[0..32] == mint.key.as_ref(), TallyError::InvalidSgt);
        require!(&d[32..64] == owner.as_ref(), TallyError::InvalidSgt);
        require!(
            u64::from_le_bytes(d[64..72].try_into().map_err(|_| TallyError::InvalidSgt)?) == 1,
            TallyError::InvalidSgt
        );
    }
    let d = mint.try_borrow_data()?;
    require!(
        d.len() > BASE_ACCOUNT_LEN && d[BASE_ACCOUNT_LEN] == ACCOUNT_TYPE_MINT,
        TallyError::InvalidSgt
    );
    let mut i = BASE_ACCOUNT_LEN + 1;
    while i + 4 <= d.len() {
        let ty = u16::from_le_bytes([d[i], d[i + 1]]);
        let len = u16::from_le_bytes([d[i + 2], d[i + 3]]) as usize;
        let start = i + 4;
        let end = start.checked_add(len).ok_or(TallyError::InvalidSgt)?;
        require!(end <= d.len(), TallyError::InvalidSgt);
        if ty == TOKEN_GROUP_MEMBER_EXT {
            require!(len >= 64, TallyError::InvalidSgt);
            require!(
                &d[start..start + 32] == mint.key.as_ref(),
                TallyError::InvalidSgt
            );
            require!(
                &d[start + 32..start + 64] == sgt_group.as_ref(),
                TallyError::InvalidSgt
            );
            return Ok(*mint.key);
        }
        if ty == 0 {
            break;
        }
        i = end;
    }
    err!(TallyError::InvalidSgt)
}
