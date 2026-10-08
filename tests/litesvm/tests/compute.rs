//! Compute budget guard: docs/06 and the whitepaper promise `pay_offer` < 45,000 CU without
//! the PoW gate. Fails loudly if a change regresses it.
use tally_core::menu::{compute_floors, Item};
use tally_litesvm::*;

#[test]
fn pay_offer_under_45k_cu() {
    let mut e = Env::funded();
    let m = e.pay().send().unwrap();
    println!("pay_offer CU (8-item menu) = {}", m.compute_units_consumed);
    assert!(
        m.compute_units_consumed < 45_000,
        "pay_offer used {} CU",
        m.compute_units_consumed
    );
}

#[test]
fn deep_proof_stays_cheap() {
    // 4,096 items → proof depth 12.
    let mut items: Vec<Item> = (0..4096u32)
        .map(|i| {
            let mut id = [0u8; 16];
            id[..4].copy_from_slice(&i.to_le_bytes());
            Item {
                id,
                category: 3,
                price: 4_500_000 + u64::from(i),
                floor: 0,
            }
        })
        .collect();
    compute_floors(&mut items);
    let mut e = Env::funded();
    let m = e.pay().items(items).item(0).send().unwrap();
    println!(
        "pay_offer CU (4,096-item menu, depth 12) = {}",
        m.compute_units_consumed
    );
    assert!(m.compute_units_consumed < 45_000);
}

#[test]
fn full_featured_pay_under_45k_cu() {
    // Lighthouse required + PoW gate on: the heaviest configuration.
    let mut e = Env::funded().with_flags(FLAG_REQUIRE_LIGHTHOUSE);
    e.set_pow_difficulty(8);
    let capsule = e.capsule;
    let sol = e.solve_pow_for(capsule, 1, 8);
    let m = e.pay().pow(sol).send().unwrap();
    println!(
        "pay_offer CU (lighthouse + pow) = {}",
        m.compute_units_consumed
    );
    assert!(m.compute_units_consumed < 45_000);
}
