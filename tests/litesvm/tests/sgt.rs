//! Seeker Genesis Token gate: SGT-verified capsules and one-claim-per-device promos.
use solana_keypair::Keypair;
use solana_signer::Signer;
use tally_litesvm::*;

#[test]
fn extension_ids_match_token_2022() {
    use spl_token_2022_interface::extension::ExtensionType;
    assert_eq!(
        ExtensionType::TokenGroupMember as u16,
        tally::checks::sgt::TOKEN_GROUP_MEMBER_EXT
    );
}

#[test]
fn sgt_ok() {
    let mut e = Env::new();
    let (g, o) = (e.sgt_group_mint, e.owner.pubkey());
    let sgt = e.mint_sgt(g, &o, 1);
    e.try_create_capsule_with_sgt(&sgt).unwrap();
    let c = e.capsule_state();
    assert_ne!(c.flags & FLAG_SGT_VERIFIED, 0);
    assert_eq!(c.sgt_mint, sgt.mint);
}

#[test]
fn sgt_wrong_group() {
    let mut e = Env::new();
    let other = e.create_sgt_group();
    let o = e.owner.pubkey();
    let sgt = e.mint_sgt(other, &o, 1);
    assert_custom_err(
        &e.try_create_capsule_with_sgt(&sgt),
        tally_err(TallyError::InvalidSgt),
    );
}

#[test]
fn sgt_wrong_owner() {
    let mut e = Env::new();
    let g = e.sgt_group_mint;
    let sgt = e.mint_sgt(g, &Keypair::new().pubkey(), 1);
    assert_custom_err(
        &e.try_create_capsule_with_sgt(&sgt),
        tally_err(TallyError::InvalidSgt),
    );
}

#[test]
fn sgt_amount_zero() {
    let mut e = Env::new();
    let (g, o) = (e.sgt_group_mint, e.owner.pubkey());
    let sgt = e.mint_sgt(g, &o, 0);
    assert_custom_err(
        &e.try_create_capsule_with_sgt(&sgt),
        tally_err(TallyError::InvalidSgt),
    );
}

#[test]
fn classic_spl_token_rejected_as_sgt() {
    let mut e = Env::new();
    let (o, usdc) = (e.owner.pubkey(), e.usdc);
    let fake_account = e.ata(&o, &usdc);
    let fake = tally_litesvm::sgt::Sgt {
        group: e.sgt_group_mint,
        mint: usdc,
        token_account: fake_account,
    };
    assert_custom_err(
        &e.try_create_capsule_with_sgt(&fake),
        tally_err(TallyError::InvalidSgt),
    );
}

#[test]
fn promo_claim_once_per_device() {
    let mut e = Env::new();
    let (g, o) = (e.sgt_group_mint, e.owner.pubkey());
    let sgt = e.mint_sgt(g, &o, 1);
    e.try_create_capsule_with_sgt(&sgt).unwrap();
    e.try_claim_promo(1).unwrap();
    assert!(e.try_claim_promo(1).is_err());
    e.try_claim_promo(2).unwrap();
}

#[test]
fn claim_requires_sgt_flag() {
    let mut e = Env::new().with_capsule(coffee_rules(), 0);
    assert_custom_err(&e.try_claim_promo(1), tally_err(TallyError::InvalidSgt));
}
