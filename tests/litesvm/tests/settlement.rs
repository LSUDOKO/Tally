//! Invariants 9 (receipt nullifier) and 10 (atomic transfer + exact-delta post-condition),
//! plus the cycle accounting that depends on settled spend.
use tally_core::menu::{compute_floors, Item};
use tally_litesvm::*;

#[test]
fn happy_path_cheapest() {
    let mut e = Env::funded();
    let (v0, p0) = (e.token_balance(&e.vault), e.token_balance(&e.payout));
    let meta = e.pay().send().unwrap();
    assert_eq!(e.token_balance(&e.vault), v0 - 4_500_000);
    assert_eq!(e.token_balance(&e.payout), p0 + 4_500_000);
    let r = e.receipt(1, 0);
    assert_eq!(
        (r.price, r.category, r.category_floor),
        (4_500_000, 3, 4_500_000)
    );
    assert_eq!(r.merchant, e.merchant);
    assert_eq!(r.capsule, e.capsule);
    assert!(!r.cosigned);
    println!("pay_offer CU = {}", meta.compute_units_consumed);
}

#[test]
fn replay_same_offer_reverts() {
    let mut e = Env::funded();
    e.pay().send().unwrap();
    let v = e.token_balance(&e.vault);
    assert!(
        e.pay().send().is_err(),
        "second settlement of (nonce, item) must fail"
    );
    assert_eq!(e.token_balance(&e.vault), v);
}

#[test]
fn same_item_next_rotation_is_allowed() {
    let mut e = Env::funded();
    e.pay().send().unwrap();
    e.pay().fresh_menu().send().unwrap();
}

#[test]
fn single_item_menu_pays() {
    let mut e = Env::funded();
    let mut items = vec![Item {
        id: [1; 16],
        category: 3,
        price: 4_000_000,
        floor: 0,
    }];
    compute_floors(&mut items);
    e.pay().items(items).send().unwrap();
}

#[test]
fn odd_menu_last_leaf_pays() {
    let mut e = Env::funded();
    let mut items: Vec<Item> = (0..3)
        .map(|i| Item {
            id: [i as u8 + 1; 16],
            category: 3,
            price: 4_000_000 + i * 100_000,
            floor: 0,
        })
        .collect();
    compute_floors(&mut items);
    e.pay().items(items).item(2).owner_signs().send().unwrap();
}

#[test]
fn spent_updates() {
    let mut e = Env::funded();
    e.pay().send().unwrap();
    assert_eq!(e.capsule_state().spent_in_cycle, 4_500_000);
}

#[test]
fn over_daily() {
    let mut e = Env::funded();
    for _ in 0..3 {
        e.pay().fresh_menu().send().unwrap(); // 3 × 4.50 = 13.50 of 15.00
    }
    assert_custom_err(
        &e.pay().fresh_menu().send(),
        tally_err(TallyError::OverDaily),
    );
}

#[test]
fn boundaries_spent_equals_daily_cap() {
    let mut e = Env::funded().with_rules(|r| r.daily_cap = 9_000_000);
    e.pay().fresh_menu().send().unwrap();
    e.pay().fresh_menu().send().unwrap();
    assert_eq!(e.capsule_state().spent_in_cycle, 9_000_000);
}

#[test]
fn cycle_reset() {
    let mut e = Env::funded().with_rules(|r| r.daily_cap = 6_000_000);
    e.pay().fresh_menu().send().unwrap();
    assert_custom_err(
        &e.pay().fresh_menu().send(),
        tally_err(TallyError::OverDaily),
    );
    let start = e.capsule_state().cycle_start_slot;
    e.warp(start + tally_core::CYCLE_SLOTS);
    e.pay().fresh_menu().send().unwrap();
    assert_eq!(e.capsule_state().spent_in_cycle, 4_500_000);
}

#[test]
fn cosigned_receipt_records_flag() {
    let mut e = Env::funded();
    e.pay().item(1).owner_signs().send().unwrap();
    assert!(e.receipt(1, 1).cosigned);
}

#[test]
fn owner_extra_signature_ok() {
    let mut e = Env::funded();
    e.pay().owner_signs().send().unwrap();
    assert!(
        !e.receipt(1, 0).cosigned,
        "co-sign was not required at the floor"
    );
}

#[test]
fn insufficient_vault_reverts_cleanly() {
    let mut e = Env::new()
        .with_merchant(5_000_000_000)
        .with_capsule(coffee_rules(), 1_000_000);
    assert!(e.pay().send().is_err());
    assert_eq!(e.token_balance(&e.vault), 1_000_000);
    assert_eq!(e.capsule_state().spent_in_cycle, 0);
}
