//! Invariants 1–3: authority & liveness, instructions-sysvar introspection, Ed25519 offset hardening.
use ed25519_dalek::SigningKey;
use solana_compute_budget_interface::ComputeBudgetInstruction;
use solana_instruction::AccountMeta;
use solana_keypair::Keypair;
use solana_pubkey::Pubkey;
use tally_litesvm::*;

#[test]
fn honest_signature_passes_introspection() {
    let mut e = Env::funded();
    e.pay().send().unwrap();
}

#[test]
fn missing_ed25519() {
    let mut e = Env::funded();
    let r = e
        .pay()
        .ixs(|v| {
            v.remove(0);
        })
        .send();
    assert_custom_err(&r, tally_err(TallyError::MissingSig));
}

#[test]
fn ed25519_not_adjacent() {
    let mut e = Env::funded();
    let r = e
        .pay()
        .ixs(|v| v.insert(1, ComputeBudgetInstruction::set_compute_unit_limit(400_000)))
        .send();
    assert_custom_err(&r, tally_err(TallyError::MissingSig));
}

#[test]
fn ed25519_cross_ix_exploit_shape_rejected() {
    let mut e = Env::funded();
    let r = e
        .pay()
        .args(|a| a.menu_nonce += 1)
        .ixs(cross_ix_exploit)
        .send();
    assert_custom_err(&r, tally_err(TallyError::BadSigIx));
}

#[test]
fn ed25519_offset_into_header_reverts() {
    let mut e = Env::funded();
    let r = e.pay().ixs(|v| patch_u16(&mut v[0].data, 10, 8)).send();
    assert!(r.is_err());
}

#[test]
fn ed25519_two_sigs() {
    let mut e = Env::funded();
    let r = e
        .pay()
        .ixs(|v| make_two_sig_ix(&mut v[0], &SigningKey::from_bytes(&[3; 32])))
        .send();
    assert_custom_err(&r, tally_err(TallyError::BadSigIx));
}

#[test]
fn ed25519_with_accounts() {
    let mut e = Env::funded();
    let r = e
        .pay()
        .ixs(|v| {
            v[0].accounts
                .push(AccountMeta::new_readonly(Pubkey::new_unique(), false))
        })
        .send();
    assert_custom_err(&r, tally_err(TallyError::BadSigIx));
}

#[test]
fn forged_menu() {
    let mut e = Env::funded();
    let r = e
        .pay()
        .menu_signer(SigningKey::from_bytes(&[99; 32]))
        .send();
    assert_custom_err(&r, tally_err(TallyError::WrongSigner));
}

#[test]
fn wrong_message() {
    let mut e = Env::funded();
    let r = e.pay().args(|a| a.menu_nonce += 1).send();
    assert_custom_err(&r, tally_err(TallyError::WrongMessage));
}

#[test]
fn header_bound_to_merchant_pda() {
    // A second merchant shares the SAME signing key. A menu signed for merchant A must not
    // settle through merchant B's registry entry: the header commits to merchant_pda.
    let mut e = Env::funded();
    let signer = e.merchant_signer.clone();
    let (m2, p2) = e.register_extra_merchant(&signer, 5_000_000_000);
    let m1 = e.merchant;
    let r = e
        .pay()
        .payout(p2)
        .ixs(move |v| {
            for meta in v[1].accounts.iter_mut() {
                if meta.pubkey == m1 {
                    meta.pubkey = m2;
                }
            }
        })
        .send();
    assert_custom_err(&r, tally_err(TallyError::WrongMessage));
}

#[test]
fn rotated_key_rejects_old_menu() {
    let mut e = Env::funded();
    let old = e.merchant_signer.clone();
    e.rotate_signing_key(SigningKey::from_bytes(&[55; 32]));
    let r = e.pay().menu_signer(old).send();
    assert_custom_err(&r, tally_err(TallyError::WrongSigner));
}

#[test]
fn wrong_agent() {
    let mut e = Env::funded();
    let k = Keypair::new();
    e.svm
        .airdrop(&solana_signer::Signer::pubkey(&k), 1_000_000_000)
        .unwrap();
    let r = e.pay().agent(k).send();
    assert_custom_err(
        &r,
        anchor_err(anchor_lang::error::ErrorCode::ConstraintHasOne),
    );
}

#[test]
fn frozen() {
    let mut e = Env::funded();
    e.freeze();
    assert_custom_err(&e.pay().send(), tally_err(TallyError::Frozen));
}

#[test]
fn expired_capsule() {
    let mut e = Env::funded();
    let exp = e.capsule_state().expiry_slot;
    e.warp(exp);
    assert_custom_err(&e.pay().send(), tally_err(TallyError::Expired));
}

#[test]
fn merchant_inactive() {
    let mut e = Env::funded();
    e.set_merchant_active(false);
    assert_custom_err(&e.pay().send(), tally_err(TallyError::MerchantInactive));
}
