//! Receipt disputes for what the chain cannot prove (wrong goods, no delivery). The capsule owner
//! opens a dispute against a `Receipt`; the configured arbiter upholds (slashing part of the
//! merchant's SKR bond to the claimant) or rejects it. Open disputes block bond withdrawal.
use anchor_lang::prelude::*;
use anchor_spl::token_interface::{self, Mint, TokenAccount, TokenInterface, TransferChecked};

use crate::{
    constants::*,
    errors::TallyError,
    events::{DisputeOpened, DisputeResolved, Slashed},
    state::{Capsule, Config, Dispute, DisputeStatus, Merchant, Receipt},
};

#[derive(Accounts)]
pub struct OpenDispute<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,
    #[account(has_one = owner @ TallyError::Unauthorized)]
    pub capsule: Box<Account<'info, Capsule>>,
    #[account(
        mut,
        constraint = receipt.capsule == capsule.key() @ TallyError::Unauthorized,
        constraint = !receipt.disputed @ TallyError::WrongDisputeState,
    )]
    pub receipt: Box<Account<'info, Receipt>>,
    #[account(mut, address = receipt.merchant)]
    pub merchant: Box<Account<'info, Merchant>>,
    #[account(
        init, payer = owner, space = 8 + Dispute::INIT_SPACE,
        seeds = [DISPUTE_SEED, receipt.key().as_ref()], bump,
    )]
    pub dispute: Box<Account<'info, Dispute>>,
    pub system_program: Program<'info, System>,
}

pub fn open_dispute(ctx: Context<OpenDispute>, evidence_hash: [u8; 32]) -> Result<()> {
    ctx.accounts.receipt.disputed = true;
    let m = &mut ctx.accounts.merchant;
    m.open_disputes = m.open_disputes.checked_add(1).ok_or(TallyError::Overflow)?;
    let (receipt, claimant) = (ctx.accounts.receipt.key(), ctx.accounts.owner.key());
    ctx.accounts.dispute.set_inner(Dispute {
        receipt,
        claimant,
        merchant: m.key(),
        evidence_hash,
        status: DisputeStatus::Open,
        slash_amount: 0,
        opened_slot: Clock::get()?.slot,
        bump: ctx.bumps.dispute,
    });
    emit!(DisputeOpened {
        dispute: ctx.accounts.dispute.key(),
        receipt,
        claimant
    });
    Ok(())
}

#[derive(Accounts)]
pub struct ResolveDispute<'info> {
    pub arbiter: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump, has_one = arbiter @ TallyError::Unauthorized)]
    pub config: Box<Account<'info, Config>>,
    #[account(mut, has_one = merchant)]
    pub dispute: Box<Account<'info, Dispute>>,
    #[account(mut)]
    pub merchant: Box<Account<'info, Merchant>>,
    #[account(mut, seeds = [BOND_SEED, merchant.key().as_ref()], bump)]
    pub bond_vault: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(address = config.skr_mint)]
    pub skr_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(mut, token::mint = skr_mint, token::authority = dispute.claimant)]
    pub claimant_skr: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
}

pub fn resolve_dispute(
    ctx: Context<ResolveDispute>,
    upheld: bool,
    slash_amount: u64,
) -> Result<()> {
    require!(
        ctx.accounts.dispute.status == DisputeStatus::Open,
        TallyError::WrongDisputeState
    );
    let mut amount = 0;
    if upheld {
        amount = slash_amount.min(ctx.accounts.bond_vault.amount);
        if amount > 0 {
            let m = &ctx.accounts.merchant;
            let (authority, bump) = (m.authority, [m.bump]);
            let seeds: &[&[u8]] = &[MERCHANT_SEED, authority.as_ref(), &bump];
            token_interface::transfer_checked(
                CpiContext::new_with_signer(
                    ctx.accounts.token_program.key(),
                    TransferChecked {
                        from: ctx.accounts.bond_vault.to_account_info(),
                        mint: ctx.accounts.skr_mint.to_account_info(),
                        to: ctx.accounts.claimant_skr.to_account_info(),
                        authority: ctx.accounts.merchant.to_account_info(),
                    },
                    &[seeds],
                ),
                amount,
                ctx.accounts.skr_mint.decimals,
            )?;
        }
    }
    let m = &mut ctx.accounts.merchant;
    m.open_disputes = m.open_disputes.saturating_sub(1);
    m.skr_bonded = m.skr_bonded.saturating_sub(amount);
    m.slashed_total = m
        .slashed_total
        .checked_add(amount)
        .ok_or(TallyError::Overflow)?;
    let d = &mut ctx.accounts.dispute;
    d.status = if upheld {
        DisputeStatus::Upheld
    } else {
        DisputeStatus::Rejected
    };
    d.slash_amount = amount;
    if amount > 0 {
        emit!(Slashed {
            merchant: m.key(),
            amount,
            reason: 1
        });
    }
    emit!(DisputeResolved {
        dispute: d.key(),
        upheld,
        slash_amount: amount
    });
    Ok(())
}

#[derive(Accounts)]
pub struct BondAdmin<'info> {
    pub authority: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Config>,
    #[account(mut, has_one = authority @ TallyError::Unauthorized)]
    pub merchant: Account<'info, Merchant>,
}

pub fn request_bond_unlock(ctx: Context<BondAdmin>) -> Result<()> {
    let now = Clock::get()?.slot;
    let m = &mut ctx.accounts.merchant;
    m.bond_unlock_slot = now
        .checked_add(ctx.accounts.config.bond_unlock_delay)
        .ok_or(TallyError::Overflow)?;
    // A merchant that is leaving cannot keep trading on a bond it is about to withdraw.
    m.active = false;
    Ok(())
}

#[derive(Accounts)]
pub struct WithdrawBond<'info> {
    pub authority: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    #[account(mut, has_one = authority @ TallyError::Unauthorized)]
    pub merchant: Box<Account<'info, Merchant>>,
    #[account(address = config.skr_mint)]
    pub skr_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(mut, seeds = [BOND_SEED, merchant.key().as_ref()], bump)]
    pub bond_vault: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(mut, token::mint = skr_mint, token::authority = authority)]
    pub authority_skr: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
}

pub fn withdraw_bond(ctx: Context<WithdrawBond>, amount: u64) -> Result<()> {
    let now = Clock::get()?.slot;
    let m = &ctx.accounts.merchant;
    require!(
        m.bond_unlock_slot != 0 && now >= m.bond_unlock_slot,
        TallyError::BondLocked
    );
    require!(m.open_disputes == 0, TallyError::DisputeOpen);
    let (authority, bump) = (m.authority, [m.bump]);
    let seeds: &[&[u8]] = &[MERCHANT_SEED, authority.as_ref(), &bump];
    token_interface::transfer_checked(
        CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            TransferChecked {
                from: ctx.accounts.bond_vault.to_account_info(),
                mint: ctx.accounts.skr_mint.to_account_info(),
                to: ctx.accounts.authority_skr.to_account_info(),
                authority: ctx.accounts.merchant.to_account_info(),
            },
            &[seeds],
        ),
        amount,
        ctx.accounts.skr_mint.decimals,
    )?;
    let m = &mut ctx.accounts.merchant;
    m.skr_bonded = m.skr_bonded.saturating_sub(amount);
    // Each withdrawal needs a fresh unlock request (and a fresh delay).
    m.bond_unlock_slot = 0;
    Ok(())
}
