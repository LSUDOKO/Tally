//! Merchant registry: registration with an SKR bond, and authority-only maintenance.
use anchor_lang::prelude::*;
use anchor_spl::token_interface::{self, Mint, TokenAccount, TokenInterface, TransferChecked};

use crate::{
    constants::*,
    errors::TallyError,
    state::{Config, Merchant},
};

#[derive(Accounts)]
pub struct RegisterMerchant<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Config>,
    #[account(
        init, payer = authority, space = 8 + Merchant::INIT_SPACE,
        seeds = [MERCHANT_SEED, authority.key().as_ref()], bump,
    )]
    pub merchant: Account<'info, Merchant>,
    #[account(token::mint = config.usdc_mint)]
    pub payout: InterfaceAccount<'info, TokenAccount>,
    #[account(address = config.skr_mint)]
    pub skr_mint: InterfaceAccount<'info, Mint>,
    #[account(mut, token::mint = skr_mint, token::authority = authority)]
    pub authority_skr: InterfaceAccount<'info, TokenAccount>,
    #[account(
        init, payer = authority, seeds = [BOND_SEED, merchant.key().as_ref()], bump,
        token::mint = skr_mint, token::authority = merchant, token::token_program = token_program,
    )]
    pub bond_vault: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
    pub system_program: Program<'info, System>,
}

pub fn register_merchant(
    ctx: Context<RegisterMerchant>,
    name: [u8; 32],
    signing_key: Pubkey,
    bond: u64,
) -> Result<()> {
    if bond > 0 {
        token_interface::transfer_checked(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.authority_skr.to_account_info(),
                    mint: ctx.accounts.skr_mint.to_account_info(),
                    to: ctx.accounts.bond_vault.to_account_info(),
                    authority: ctx.accounts.authority.to_account_info(),
                },
            ),
            bond,
            ctx.accounts.skr_mint.decimals,
        )?;
    }
    ctx.accounts.merchant.set_inner(Merchant {
        authority: ctx.accounts.authority.key(),
        signing_key,
        payout: ctx.accounts.payout.key(),
        name,
        skr_bonded: bond,
        bond_unlock_slot: 0,
        pow_difficulty: 0,
        slashed_total: 0,
        open_disputes: 0,
        active: true,
        bump: ctx.bumps.merchant,
    });
    Ok(())
}

/// Shared accounts for authority-only registry maintenance.
#[derive(Accounts)]
pub struct MerchantAdmin<'info> {
    pub authority: Signer<'info>,
    #[account(mut, has_one = authority @ TallyError::Unauthorized)]
    pub merchant: Account<'info, Merchant>,
}

pub fn rotate_signing_key(ctx: Context<MerchantAdmin>, new_key: Pubkey) -> Result<()> {
    ctx.accounts.merchant.signing_key = new_key;
    Ok(())
}

pub fn set_active(ctx: Context<MerchantAdmin>, active: bool) -> Result<()> {
    ctx.accounts.merchant.active = active;
    Ok(())
}

pub fn set_pow_difficulty(ctx: Context<MerchantAdmin>, difficulty: u8) -> Result<()> {
    ctx.accounts.merchant.pow_difficulty = difficulty;
    Ok(())
}

#[derive(Accounts)]
pub struct SetPayout<'info> {
    pub authority: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Config>,
    #[account(mut, has_one = authority @ TallyError::Unauthorized)]
    pub merchant: Account<'info, Merchant>,
    #[account(token::mint = config.usdc_mint)]
    pub payout: InterfaceAccount<'info, TokenAccount>,
}

pub fn set_payout(ctx: Context<SetPayout>) -> Result<()> {
    ctx.accounts.merchant.payout = ctx.accounts.payout.key();
    Ok(())
}

#[derive(Accounts)]
pub struct TopUpBond<'info> {
    pub authority: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Config>,
    #[account(mut, has_one = authority @ TallyError::Unauthorized)]
    pub merchant: Account<'info, Merchant>,
    #[account(address = config.skr_mint)]
    pub skr_mint: InterfaceAccount<'info, Mint>,
    #[account(mut, token::mint = skr_mint, token::authority = authority)]
    pub authority_skr: InterfaceAccount<'info, TokenAccount>,
    #[account(mut, seeds = [BOND_SEED, merchant.key().as_ref()], bump)]
    pub bond_vault: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
}

pub fn top_up_bond(ctx: Context<TopUpBond>, amount: u64) -> Result<()> {
    token_interface::transfer_checked(
        CpiContext::new(
            ctx.accounts.token_program.key(),
            TransferChecked {
                from: ctx.accounts.authority_skr.to_account_info(),
                mint: ctx.accounts.skr_mint.to_account_info(),
                to: ctx.accounts.bond_vault.to_account_info(),
                authority: ctx.accounts.authority.to_account_info(),
            },
        ),
        amount,
        ctx.accounts.skr_mint.decimals,
    )?;
    let m = &mut ctx.accounts.merchant;
    m.skr_bonded = m
        .skr_bonded
        .checked_add(amount)
        .ok_or(TallyError::Overflow)?;
    Ok(())
}
