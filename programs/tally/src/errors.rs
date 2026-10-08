use anchor_lang::prelude::*;

/// Order is part of the public interface: codes 6000..=6019 follow docs/06 exactly,
/// later variants are appended. Never reorder.
#[error_code]
pub enum TallyError {
    #[msg("Capsule is frozen")]
    Frozen,
    #[msg("Capsule expired")]
    Expired,
    #[msg("Merchant inactive")]
    MerchantInactive,
    #[msg("Merchant signature instruction missing")]
    MissingSig,
    #[msg("Malformed or unsafe signature instruction")]
    BadSigIx,
    #[msg("Menu not signed by registered merchant key")]
    WrongSigner,
    #[msg("Signed menu header does not match")]
    WrongMessage,
    #[msg("Menu is stale or not yet valid")]
    Stale,
    #[msg("Menu validity window too long")]
    WindowTooLong,
    #[msg("Item is not on the signed menu")]
    NotOnMenu,
    #[msg("Category not allowed by your rules")]
    Category,
    #[msg("Above your per-purchase cap")]
    OverPerTx,
    #[msg("Above your daily cap")]
    OverDaily,
    #[msg("Pricier than the cheapest match: owner must co-sign")]
    OwnerCosignRequired,
    #[msg("Payout mint mismatch")]
    WrongMint,
    #[msg("Balance post-condition failed")]
    PostCondition,
    #[msg("Required Lighthouse assertion missing")]
    LighthouseMissing,
    #[msg("Merchant requires proof-of-work")]
    PowRequired,
    #[msg("Invalid proof-of-work")]
    PowInvalid,
    #[msg("Arithmetic overflow")]
    Overflow,
    #[msg("Signer is not authorised for this action")]
    Unauthorized,
    #[msg("Bond is still locked")]
    BondLocked,
    #[msg("Merchant has an open dispute")]
    DisputeOpen,
    #[msg("Leaves do not prove a floor contradiction")]
    NotAFraud,
    #[msg("Seeker Genesis Token check failed")]
    InvalidSgt,
    #[msg("Capsule rules are invalid")]
    InvalidRules,
    #[msg("Dispute is not in the required state")]
    WrongDisputeState,
    #[msg("A merchant cannot report its own fraud")]
    SelfReport,
    #[msg("Evidence signed with a retired key is too old")]
    EvidenceExpired,
}
