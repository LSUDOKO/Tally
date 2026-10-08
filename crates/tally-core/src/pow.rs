//! Merchant-enabled proof-of-work gate (docs/20, Part B). Hashcash over a challenge bound to
//! the menu rotation, the capsule and a recent slot hash, so work cannot be reused.

use crate::hash::sha256v;

/// `SHA256("TALLY_POW_V1" || menu_nonce_le64 || capsule || slot_hash)`.
pub fn pow_challenge(menu_nonce: u64, capsule: &[u8; 32], slot_hash: &[u8; 32]) -> [u8; 32] {
    sha256v(&[
        b"TALLY_POW_V1",
        &menu_nonce.to_le_bytes(),
        capsule,
        slot_hash,
    ])
}

pub fn leading_zero_bits(h: &[u8; 32]) -> u32 {
    let mut n = 0;
    for b in h {
        if *b == 0 {
            n += 8;
        } else {
            n += b.leading_zeros();
            break;
        }
    }
    n
}

/// Valid when `SHA256(challenge || nonce)` has at least `difficulty` leading zero bits.
pub fn verify_hashcash(challenge: &[u8; 32], nonce: &[u8; 8], difficulty: u8) -> bool {
    leading_zero_bits(&sha256v(&[challenge, nonce])) >= u32::from(difficulty)
}

/// Brute-force a nonce (client side only).
#[cfg(not(target_os = "solana"))]
pub fn solve_hashcash(challenge: &[u8; 32], difficulty: u8) -> [u8; 8] {
    (0u64..)
        .map(u64::to_le_bytes)
        .find(|n| verify_hashcash(challenge, n, difficulty))
        .unwrap_or([0; 8])
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn leading_zero_bits_counts_across_bytes() {
        let mut h = [0u8; 32];
        h[1] = 0b0001_0000;
        assert_eq!(leading_zero_bits(&h), 11);
    }
    #[test]
    fn solve_then_verify() {
        let c = pow_challenge(42, &[1; 32], &[2; 32]);
        let n = solve_hashcash(&c, 12);
        assert!(verify_hashcash(&c, &n, 12));
    }
    #[test]
    fn challenge_differs_per_input() {
        let base = pow_challenge(42, &[1; 32], &[2; 32]);
        assert_ne!(base, pow_challenge(43, &[1; 32], &[2; 32]));
        assert_ne!(base, pow_challenge(42, &[3; 32], &[2; 32]));
        assert_ne!(base, pow_challenge(42, &[1; 32], &[9; 32]));
    }
}
