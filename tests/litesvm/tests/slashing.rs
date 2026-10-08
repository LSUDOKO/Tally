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
    // slash_bps = 5000 → 2,500 SKR slashed: a 10% bounty (250) to the reporter, 2,250 to the
    // compensation pool. A small bounty makes self-reporting through a sock puppet a 90% loss.
    assert_eq!(e.token_balance(&e.bond_vault()), BOND / 2);
    assert_eq!(e.token_balance(&rep_skr), BOND / 20);
    assert_eq!(e.token_balance(&e.pool()), BOND / 2 - BOND / 20);
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
    // Valid-looking evidence, but signed by a key that is not the merchant's: pass the merchant's
    // real key record so the program reaches (and fails) the signature check.
    let current = e.key_record(&solana_pubkey::Pubkey::new_from_array(
        e.merchant_signer.verifying_key().to_bytes(),
    ));
    let r = e.try_prove_fraud_with_record(
        &fraud_items(),
        0,
        1,
        &SigningKey::from_bytes(&[42; 32]),
        &rep,
        rep_skr,
        current,
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

#[test]
fn merchant_cannot_report_itself() {
    let mut e = Env::funded();
    let me = e.merchant_auth.insecure_clone();
    let my_skr = e.merchant_skr();
    let signer = e.merchant_signer.clone();
    assert_custom_err(
        &e.try_prove_fraud(&fraud_items(), 0, 1, &signer, &me, my_skr),
        tally_err(TallyError::SelfReport),
    );
}

#[test]
fn arbiter_compensates_users_from_pool() {
    let mut e = Env::funded();
    let (rep, rep_skr) = e.reporter();
    let signer = e.merchant_signer.clone();
    e.try_prove_fraud(&fraud_items(), 0, 1, &signer, &rep, rep_skr)
        .unwrap();
    let to = e.owner_skr();
    let arbiter = e.admin.insecure_clone();
    e.try_compensate(&arbiter, to, 1_000_000_000).unwrap();
    assert_eq!(e.token_balance(&to), 1_000_000_000);
}

#[test]
fn only_arbiter_spends_pool() {
    let mut e = Env::funded();
    let (rep, rep_skr) = e.reporter();
    let signer = e.merchant_signer.clone();
    e.try_prove_fraud(&fraud_items(), 0, 1, &signer, &rep, rep_skr)
        .unwrap();
    assert!(e.try_compensate(&rep, rep_skr, 1).is_err());
}

#[test]
fn rotation_does_not_escape_fraud_proof() {
    let mut e = Env::funded();
    let (rep, rep_skr) = e.reporter();
    let old = e.merchant_signer.clone();
    e.rotate_signing_key(SigningKey::from_bytes(&[56; 32]));
    e.try_prove_fraud(&fraud_items(), 0, 1, &old, &rep, rep_skr)
        .unwrap();
    assert_eq!(e.token_balance(&e.bond_vault()), BOND / 2);
}

#[test]
fn retired_key_evidence_expires_after_unlock_delay() {
    let mut e = Env::funded();
    let (rep, rep_skr) = e.reporter();
    let old = e.merchant_signer.clone();
    e.rotate_signing_key(SigningKey::from_bytes(&[56; 32]));
    let s = e.slot();
    e.warp(s + 1_001); // config bond_unlock_delay = 1,000
    assert_custom_err(
        &e.try_prove_fraud(&fraud_items(), 0, 1, &old, &rep, rep_skr),
        tally_err(TallyError::EvidenceExpired),
    );
}

#[test]
fn key_records_track_rotation() {
    let mut e = Env::funded();
    let old = solana_pubkey::Pubkey::new_from_array(e.merchant_signer.verifying_key().to_bytes());
    e.rotate_signing_key(SigningKey::from_bytes(&[56; 32]));
    let r: tally::state::KeyRecord = e.fetch(&e.key_record(&old));
    assert_eq!(r.valid_until, e.slot());
}
