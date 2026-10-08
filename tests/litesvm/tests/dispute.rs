//! Receipt disputes (arbiter-resolved) and the delayed, dispute-blocked bond unlock.
use solana_keypair::Keypair;
use tally_litesvm::*;

const BOND: u64 = 5_000_000_000;

fn paid() -> Env {
    let mut e = Env::funded();
    e.pay().send().unwrap(); // receipt (nonce 1, item 0)
    e
}

#[test]
fn dispute_upheld_slashes_to_claimant() {
    let mut e = paid();
    e.try_open_dispute(1, 0).unwrap();
    assert!(e.receipt(1, 0).disputed);
    let arbiter = e.admin.insecure_clone();
    e.try_resolve_dispute(&arbiter, 1, 0, true, 100_000_000)
        .unwrap();
    let skr = e.owner_skr();
    assert_eq!(e.token_balance(&skr), 100_000_000);
    let m: tally::state::Merchant = e.fetch(&e.merchant);
    assert_eq!(m.skr_bonded, BOND - 100_000_000);
    assert_eq!(m.open_disputes, 0);
}

#[test]
fn dispute_rejected_no_slash() {
    let mut e = paid();
    e.try_open_dispute(1, 0).unwrap();
    let arbiter = e.admin.insecure_clone();
    e.try_resolve_dispute(&arbiter, 1, 0, false, 100_000_000)
        .unwrap();
    assert_eq!(e.token_balance(&e.bond_vault()), BOND);
}

#[test]
fn only_arbiter_resolves() {
    let mut e = paid();
    e.try_open_dispute(1, 0).unwrap();
    let rando = Keypair::new();
    e.svm
        .airdrop(&solana_signer::Signer::pubkey(&rando), 1_000_000_000)
        .unwrap();
    assert!(e.try_resolve_dispute(&rando, 1, 0, true, 1).is_err());
}

#[test]
fn cannot_resolve_twice() {
    let mut e = paid();
    e.try_open_dispute(1, 0).unwrap();
    let arbiter = e.admin.insecure_clone();
    e.try_resolve_dispute(&arbiter, 1, 0, true, 1).unwrap();
    assert_custom_err(
        &e.try_resolve_dispute(&arbiter, 1, 0, true, 1),
        tally_err(TallyError::WrongDisputeState),
    );
}

#[test]
fn cannot_dispute_twice() {
    let mut e = paid();
    e.try_open_dispute(1, 0).unwrap();
    assert!(e.try_open_dispute(1, 0).is_err());
}

#[test]
fn slash_capped_at_bond() {
    let mut e = paid();
    e.try_open_dispute(1, 0).unwrap();
    let arbiter = e.admin.insecure_clone();
    e.try_resolve_dispute(&arbiter, 1, 0, true, u64::MAX)
        .unwrap();
    assert_eq!(e.token_balance(&e.bond_vault()), 0);
}

#[test]
fn bond_withdraw_requires_unlock_request() {
    let mut e = Env::funded();
    assert_custom_err(&e.try_withdraw_bond(1), tally_err(TallyError::BondLocked));
}

#[test]
fn bond_unlock_blocked_before_delay() {
    let mut e = Env::funded();
    e.try_request_bond_unlock().unwrap();
    assert_custom_err(&e.try_withdraw_bond(1), tally_err(TallyError::BondLocked));
}

#[test]
fn bond_unlock_blocked_with_open_dispute() {
    let mut e = paid();
    e.try_open_dispute(1, 0).unwrap();
    e.try_request_bond_unlock().unwrap();
    let s = e.slot();
    e.warp(s + 1_001);
    assert_custom_err(&e.try_withdraw_bond(1), tally_err(TallyError::DisputeOpen));
}

#[test]
fn bond_withdraw_after_delay() {
    let mut e = Env::funded();
    e.try_request_bond_unlock().unwrap();
    let s = e.slot();
    e.warp(s + 1_000);
    e.try_withdraw_bond(BOND).unwrap();
    assert_eq!(e.token_balance(&e.bond_vault()), 0);
    let m: tally::state::Merchant = e.fetch(&e.merchant);
    assert_eq!(m.skr_bonded, 0);
}

#[test]
fn withdraw_relocks_bond() {
    let mut e = Env::funded();
    e.try_request_bond_unlock().unwrap();
    let s = e.slot();
    e.warp(s + 1_000);
    e.try_withdraw_bond(1_000).unwrap();
    assert_custom_err(
        &e.try_withdraw_bond(1_000),
        tally_err(TallyError::BondLocked),
    );
}

#[test]
fn unlock_request_deactivates_merchant() {
    let mut e = Env::funded();
    e.try_request_bond_unlock().unwrap();
    assert_custom_err(&e.pay().send(), tally_err(TallyError::MerchantInactive));
}

#[test]
fn reactivating_cancels_unlock() {
    let mut e = Env::funded();
    e.try_request_bond_unlock().unwrap();
    e.set_merchant_active(true);
    let s = e.slot();
    e.warp(s + 1_000);
    assert_custom_err(&e.try_withdraw_bond(1), tally_err(TallyError::BondLocked));
    e.pay().send().unwrap();
}
