//! One-time protocol configuration: pins the USDC and SKR mints, the dispute arbiter, the SGT
//! collection group and slashing parameters, and creates the SKR compensation pool.
use anchor_lang::prelude::*;
use anchor_spl::token_interface::{Mint, TokenAccount, TokenInterface};

use crate::{constants::*, errors::TallyError, state::Config};

#[derive(Accounts)]
pub struct InitializeConfig<'info> {
    #[account(mut)]
    pub admin: Signer<'info>,
    #[account(init, payer = admin, space = 8 + Config::INIT_SPACE, seeds = [CONFIG_SEED], bump)]
    pub config: Account<'info, Config>,
    pub usdc_mint: InterfaceAccount<'info, Mint>,
    pub skr_mint: InterfaceAccount<'info, Mint>,
    #[account(
        init, payer = admin, seeds = [POOL_SEED], bump,
        token::mint = skr_mint, token::authority = config, token::token_program = token_program,
    )]
    pub pool: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
    pub system_program: Program<'info, System>,
}

pub fn initialize_config(
    ctx: Context<InitializeConfig>,
    arbiter: Pubkey,
    sgt_group: Pubkey,
    slash_bps: u16,
    bond_unlock_delay: u64,
) -> Result<()> {
    require!(slash_bps <= 10_000, TallyError::InvalidRules);
    ctx.accounts.config.set_inner(Config {
        admin: ctx.accounts.admin.key(),
        arbiter,
        usdc_mint: ctx.accounts.usdc_mint.key(),
        skr_mint: ctx.accounts.skr_mint.key(),
        sgt_group,
        slash_bps,
        bond_unlock_delay,
        bump: ctx.bumps.config,
    });
    Ok(())
}
