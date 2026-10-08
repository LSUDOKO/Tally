use tally_litesvm::*;

#[test]
fn register_moves_bond_into_vault() {
    let env = Env::new().with_merchant(5_000_000_000);
    let m: tally::state::Merchant = env.fetch(&env.merchant);
    assert_eq!(m.skr_bonded, 5_000_000_000);
    assert!(m.active);
    assert_eq!(env.token_balance(&env.bond_vault()), 5_000_000_000);
    assert_eq!(m.payout, env.payout);
}

#[test]
fn register_rejects_fake_skr_mint() {
    let mut env = Env::new();
    let fake = env.create_mint(6);
    let r = env.try_register_merchant(fake, 1);
    assert_custom_err(&r, anchor_err(anchor_lang::error::ErrorCode::ConstraintAddress));
}

#[test]
fn only_authority_rotates_key() {
    let mut env = Env::new().with_merchant(1);
    let intruder = solana_keypair::Keypair::new();
    assert!(env.try_rotate_signing_key_as(&intruder, solana_pubkey::Pubkey::new_unique()).is_err());
}

#[test]
fn authority_rotates_key() {
    let mut env = Env::new().with_merchant(1);
    let next = ed25519_dalek::SigningKey::from_bytes(&[55u8; 32]);
    env.rotate_signing_key(next.clone());
    let m: tally::state::Merchant = env.fetch(&env.merchant);
    assert_eq!(m.signing_key.to_bytes(), next.verifying_key().to_bytes());
}
