//! `prove_floor_fraud`: a merchant's own signature over a self-contradictory menu is slashable
//! by anyone, with no arbiter.
use ed25519_dalek::SigningKey;
use tally_litesvm::{fraud::fraud_items, *};

const BOND: u64 = 5_000_000_000;

#[test]
fn floor_fraud_proof_slashes_bond() {
    let mut e = Env::funded();
    let (rep, rep_skr) = e.reporter();
    let signer = e.merchant_signer.clone();
    e.try_prove_fraud(&fraud_items(), 0, 1, &signer, &rep, rep_skr)
        .unwrap();
    // slash_bps = 5000 → 2,500 SKR slashed: 1,250 to the reporter, 1,250 to the pool.
    assert_eq!(e.token_balance(&e.bond_vault()), BOND / 2);
    assert_eq!(e.token_balance(&rep_skr), BOND / 4);
    assert_eq!(e.token_balance(&e.pool()), BOND / 4);
    let m: tally::state::Merchant = e.fetch(&e.merchant);
    assert_eq!(m.skr_bonded, BOND / 2);
    assert_eq!(m.slashed_total, BOND / 2);
}

#[test]
fn honest_menu_is_not_a_fraud() {
    let mut e = Env::funded();
    let (rep, rep_skr) = e.reporter();
    let signer = e.merchant_signer.clone();
    let r = e.try_prove_fraud(&default_items(), 0, 1, &signer, &rep, rep_skr);
    assert_custom_err(&r, tally_err(TallyError::NotAFraud));
    assert_eq!(e.token_balance(&e.bond_vault()), BOND);
}

#[test]
fn different_categories_are_not_a_fraud() {
    let mut e = Env::funded();
    let (rep, rep_skr) = e.reporter();
    let signer = e.merchant_signer.clone();
    // index 0 drip coffee (cat 3, 3.25) vs index 5 croissant (cat 0, floor 3.00)
    let r = e.try_prove_fraud(&fraud_items(), 0, 5, &signer, &rep, rep_skr);
    assert_custom_err(&r, tally_err(TallyError::NotAFraud));
}

#[test]
fn fraud_signed_by_someone_else_is_rejected() {
    let mut e = Env::funded();
    let (rep, rep_skr) = e.reporter();
    let r = e.try_prove_fraud(
        &fraud_items(),
        0,
        1,
        &SigningKey::from_bytes(&[42; 32]),
        &rep,
        rep_skr,
    );
    assert_custom_err(&r, tally_err(TallyError::WrongSigner));
}

#[test]
fn same_evidence_cannot_slash_twice() {
    let mut e = Env::funded();
    let (rep, rep_skr) = e.reporter();
    let signer = e.merchant_signer.clone();
    e.try_prove_fraud(&fraud_items(), 0, 1, &signer, &rep, rep_skr)
        .unwrap();
    // same menu root again (even with a different leaf pair) is nullified
    assert!(e
        .try_prove_fraud(&fraud_items(), 0, 2, &signer, &rep, rep_skr)
        .is_err());
    assert_eq!(e.token_balance(&e.bond_vault()), BOND / 2);
}

#[test]
fn a_second_fraudulent_menu_slashes_again() {
    let mut e = Env::funded();
    let (rep, rep_skr) = e.reporter();
    let signer = e.merchant_signer.clone();
    e.try_prove_fraud(&fraud_items(), 0, 1, &signer, &rep, rep_skr)
        .unwrap();
    let mut other = fraud_items();
    other[0].price = 3_000_000;
    other[0].floor = 3_000_000;
    e.try_prove_fraud(&other, 0, 1, &signer, &rep, rep_skr)
        .unwrap();
    assert_eq!(e.token_balance(&e.bond_vault()), BOND / 4);
}
