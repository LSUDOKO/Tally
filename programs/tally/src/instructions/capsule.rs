//! Capsule lifecycle: the owner (Seed Vault via MWA) creates, funds, tunes, freezes and closes
//! the on-chain intent the agent key is allowed to spend under.
use anchor_lang::prelude::*;
use anchor_spl::{
    associated_token::AssociatedToken,
    token_interface::{self, CloseAccount, Mint, TokenAccount, TokenInterface, TransferChecked},
};

use crate::{
    constants::*,
    errors::TallyError,
    state::{Capsule, Config},
};

/// Owner-chosen spending rules. `flags` may not include `FLAG_SGT_VERIFIED`; only the
/// SGT-checked creation path sets it.
#[derive(AnchorSerialize, AnchorDeserialize, Clone, Debug, PartialEq, Eq)]
pub struct Rules {
    pub per_tx_cap: u64,
    pub daily_cap: u64,
    pub allowed_categories: [u64; 4],
    pub min_merchant_bond: u64,
    pub expiry_slot: u64,
    pub flags: u8,
}

impl Rules {
    pub fn validate(&self, now: u64) -> Result<()> {
        require!(
            self.per_tx_cap > 0
                && self.per_tx_cap <= self.daily_cap
                && self.expiry_slot > now
                && self.flags & !KNOWN_FLAGS == 0
                && self.flags & FLAG_SGT_VERIFIED == 0,
            TallyError::InvalidRules
        );
        Ok(())
    }

    pub fn apply(&self, c: &mut Capsule) {
        c.per_tx_cap = self.per_tx_cap;
        c.daily_cap = self.daily_cap;
        c.allowed_categories = self.allowed_categories;
        c.min_merchant_bond = self.min_merchant_bond;
        c.expiry_slot = self.expiry_slot;
        c.flags = (c.flags & FLAG_SGT_VERIFIED) | self.flags;
    }
}

#[derive(Accounts)]
#[instruction(capsule_id: u64)]
pub struct CreateCapsule<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Config>,
    #[account(
        init, payer = owner, space = 8 + Capsule::INIT_SPACE,
        seeds = [CAPSULE_SEED, owner.key().as_ref(), &capsule_id.to_le_bytes()], bump,
    )]
    pub capsule: Account<'info, Capsule>,
    #[account(address = config.usdc_mint)]
    pub mint: InterfaceAccount<'info, Mint>,
    #[account(
        init, payer = owner,
        associated_token::mint = mint, associated_token::authority = capsule,
        associated_token::token_program = token_program,
    )]
    pub vault: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

pub fn create_capsule(ctx: Context<CreateCapsule>, capsule_id: u64, rules: Rules, agent: Pubkey) -> Result<()> {
    let c = new_capsule(ctx.accounts.owner.key(), agent, ctx.accounts.mint.key(), ctx.accounts.vault.key(), capsule_id, ctx.bumps.capsule, &rules)?;
    ctx.accounts.capsule.set_inner(c);
    Ok(())
}

/// Build a validated capsule; shared by the plain and SGT-verified creation paths.
pub fn new_capsule(owner: Pubkey, agent: Pubkey, mint: Pubkey, vault: Pubkey, capsule_id: u64, bump: u8, rules: &Rules) -> Result<Capsule> {
    let now = Clock::get()?.slot;
    rules.validate(now)?;
    let mut c = Capsule {
        owner,
        agent,
        mint,
        vault,
        per_tx_cap: 0,
        daily_cap: 0,
        spent_in_cycle: 0,
        cycle_start_slot: now,
        allowed_categories: [0; 4],
        min_merchant_bond: 0,
        expiry_slot: 0,
        flags: 0,
        sgt_mint: Pubkey::default(),
        frozen: false,
        capsule_id,
        bump,
    };
    rules.apply(&mut c);
    Ok(c)
}

/// Shared accounts for owner-only state changes.
#[derive(Accounts)]
pub struct CapsuleOwner<'info> {
    pub owner: Signer<'info>,
    #[account(mut, has_one = owner @ TallyError::Unauthorized)]
    pub capsule: Account<'info, Capsule>,
}

pub fn update_rules(ctx: Context<CapsuleOwner>, rules: Rules) -> Result<()> {
    rules.validate(Clock::get()?.slot)?;
    rules.apply(&mut ctx.accounts.capsule);
    Ok(())
}

pub fn set_frozen(ctx: Context<CapsuleOwner>, frozen: bool) -> Result<()> {
    ctx.accounts.capsule.frozen = frozen;
    Ok(())
}

pub fn set_agent(ctx: Context<CapsuleOwner>, new_agent: Pubkey) -> Result<()> {
    ctx.accounts.capsule.agent = new_agent;
    Ok(())
}

#[derive(Accounts)]
pub struct MoveFunds<'info> {
    pub owner: Signer<'info>,
    #[account(has_one = owner @ TallyError::Unauthorized, has_one = vault, has_one = mint)]
    pub capsule: Account<'info, Capsule>,
    pub mint: InterfaceAccount<'info, Mint>,
    #[account(mut)]
    pub vault: InterfaceAccount<'info, TokenAccount>,
    #[account(mut, token::mint = mint, token::authority = owner)]
    pub owner_token: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
}

pub fn fund(ctx: Context<MoveFunds>, amount: u64) -> Result<()> {
    let a = &ctx.accounts;
    token_interface::transfer_checked(
        CpiContext::new(
            a.token_program.key(),
            TransferChecked {
                from: a.owner_token.to_account_info(),
                mint: a.mint.to_account_info(),
                to: a.vault.to_account_info(),
                authority: a.owner.to_account_info(),
            },
        ),
        amount,
        a.mint.decimals,
    )
}

pub fn withdraw(ctx: Context<MoveFunds>, amount: u64) -> Result<()> {
    let a = &ctx.accounts;
    let c = &a.capsule;
    let id = c.capsule_id.to_le_bytes();
    let seeds: &[&[u8]] = &[CAPSULE_SEED, c.owner.as_ref(), &id, &[c.bump]];
    token_interface::transfer_checked(
        CpiContext::new_with_signer(
            a.token_program.key(),
            TransferChecked {
                from: a.vault.to_account_info(),
                mint: a.mint.to_account_info(),
                to: a.owner_token.to_account_info(),
                authority: c.to_account_info(),
            },
            &[seeds],
        ),
        amount,
        a.mint.decimals,
    )
}

#[derive(Accounts)]
pub struct CloseCapsule<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,
    #[account(mut, close = owner, has_one = owner @ TallyError::Unauthorized, has_one = vault, has_one = mint)]
    pub capsule: Account<'info, Capsule>,
    pub mint: InterfaceAccount<'info, Mint>,
    #[account(mut)]
    pub vault: InterfaceAccount<'info, TokenAccount>,
    #[account(mut, token::mint = mint, token::authority = owner)]
    pub owner_token: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
}

pub fn close_capsule(ctx: Context<CloseCapsule>) -> Result<()> {
    let a = &ctx.accounts;
    let c = &a.capsule;
    let id = c.capsule_id.to_le_bytes();
    let seeds: &[&[u8]] = &[CAPSULE_SEED, c.owner.as_ref(), &id, &[c.bump]];
    if a.vault.amount > 0 {
        token_interface::transfer_checked(
            CpiContext::new_with_signer(
                a.token_program.key(),
                TransferChecked {
                    from: a.vault.to_account_info(),
                    mint: a.mint.to_account_info(),
                    to: a.owner_token.to_account_info(),
                    authority: c.to_account_info(),
                },
                &[seeds],
            ),
            a.vault.amount,
            a.mint.decimals,
        )?;
    }
    token_interface::close_account(CpiContext::new_with_signer(
        a.token_program.key(),
        CloseAccount {
            account: a.vault.to_account_info(),
            destination: a.owner.to_account_info(),
            authority: c.to_account_info(),
        },
        &[seeds],
    ))
}
