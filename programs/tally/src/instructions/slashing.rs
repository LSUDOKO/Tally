//! `prove_floor_fraud`: trustless slashing. A merchant certifies `floor` as the cheapest price in a
//! category; if the *same signed menu* contains a leaf in that category priced below another leaf's
//! certified floor, the merchant has signed a contradiction. Anyone may prove it; part of the SKR
//! bond goes to the reporter and the rest to the compensation pool. One slash per menu root.
use anchor_lang::prelude::*;
use anchor_spl::token_interface::{self, Mint, TokenAccount, TokenInterface, TransferChecked};
use tally_core::{
    header::{build_header, HeaderFields},
    menu::{leaf_hash, Item},
    merkle::verify_proof,
};

use crate::{
    checks::introspection::verify_prev_ed25519,
    constants::*,
    errors::TallyError,
    events::Slashed,
    state::{Config, FraudRecord, Merchant},
};

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Debug, PartialEq, Eq)]
pub struct MenuHeaderArgs {
    pub menu_root: [u8; 32],
    pub issued_slot: u64,
    pub expires_slot: u64,
    pub menu_nonce: u64,
    pub item_count: u32,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, PartialEq, Eq)]
pub struct LeafArgs {
    pub item_id: [u8; 16],
    pub category: u16,
    pub price: u64,
    pub floor: u64,
}

impl From<LeafArgs> for Item {
    fn from(l: LeafArgs) -> Self {
        Item {
            id: l.item_id,
            category: l.category,
            price: l.price,
            floor: l.floor,
        }
    }
}

#[derive(Accounts)]
#[instruction(header: MenuHeaderArgs)]
pub struct ProveFloorFraud<'info> {
    #[account(mut)]
    pub reporter: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    #[account(mut)]
    pub merchant: Box<Account<'info, Merchant>>,
    #[account(mut, seeds = [BOND_SEED, merchant.key().as_ref()], bump)]
    pub bond_vault: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(address = config.skr_mint)]
    pub skr_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(mut, token::mint = skr_mint)]
    pub reporter_skr: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(mut, seeds = [POOL_SEED], bump)]
    pub pool: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(
        init, payer = reporter, space = 8 + FraudRecord::INIT_SPACE,
        seeds = [FRAUD_SEED, merchant.key().as_ref(), &header.menu_root], bump,
    )]
    pub fraud_record: Box<Account<'info, FraudRecord>>,
    /// CHECK: address-checked instructions sysvar.
    #[account(address = solana_sdk_ids::sysvar::instructions::ID)]
    pub ix_sysvar: UncheckedAccount<'info>,
    pub token_program: Interface<'info, TokenInterface>,
    pub system_program: Program<'info, System>,
}

pub fn prove_floor_fraud(
    ctx: Context<ProveFloorFraud>,
    header: MenuHeaderArgs,
    leaf_a: LeafArgs,
    proof_a: Vec<[u8; 32]>,
    leaf_b: LeafArgs,
    proof_b: Vec<[u8; 32]>,
) -> Result<()> {
    let merchant_key = ctx.accounts.merchant.key();
    let authority = ctx.accounts.merchant.authority;
    require!(
        ctx.accounts.reporter.key() != authority && ctx.accounts.reporter_skr.owner != authority,
        TallyError::SelfReport
    );
    // Evidence is the merchant's own signature; no freshness requirement — a signed
    // contradiction stays evidence forever.
    let msg = build_header(&HeaderFields {
        program_id: crate::ID.to_bytes(),
        merchant_pda: merchant_key.to_bytes(),
        menu_root: header.menu_root,
        issued_slot: header.issued_slot,
        expires_slot: header.expires_slot,
        menu_nonce: header.menu_nonce,
        item_count: header.item_count,
    });
    verify_prev_ed25519(
        &ctx.accounts.ix_sysvar.to_account_info(),
        &ctx.accounts.merchant.signing_key,
        &msg,
    )?;
    require!(
        verify_proof(leaf_hash(&leaf_a.into()), &proof_a, header.menu_root),
        TallyError::NotOnMenu
    );
    require!(
        verify_proof(leaf_hash(&leaf_b.into()), &proof_b, header.menu_root),
        TallyError::NotOnMenu
    );
    require!(
        leaf_a.category == leaf_b.category && leaf_a.price < leaf_b.floor,
        TallyError::NotAFraud
    );

    let vault_amount = ctx.accounts.bond_vault.amount;
    let bps = u128::from(ctx.accounts.config.slash_bps);
    let mut amount =
        u64::try_from(u128::from(vault_amount) * bps / 10_000).map_err(|_| TallyError::Overflow)?;
    if amount == 0 {
        amount = vault_amount;
    }
    let to_reporter = amount * REPORTER_BOUNTY_BPS / 10_000;
    let to_pool = amount - to_reporter;

    let m = &ctx.accounts.merchant;
    let (authority, bump) = (m.authority, [m.bump]);
    let seeds: &[&[u8]] = &[MERCHANT_SEED, authority.as_ref(), &bump];
    let decimals = ctx.accounts.skr_mint.decimals;
    for (to, value) in [
        (ctx.accounts.reporter_skr.to_account_info(), to_reporter),
        (ctx.accounts.pool.to_account_info(), to_pool),
    ] {
        if value > 0 {
            token_interface::transfer_checked(
                CpiContext::new_with_signer(
                    ctx.accounts.token_program.key(),
                    TransferChecked {
                        from: ctx.accounts.bond_vault.to_account_info(),
                        mint: ctx.accounts.skr_mint.to_account_info(),
                        to,
                        authority: ctx.accounts.merchant.to_account_info(),
                    },
                    &[seeds],
                ),
                value,
                decimals,
            )?;
        }
    }

    let m = &mut ctx.accounts.merchant;
    m.skr_bonded = m.skr_bonded.saturating_sub(amount);
    m.slashed_total = m
        .slashed_total
        .checked_add(amount)
        .ok_or(TallyError::Overflow)?;
    ctx.accounts.fraud_record.set_inner(FraudRecord {
        merchant: merchant_key,
        menu_root: header.menu_root,
        reporter: ctx.accounts.reporter.key(),
        amount,
        slot: Clock::get()?.slot,
    });
    emit!(Slashed {
        merchant: merchant_key,
        amount,
        reason: 0
    });
    Ok(())
}

/// Pay a user out of the compensation pool (funded by fraud slashes). Arbiter only.
#[derive(Accounts)]
pub struct Compensate<'info> {
    pub arbiter: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump, has_one = arbiter @ TallyError::Unauthorized)]
    pub config: Box<Account<'info, Config>>,
    #[account(mut, seeds = [POOL_SEED], bump)]
    pub pool: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(address = config.skr_mint)]
    pub skr_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(mut, token::mint = skr_mint)]
    pub destination: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
}

pub fn compensate(ctx: Context<Compensate>, amount: u64) -> Result<()> {
    let bump = [ctx.accounts.config.bump];
    let seeds: &[&[u8]] = &[CONFIG_SEED, &bump];
    token_interface::transfer_checked(
        CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            TransferChecked {
                from: ctx.accounts.pool.to_account_info(),
                mint: ctx.accounts.skr_mint.to_account_info(),
                to: ctx.accounts.destination.to_account_info(),
                authority: ctx.accounts.config.to_account_info(),
            },
            &[seeds],
        ),
        amount,
        ctx.accounts.skr_mint.decimals,
    )
}
