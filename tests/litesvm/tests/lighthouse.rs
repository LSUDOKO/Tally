//! Invariant 10, defence-in-depth half: an optional, capsule-required terminal Lighthouse assertion.
use tally_litesvm::{lighthouse::assert_token_amount_eq, *};

#[test]
fn lighthouse_encoding_passes_when_true_and_fails_when_false() {
    let mut e = Env::funded();
    let bal = e.token_balance(&e.vault);
    e.send_plain(&[assert_token_amount_eq(e.vault, bal)])
        .unwrap();
    assert!(e
        .send_plain(&[assert_token_amount_eq(e.vault, bal + 1)])
        .is_err());
}

#[test]
fn lighthouse_missing() {
    let mut e = Env::funded().with_flags(FLAG_REQUIRE_LIGHTHOUSE);
    assert_custom_err(
        &e.pay().lighthouse(false).send(),
        tally_err(TallyError::LighthouseMissing),
    );
}

#[test]
fn lighthouse_ok() {
    let mut e = Env::funded().with_flags(FLAG_REQUIRE_LIGHTHOUSE);
    e.pay().send().unwrap();
}

#[test]
fn lighthouse_not_last_is_missing() {
    let mut e = Env::funded().with_flags(FLAG_REQUIRE_LIGHTHOUSE);
    let r = e
        .pay()
        .ixs(|v| {
            v.push(
                solana_compute_budget_interface::ComputeBudgetInstruction::set_compute_unit_price(
                    1,
                ),
            )
        })
        .send();
    assert_custom_err(&r, tally_err(TallyError::LighthouseMissing));
}

#[test]
fn lighthouse_on_other_account_is_missing() {
    let mut e = Env::funded().with_flags(FLAG_REQUIRE_LIGHTHOUSE);
    let payout = e.payout;
    let bal = e.token_balance(&payout);
    let r = e
        .pay()
        .lighthouse(false)
        .ixs(move |v| v.push(assert_token_amount_eq(payout, bal + 4_500_000)))
        .send();
    assert_custom_err(&r, tally_err(TallyError::LighthouseMissing));
}

#[test]
fn lighthouse_wrong_amount_reverts_whole_tx() {
    let mut e = Env::funded().with_flags(FLAG_REQUIRE_LIGHTHOUSE);
    let v0 = e.token_balance(&e.vault);
    assert!(e.pay().lighthouse_amount_off_by(1).send().is_err());
    assert_eq!(e.token_balance(&e.vault), v0);
}

#[test]
fn optional_lighthouse_accepted_without_flag() {
    let mut e = Env::funded();
    e.pay().lighthouse(true).send().unwrap();
}

#[test]
fn trivially_true_lighthouse_assertion_is_rejected() {
    // `vault amount >= 0` always passes Lighthouse, so it must not count as the required check.
    let mut e = Env::funded().with_flags(FLAG_REQUIRE_LIGHTHOUSE);
    let vault = e.vault;
    let r = e
        .pay()
        .lighthouse(false)
        .ixs(move |v| {
            v.push(tally_litesvm::lighthouse::assert_token_amount_op(
                vault, 0, 4,
            ))
        })
        .send();
    assert_custom_err(&r, tally_err(TallyError::LighthouseMissing));
}
