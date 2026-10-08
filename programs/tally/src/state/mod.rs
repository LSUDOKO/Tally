//! On-chain accounts (docs/06, "Accounts").
use anchor_lang::prelude::*;

/// Protocol configuration pinned at deploy: trusted mints, arbiter and slashing parameters.
#[account]
#[derive(InitSpace)]
pub struct Config {
    pub admin: Pubkey,
    pub arbiter: Pubkey,
    pub usdc_mint: Pubkey,
    pub skr_mint: Pubkey,
    pub sgt_group: Pubkey,
    pub slash_bps: u16,
    pub bond_unlock_delay: u64,
    pub bump: u8,
}

/// Merchant registry entry, seeded by the merchant's master authority so the menu key can rotate.
#[account]
#[derive(InitSpace)]
pub struct Merchant {
    pub authority: Pubkey,
    pub signing_key: Pubkey,
    pub payout: Pubkey,
    pub name: [u8; 32],
    pub skr_bonded: u64,
    pub bond_unlock_slot: u64,
    pub pow_difficulty: u8,
    pub slashed_total: u64,
    pub open_disputes: u32,
    pub active: bool,
    pub bump: u8,
}

/// A user's on-chain intent: rules plus a USDC vault the agent key may spend from.
#[account]
#[derive(InitSpace)]
pub struct Capsule {
    pub owner: Pubkey,
    pub agent: Pubkey,
    pub mint: Pubkey,
    pub vault: Pubkey,
    pub per_tx_cap: u64,
    pub daily_cap: u64,
    pub spent_in_cycle: u64,
    pub cycle_start_slot: u64,
    pub allowed_categories: [u64; 4],
    pub min_merchant_bond: u64,
    pub expiry_slot: u64,
    pub flags: u8,
    pub sgt_mint: Pubkey,
    pub frozen: bool,
    pub capsule_id: u64,
    pub bump: u8,
}

impl Capsule {
    pub fn allows_category(&self, category: u16) -> bool {
        let c = usize::from(category);
        c < 256 && self.allowed_categories[c / 64] & (1u64 << (c % 64)) != 0
    }
}

/// One settled purchase. Its address `(capsule, menu_nonce, item_id)` is the duplicate-settlement
/// nullifier, and its contents are dispute evidence.
#[account]
#[derive(InitSpace)]
pub struct Receipt {
    pub capsule: Pubkey,
    pub merchant: Pubkey,
    pub menu_root: [u8; 32],
    pub item_id: [u8; 16],
    pub category: u16,
    pub price: u64,
    pub category_floor: u64,
    pub slot: u64,
    pub cosigned: bool,
    pub disputed: bool,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, PartialEq, Eq, Debug, InitSpace)]
pub enum DisputeStatus {
    Open,
    Upheld,
    Rejected,
}

#[account]
#[derive(InitSpace)]
pub struct Dispute {
    pub receipt: Pubkey,
    pub claimant: Pubkey,
    pub merchant: Pubkey,
    pub evidence_hash: [u8; 32],
    pub status: DisputeStatus,
    pub slash_amount: u64,
    pub opened_slot: u64,
    pub bump: u8,
}

/// One promo claim per Seeker Genesis Token per promo id.
#[account]
#[derive(InitSpace)]
pub struct SgtClaim {
    pub sgt_mint: Pubkey,
    pub promo_id: u64,
    pub owner: Pubkey,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn receipt_space_matches_doc() {
        assert_eq!(Receipt::INIT_SPACE, 32 + 32 + 32 + 16 + 2 + 8 + 8 + 8 + 1 + 1);
    }

    #[test]
    fn error_codes_are_stable() {
        assert_eq!(u32::from(crate::errors::TallyError::Frozen), 6000);
        assert_eq!(u32::from(crate::errors::TallyError::Overflow), 6019);
    }
}
