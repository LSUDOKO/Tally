//! SHA-256 that uses the `sol_sha256` syscall on-chain and a native implementation off-chain.

/// SHA-256 over the concatenation of `parts`.
pub fn sha256v(parts: &[&[u8]]) -> [u8; 32] {
    solana_sha256_hasher::hashv(parts).to_bytes()
}
