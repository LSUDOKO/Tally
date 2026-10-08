use anchor_lang::InstructionData;
use solana_keypair::Keypair;
use solana_signer::Signer;
use tally_litesvm::*;

#[test]
fn create_and_fund() {
    let env = Env::new()
        .with_merchant(1)
        .with_capsule(coffee_rules(), 10_000_000);
    assert_eq!(env.token_balance(&env.vault), 10_000_000);
    let c = env.capsule_state();
    assert_eq!(c.owner, env.owner.pubkey());
    assert_eq!(c.agent, env.agent.pubkey());
    assert_eq!(c.per_tx_cap, 6_000_000);
    assert_eq!(c.cycle_start_slot, env.slot());
}

#[test]
fn rules_reject_per_tx_above_daily() {
    let mut env = Env::new();
    let mut r = coffee_rules();
    r.per_tx_cap = r.daily_cap + 1;
    assert_custom_err(
        &env.try_create_capsule(r),
        tally_err(TallyError::InvalidRules),
    );
}

#[test]
fn rules_reject_past_expiry() {
    let mut env = Env::new();
    let mut r = coffee_rules();
    r.expiry_slot = env.slot();
    assert_custom_err(
        &env.try_create_capsule(r),
        tally_err(TallyError::InvalidRules),
    );
}

#[test]
fn rules_cannot_self_set_sgt_flag() {
    let mut env = Env::new();
    let mut r = coffee_rules();
    r.flags |= FLAG_SGT_VERIFIED;
    assert_custom_err(
        &env.try_create_capsule(r),
        tally_err(TallyError::InvalidRules),
    );
}

#[test]
fn withdraw_only_owner() {
    let mut env = Env::new().with_capsule(coffee_rules(), 5);
    let agent = env.agent.insecure_clone();
    assert!(env.try_withdraw_as(&agent, 5).is_err());
    let owner = env.owner.insecure_clone();
    env.try_withdraw_as(&owner, 5).unwrap();
    assert_eq!(env.token_balance(&env.vault), 0);
}

#[test]
fn freeze_and_unfreeze() {
    let mut env = Env::new().with_capsule(coffee_rules(), 0);
    env.freeze();
    assert!(env.capsule_state().frozen);
    env.try_owner_op(tally::instruction::Unfreeze {}.data())
        .unwrap();
    assert!(!env.capsule_state().frozen);
}

#[test]
fn set_agent_rotates() {
    let mut env = Env::new().with_capsule(coffee_rules(), 0);
    let next = Keypair::new().pubkey();
    env.try_owner_op(tally::instruction::SetAgent { new_agent: next }.data())
        .unwrap();
    assert_eq!(env.capsule_state().agent, next);
}

#[test]
fn update_rules_keeps_sgt_flag_out_of_reach() {
    let mut env = Env::new().with_capsule(coffee_rules(), 0);
    let mut r = coffee_rules();
    r.daily_cap = 20_000_000;
    env.update_rules(r.clone()).unwrap();
    assert_eq!(env.capsule_state().daily_cap, 20_000_000);
    r.flags = FLAG_SGT_VERIFIED;
    assert_custom_err(&env.update_rules(r), tally_err(TallyError::InvalidRules));
}

#[test]
fn close_returns_funds() {
    let mut env = Env::new().with_capsule(coffee_rules(), 7_000_000);
    let before = env.token_balance(&env.owner_usdc());
    env.close_capsule().unwrap();
    assert_eq!(env.token_balance(&env.owner_usdc()), before + 7_000_000);
    assert!(!env.exists(&env.capsule));
}

#[test]
fn agent_cannot_be_owner_at_creation() {
    let mut env = Env::new();
    env.agent = env.owner.insecure_clone();
    assert_custom_err(
        &env.try_create_capsule(coffee_rules()),
        tally_err(TallyError::InvalidRules),
    );
}

#[test]
fn agent_cannot_be_rotated_to_owner() {
    let mut env = Env::new().with_capsule(coffee_rules(), 0);
    let owner = env.owner.pubkey();
    assert_custom_err(
        &env.try_owner_op(tally::instruction::SetAgent { new_agent: owner }.data()),
        tally_err(TallyError::InvalidRules),
    );
}
