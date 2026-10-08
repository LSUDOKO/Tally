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
    let slot = e.recent_slot_entry().0;
    let r = e
        .pay()
        .pow(PowSolution {
            nonce: [0xff; 8],
            slot,
        })
        .send();
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

fn fake_hash(n: u8) -> [u8; 32] {
    [n; 32]
}

#[test]
fn pow_survives_slot_advance() {
    // Mined against slot S; lands at S + 10 while S is still in SlotHashes.
    let mut e = Env::funded();
    e.set_pow_difficulty(DIFF);
    let s = e.slot();
    e.set_slot_hashes(&[(s, fake_hash(1))]);
    let sol = e.solve_pow_at(e.capsule, 1, DIFF, s, fake_hash(1));
    e.warp(s + 10);
    e.set_slot_hashes(&[
        (s + 9, fake_hash(9)),
        (s + 5, fake_hash(5)),
        (s, fake_hash(1)),
    ]);
    e.pay().pow(sol).send().unwrap();
}

#[test]
fn pow_slot_too_old() {
    let mut e = Env::funded();
    e.set_pow_difficulty(DIFF);
    let s = e.slot();
    e.set_slot_hashes(&[(s - 1, fake_hash(2)), (s - 200, fake_hash(3))]);
    let sol = e.solve_pow_at(e.capsule, 1, DIFF, s - 200, fake_hash(3));
    assert_custom_err(&e.pay().pow(sol).send(), tally_err(TallyError::PowInvalid));
}

#[test]
fn pow_slot_not_in_sysvar() {
    let mut e = Env::funded();
    e.set_pow_difficulty(DIFF);
    let s = e.slot();
    e.set_slot_hashes(&[(s - 1, fake_hash(2))]);
    let sol = e.solve_pow_at(e.capsule, 1, DIFF, s - 3, fake_hash(4));
    assert_custom_err(&e.pay().pow(sol).send(), tally_err(TallyError::PowInvalid));
}

#[test]
fn pow_wrong_hash_for_named_slot() {
    let mut e = Env::funded();
    e.set_pow_difficulty(DIFF);
    let s = e.slot();
    e.set_slot_hashes(&[(s - 1, fake_hash(2))]);
    let sol = e.solve_pow_at(e.capsule, 1, DIFF, s - 1, fake_hash(7));
    assert_custom_err(&e.pay().pow(sol).send(), tally_err(TallyError::PowInvalid));
}
