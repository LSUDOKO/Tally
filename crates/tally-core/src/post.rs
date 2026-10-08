//! Invariant 10 post-condition: the vault lost exactly `price` and the payout gained exactly `price`.

/// True only when `vault_before - vault_after == price` and `payout_after - payout_before == price`.
/// Catches extra drains, fee-on-transfer mints shorting the payee, and arithmetic underflow.
pub fn check_exact_deltas(vault_before: u64, vault_after: u64, payout_before: u64, payout_after: u64, price: u64) -> bool {
    matches!(
        (vault_before.checked_sub(vault_after), payout_after.checked_sub(payout_before)),
        (Some(out), Some(inc)) if out == price && inc == price
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn exact_ok() {
        assert!(check_exact_deltas(100, 60, 5, 45, 40));
    }
    #[test]
    fn fee_mint_shorts_payee() {
        assert!(!check_exact_deltas(100, 60, 5, 44, 40));
    }
    #[test]
    fn extra_drain_from_vault() {
        assert!(!check_exact_deltas(100, 59, 5, 45, 40));
    }
    #[test]
    fn underflow_is_false_not_panic() {
        assert!(!check_exact_deltas(10, 60, 5, 45, 40));
    }
}
