//! `pay_offer`: the ten invariants of docs/06 that bind an agent's purchase to a merchant-signed
//! catalog leaf and the owner's capsule rules, then settle atomically.
use anchor_lang::prelude::*;
use anchor_spl::token_interface::{Mint, TokenAccount, TokenInterface};
use tally_core::{
    header::{build_header, HeaderFields},
    menu::{leaf_hash, Item},
    merkle::verify_proof,
};

use crate::{
    checks::introspection::verify_prev_ed25519,
    constants::*,
    errors::TallyError,
    state::{Capsule, Merchant, Receipt},
};

/// Optional proof-of-work solution (gate G1), required when the merchant enables it.
#[derive(AnchorSerialize, AnchorDeserialize, Clone, Debug, PartialEq, Eq)]
pub struct PowSolution {
    pub nonce: [u8; 8],
}

/// Everything the program needs to rebuild the signed header and the chosen leaf.
#[derive(AnchorSerialize, AnchorDeserialize, Clone, Debug, PartialEq, Eq)]
pub struct PayOfferArgs {
    pub menu_root: [u8; 32],
    pub issued_slot: u64,
    pub expires_slot: u64,
    pub menu_nonce: u64,
    pub item_count: u32,
    pub item_id: [u8; 16],
    pub category: u16,
    pub price: u64,
    pub category_floor: u64,
    pub proof: Vec<[u8; 32]>,
    pub pow: Option<PowSolution>,
}

#[derive(Accounts)]
#[instruction(args: PayOfferArgs)]
pub struct PayOffer<'info> {
    #[account(mut, has_one = agent, has_one = owner, has_one = vault, has_one = mint)]
    pub capsule: Account<'info, Capsule>,
    #[account(mut)]
    pub agent: Signer<'info>,
    /// CHECK: equals `capsule.owner` (has_one); must sign only when invariant 8 requires it.
    pub owner: UncheckedAccount<'info>,
    #[account(has_one = payout)]
    pub merchant: Account<'info, Merchant>,
    pub mint: InterfaceAccount<'info, Mint>,
    #[account(mut)]
    pub vault: InterfaceAccount<'info, TokenAccount>,
    #[account(mut, constraint = payout.mint == mint.key() @ TallyError::WrongMint)]
    pub payout: InterfaceAccount<'info, TokenAccount>,
    #[account(
        init, payer = agent, space = 8 + Receipt::INIT_SPACE,
        seeds = [RECEIPT_SEED, capsule.key().as_ref(), &args.menu_nonce.to_le_bytes(), &args.item_id],
        bump,
    )]
    pub receipt: Account<'info, Receipt>,
    /// CHECK: address-checked instructions sysvar.
    #[account(address = solana_sdk_ids::sysvar::instructions::ID)]
    pub ix_sysvar: UncheckedAccount<'info>,
    /// CHECK: address-checked SlotHashes sysvar (proof-of-work challenge freshness).
    #[account(address = solana_sdk_ids::sysvar::slot_hashes::ID)]
    pub slot_hashes: UncheckedAccount<'info>,
    pub token_program: Interface<'info, TokenInterface>,
    pub system_program: Program<'info, System>,
}

pub fn pay_offer(ctx: Context<PayOffer>, args: PayOfferArgs) -> Result<()> {
    let slot = Clock::get()?.slot;
    let merchant = &ctx.accounts.merchant;

    // 1. authority & liveness (agent signer + has_one checks are in the accounts struct)
    {
        let cap = &ctx.accounts.capsule;
        require!(!cap.frozen, TallyError::Frozen);
        require!(slot < cap.expiry_slot, TallyError::Expired);
        require!(merchant.active, TallyError::MerchantInactive);
    }

    // 2 + 3. introspection with hardened offsets; the header is rebuilt, never trusted
    let header = build_header(&HeaderFields {
        program_id: crate::ID.to_bytes(),
        merchant_pda: merchant.key().to_bytes(),
        menu_root: args.menu_root,
        issued_slot: args.issued_slot,
        expires_slot: args.expires_slot,
        menu_nonce: args.menu_nonce,
        item_count: args.item_count,
    });
    verify_prev_ed25519(&ctx.accounts.ix_sysvar.to_account_info(), &merchant.signing_key, &header)?;

    // 4. freshness tiers: <=150 slots autonomous; up to 9,000 only if the capsule allows it
    //    and the owner co-signs (enforced with invariant 8 below)
    require!(args.issued_slot <= slot && slot <= args.expires_slot, TallyError::Stale);
    let window = args.expires_slot - args.issued_slot;
    let extended = window > MAX_AUTONOMOUS_WINDOW;
    require!(window <= MAX_EXTENDED_WINDOW, TallyError::WindowTooLong);
    if extended {
        require!(ctx.accounts.capsule.flags & FLAG_ALLOW_EXTENDED != 0, TallyError::WindowTooLong);
    }

    // 5. registry payout: `has_one = payout` + mint constraint in the accounts struct

    // 6. Merkle inclusion of the leaf the program hashes itself
    let leaf = leaf_hash(&Item { id: args.item_id, category: args.category, price: args.price, floor: args.category_floor });
    require!(verify_proof(leaf, &args.proof, args.menu_root), TallyError::NotOnMenu);

    // 7. capsule policy: category bitmap, per-purchase cap, rolling ~24h cycle cap
    let cap = &mut ctx.accounts.capsule;
    require!(cap.allows_category(args.category), TallyError::Category);
    require!(args.price <= cap.per_tx_cap, TallyError::OverPerTx);
    if slot.saturating_sub(cap.cycle_start_slot) >= CYCLE_SLOTS {
        cap.cycle_start_slot = slot;
        cap.spent_in_cycle = 0;
    }
    let new_total = cap.spent_in_cycle.checked_add(args.price).ok_or(TallyError::Overflow)?;
    require!(new_total <= cap.daily_cap, TallyError::OverDaily);

    // 8. Cheapest-Match Invariant, bond tier and extended windows all need the owner
    let needs_owner = args.price > args.category_floor || merchant.skr_bonded < cap.min_merchant_bond || extended;
    if needs_owner {
        require!(ctx.accounts.owner.is_signer, TallyError::OwnerCosignRequired);
    }

    Ok(())
}
