//! Gate G1: merchant-enabled hashcash proof-of-work, bound to menu rotation, capsule and a
//! recent slot hash so work cannot be reused.
use tally::instructions::pay_offer::PowSolution;
use tally_litesvm::*;

const DIFF: u8 = 12;

#[test]
fn pow_not_required_by_default() {
    let mut e = Env::funded();
    e.pay().send().unwrap();
}

#[test]
fn pow_required() {
    let mut e = Env::funded();
    e.set_pow_difficulty(DIFF);
    assert_custom_err(&e.pay().send(), tally_err(TallyError::PowRequired));
}

#[test]
fn pow_invalid_digest() {
    let mut e = Env::funded();
    e.set_pow_difficulty(DIFF);
    let r = e.pay().pow(PowSolution { nonce: [0xff; 8] }).send();
    assert_custom_err(&r, tally_err(TallyError::PowInvalid));
}

#[test]
fn pow_reused_across_capsules_is_invalid() {
    let mut e = Env::funded();
    e.set_pow_difficulty(DIFF);
    let other_capsule = solana_pubkey::Pubkey::new_unique();
    let sol = e.solve_pow_for(other_capsule, 1, DIFF);
    assert_custom_err(&e.pay().pow(sol).send(), tally_err(TallyError::PowInvalid));
}

#[test]
fn pow_ok_and_compute_reported() {
    let mut e = Env::funded();
    e.set_pow_difficulty(DIFF);
    let sol = e.solve_pow_for(e.capsule, 1, DIFF);
    let meta = e.pay().pow(sol).send().unwrap();
    println!("pay_offer + hashcash CU = {}", meta.compute_units_consumed);
    assert!(meta.compute_units_consumed < 45_000);
}
