//! Invariants 5 (registry payout), 7 (capsule policy) and 8 (Cheapest-Match / bond tier).
use solana_keypair::Keypair;
use solana_signer::Signer;
use tally_litesvm::*;

#[test]
fn payee_swap() {
    let mut e = Env::funded();
    let evil = e.create_usdc_account(&Keypair::new().pubkey());
    let r = e.pay().payout(evil).send();
    assert_custom_err(&r, anchor_err(anchor_lang::error::ErrorCode::ConstraintHasOne));
}

#[test]
fn wrong_mint_account() {
    let mut e = Env::funded();
    let other = e.create_mint(6);
    let usdc = e.usdc;
    let r = e
        .pay()
        .ixs(move |v| {
            for m in v[1].accounts.iter_mut() {
                if m.pubkey == usdc {
                    m.pubkey = other;
                }
            }
        })
        .send();
    assert_custom_err(&r, anchor_err(anchor_lang::error::ErrorCode::ConstraintHasOne));
}

#[test]
fn category_blocked() {
    let mut e = Env::funded();
    assert_custom_err(&e.pay().item(4).send(), tally_err(TallyError::Category)); // croissant, food
}

#[test]
fn category_255_allowed_when_bit_set() {
    let mut e = Env::funded().with_rules(|r| r.allowed_categories[3] = 1 << 63);
    e.pay().items(items_in_category(255)).item(6).send().unwrap(); // item 6 ($2.75) is the floor
}

#[test]
fn category_256_rejected() {
    let mut e = Env::funded().with_rules(|r| r.allowed_categories = [u64::MAX; 4]);
    assert_custom_err(&e.pay().items(items_in_category(256)).send(), tally_err(TallyError::Category));
}

#[test]
fn over_per_tx() {
    let mut e = Env::funded();
    assert_custom_err(&e.pay().item(3).owner_signs().send(), tally_err(TallyError::OverPerTx)); // reserve roast $18
}

#[test]
fn boundaries_price_equals_per_tx_cap() {
    let mut e = Env::funded().with_rules(|r| r.per_tx_cap = 4_500_000);
    e.pay().send().unwrap();
}

#[test]
fn boundaries_price_equals_floor_is_autonomous() {
    let mut e = Env::funded();
    e.pay().item(0).send().unwrap();
}

#[test]
fn cosign_required_above_floor() {
    let mut e = Env::funded();
    assert_custom_err(&e.pay().item(1).send(), tally_err(TallyError::OwnerCosignRequired));
}

#[test]
fn cosign_satisfies_above_floor() {
    let mut e = Env::funded();
    e.pay().item(1).owner_signs().send().unwrap();
}

#[test]
fn low_bond_requires_cosign() {
    let mut e = Env::new().with_merchant(10).with_capsule(coffee_rules(), 20_000_000);
    assert_custom_err(&e.pay().send(), tally_err(TallyError::OwnerCosignRequired));
    e.pay().nonce(99).owner_signs().send().unwrap();
}

#[test]
fn impostor_owner_signature_does_not_count() {
    let mut e = Env::funded();
    let impostor = Keypair::new();
    let (real_owner, imp) = (e.owner.pubkey(), impostor.pubkey());
    let r = e
        .pay()
        .item(1)
        .extra_signer(impostor)
        .ixs(move |v| {
            for m in v[1].accounts.iter_mut() {
                if m.pubkey == real_owner {
                    *m = solana_instruction::AccountMeta::new_readonly(imp, true);
                }
            }
        })
        .send();
    assert_custom_err(&r, anchor_err(anchor_lang::error::ErrorCode::ConstraintHasOne));
}
