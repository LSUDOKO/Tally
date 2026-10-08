use anchor_lang::prelude::*;

#[event]
pub struct Paid {
    pub capsule: Pubkey,
    pub merchant: Pubkey,
    pub item_id: [u8; 16],
    pub price: u64,
    pub cosigned: bool,
}

/// `reason`: 0 = floor fraud proof, 1 = upheld dispute.
#[event]
pub struct Slashed {
    pub merchant: Pubkey,
    pub amount: u64,
    pub reason: u8,
}

#[event]
pub struct DisputeOpened {
    pub dispute: Pubkey,
    pub receipt: Pubkey,
    pub claimant: Pubkey,
}

#[event]
pub struct DisputeResolved {
    pub dispute: Pubkey,
    pub upheld: bool,
    pub slash_amount: u64,
}
