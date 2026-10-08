//! Tally: binds an autonomous agent's purchase to a merchant-signed catalog leaf and the owner's
//! on-chain rules, inside one atomic transaction. See `docs/00-whitepaper.md`.
use anchor_lang::prelude::*;

pub mod checks;
pub mod constants;
pub mod errors;
pub mod events;
pub mod instructions;
pub mod state;

use instructions::*;

declare_id!("3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk");

#[program]
pub mod tally {
    use super::*;

    pub fn initialize_config(
        ctx: Context<InitializeConfig>,
        arbiter: Pubkey,
        sgt_group: Pubkey,
        slash_bps: u16,
        bond_unlock_delay: u64,
    ) -> Result<()> {
        instructions::config::initialize_config(ctx, arbiter, sgt_group, slash_bps, bond_unlock_delay)
    }

    pub fn register_merchant(ctx: Context<RegisterMerchant>, name: [u8; 32], signing_key: Pubkey, bond: u64) -> Result<()> {
        instructions::merchant::register_merchant(ctx, name, signing_key, bond)
    }

    pub fn rotate_signing_key(ctx: Context<MerchantAdmin>, new_key: Pubkey) -> Result<()> {
        instructions::merchant::rotate_signing_key(ctx, new_key)
    }

    pub fn set_active(ctx: Context<MerchantAdmin>, active: bool) -> Result<()> {
        instructions::merchant::set_active(ctx, active)
    }

    pub fn set_pow_difficulty(ctx: Context<MerchantAdmin>, difficulty: u8) -> Result<()> {
        instructions::merchant::set_pow_difficulty(ctx, difficulty)
    }

    pub fn set_payout(ctx: Context<SetPayout>) -> Result<()> {
        instructions::merchant::set_payout(ctx)
    }

    pub fn top_up_bond(ctx: Context<TopUpBond>, amount: u64) -> Result<()> {
        instructions::merchant::top_up_bond(ctx, amount)
    }

    pub fn create_capsule(ctx: Context<CreateCapsule>, capsule_id: u64, rules: Rules, agent: Pubkey) -> Result<()> {
        instructions::capsule::create_capsule(ctx, capsule_id, rules, agent)
    }

    pub fn update_rules(ctx: Context<CapsuleOwner>, rules: Rules) -> Result<()> {
        instructions::capsule::update_rules(ctx, rules)
    }

    pub fn fund(ctx: Context<MoveFunds>, amount: u64) -> Result<()> {
        instructions::capsule::fund(ctx, amount)
    }

    pub fn withdraw(ctx: Context<MoveFunds>, amount: u64) -> Result<()> {
        instructions::capsule::withdraw(ctx, amount)
    }

    pub fn freeze(ctx: Context<CapsuleOwner>) -> Result<()> {
        instructions::capsule::set_frozen(ctx, true)
    }

    pub fn unfreeze(ctx: Context<CapsuleOwner>) -> Result<()> {
        instructions::capsule::set_frozen(ctx, false)
    }

    pub fn set_agent(ctx: Context<CapsuleOwner>, new_agent: Pubkey) -> Result<()> {
        instructions::capsule::set_agent(ctx, new_agent)
    }

    pub fn close_capsule(ctx: Context<CloseCapsule>) -> Result<()> {
        instructions::capsule::close_capsule(ctx)
    }

    pub fn pay_offer(ctx: Context<PayOffer>, args: PayOfferArgs) -> Result<()> {
        instructions::pay_offer::pay_offer(ctx, args)
    }

    pub fn prove_floor_fraud(
        ctx: Context<ProveFloorFraud>,
        header: MenuHeaderArgs,
        leaf_a: LeafArgs,
        proof_a: Vec<[u8; 32]>,
        leaf_b: LeafArgs,
        proof_b: Vec<[u8; 32]>,
    ) -> Result<()> {
        instructions::slashing::prove_floor_fraud(ctx, header, leaf_a, proof_a, leaf_b, proof_b)
    }

    pub fn open_dispute(ctx: Context<OpenDispute>, evidence_hash: [u8; 32]) -> Result<()> {
        instructions::dispute::open_dispute(ctx, evidence_hash)
    }

    pub fn resolve_dispute(ctx: Context<ResolveDispute>, upheld: bool, slash_amount: u64) -> Result<()> {
        instructions::dispute::resolve_dispute(ctx, upheld, slash_amount)
    }

    pub fn request_bond_unlock(ctx: Context<BondAdmin>) -> Result<()> {
        instructions::dispute::request_bond_unlock(ctx)
    }

    pub fn withdraw_bond(ctx: Context<WithdrawBond>, amount: u64) -> Result<()> {
        instructions::dispute::withdraw_bond(ctx, amount)
    }

    pub fn create_capsule_with_sgt(ctx: Context<CreateCapsuleWithSgt>, capsule_id: u64, rules: Rules, agent: Pubkey) -> Result<()> {
        instructions::sgt::create_capsule_with_sgt(ctx, capsule_id, rules, agent)
    }

    pub fn claim_promo(ctx: Context<ClaimPromo>, promo_id: u64) -> Result<()> {
        instructions::sgt::claim_promo(ctx, promo_id)
    }
}
