//! Invariants 4 (freshness tiers) and 6 (Merkle inclusion).
use tally_litesvm::*;

#[test]
fn stale_menu() {
    let mut e = Env::funded();
    let now = e.slot();
    let r = e.pay().issued_at(now - 200).window(100).send();
    assert_custom_err(&r, tally_err(TallyError::Stale));
}

#[test]
fn not_yet_valid() {
    let mut e = Env::funded();
    let now = e.slot();
    let r = e.pay().issued_at(now + 5).send();
    assert_custom_err(&r, tally_err(TallyError::Stale));
}

#[test]
fn boundaries_expires_slot_inclusive() {
    let mut e = Env::funded();
    let now = e.slot();
    e.pay().issued_at(now - 150).window(150).send().unwrap();
}

#[test]
fn boundaries_window_150_autonomous() {
    let mut e = Env::funded();
    e.pay().window(150).send().unwrap();
}

#[test]
fn window_151_needs_extended_flag() {
    let mut e = Env::funded();
    assert_custom_err(
        &e.pay().window(151).owner_signs().send(),
        tally_err(TallyError::WindowTooLong),
    );
}

#[test]
fn window_151_with_flag_needs_cosign() {
    let mut e = Env::funded().with_flags(FLAG_ALLOW_EXTENDED);
    assert_custom_err(
        &e.pay().window(151).send(),
        tally_err(TallyError::OwnerCosignRequired),
    );
    e.pay().window(151).owner_signs().send().unwrap();
}

#[test]
fn window_too_long() {
    let mut e = Env::funded().with_flags(FLAG_ALLOW_EXTENDED);
    assert_custom_err(
        &e.pay().window(9_001).owner_signs().send(),
        tally_err(TallyError::WindowTooLong),
    );
}

#[test]
fn price_inflation() {
    let mut e = Env::funded();
    assert_custom_err(
        &e.pay().args(|a| a.price = 9_900_000).send(),
        tally_err(TallyError::NotOnMenu),
    );
}

#[test]
fn floor_tampering() {
    let mut e = Env::funded();
    // Agent claims the large latte's floor equals its price to dodge the co-sign rule.
    assert_custom_err(
        &e.pay().item(1).args(|a| a.category_floor = a.price).send(),
        tally_err(TallyError::NotOnMenu),
    );
}

#[test]
fn unshown_item() {
    let mut e = Env::funded();
    assert_custom_err(
        &e.pay().args(|a| a.item_id = [0xde; 16]).send(),
        tally_err(TallyError::NotOnMenu),
    );
}

#[test]
fn proof_too_long() {
    let mut e = Env::funded();
    assert_custom_err(
        &e.pay().args(|a| a.proof = vec![[0; 32]; 17]).send(),
        tally_err(TallyError::NotOnMenu),
    );
}
