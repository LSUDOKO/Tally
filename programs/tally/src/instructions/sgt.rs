//! Seeker-only features: a capsule created with a verified Seeker Genesis Token, and promo
//! claims limited to one per physical device (one SGT) per promo id.
use anchor_lang::prelude::*;
use anchor_spl::{
    associated_token::AssociatedToken,
    token_interface::{Mint, TokenAccount, TokenInterface},
};

use crate::{
    checks::sgt::verify_sgt,
    constants::*,
    errors::TallyError,
    instructions::capsule::{new_capsule, Rules},
    state::{Capsule, Config, SgtClaim},
};

#[derive(Accounts)]
#[instruction(capsule_id: u64)]
pub struct CreateCapsuleWithSgt<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    #[account(
        init, payer = owner, space = 8 + Capsule::INIT_SPACE,
        seeds = [CAPSULE_SEED, owner.key().as_ref(), &capsule_id.to_le_bytes()], bump,
    )]
    pub capsule: Box<Account<'info, Capsule>>,
    #[account(address = config.usdc_mint)]
    pub mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(
        init, payer = owner,
        associated_token::mint = mint, associated_token::authority = capsule,
        associated_token::token_program = token_program,
    )]
    pub vault: Box<InterfaceAccount<'info, TokenAccount>>,
    /// CHECK: parsed and verified by `verify_sgt` (Token-2022 owner, holder, amount).
    pub sgt_token_account: UncheckedAccount<'info>,
    /// CHECK: parsed and verified by `verify_sgt` (TokenGroupMember of the configured group).
    pub sgt_mint: UncheckedAccount<'info>,
    pub token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

pub fn create_capsule_with_sgt(ctx: Context<CreateCapsuleWithSgt>, capsule_id: u64, rules: Rules, agent: Pubkey) -> Result<()> {
    let owner = ctx.accounts.owner.key();
    let sgt_mint = verify_sgt(
        &ctx.accounts.sgt_token_account.to_account_info(),
        &ctx.accounts.sgt_mint.to_account_info(),
        &owner,
        &ctx.accounts.config.sgt_group,
    )?;
    let mut c = new_capsule(owner, agent, ctx.accounts.mint.key(), ctx.accounts.vault.key(), capsule_id, ctx.bumps.capsule, &rules)?;
    c.flags |= FLAG_SGT_VERIFIED;
    c.sgt_mint = sgt_mint;
    ctx.accounts.capsule.set_inner(c);
    Ok(())
}

#[derive(Accounts)]
#[instruction(promo_id: u64)]
pub struct ClaimPromo<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,
    #[account(has_one = owner @ TallyError::Unauthorized)]
    pub capsule: Account<'info, Capsule>,
    #[account(
        init, payer = owner, space = 8 + SgtClaim::INIT_SPACE,
        seeds = [SGT_SEED, capsule.sgt_mint.as_ref(), &promo_id.to_le_bytes()], bump,
    )]
    pub sgt_claim: Account<'info, SgtClaim>,
    pub system_program: Program<'info, System>,
}

pub fn claim_promo(ctx: Context<ClaimPromo>, promo_id: u64) -> Result<()> {
    let c = &ctx.accounts.capsule;
    require!(c.flags & FLAG_SGT_VERIFIED != 0, TallyError::InvalidSgt);
    ctx.accounts.sgt_claim.set_inner(SgtClaim { sgt_mint: c.sgt_mint, promo_id, owner: c.owner });
    Ok(())
}
