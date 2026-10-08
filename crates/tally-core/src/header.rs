//! The 144-byte menu header a merchant signs with Ed25519 (docs/07, "Signed header").

use crate::{DOMAIN, HASH_ALG_SHA256, HEADER_LEN, HEADER_VERSION};

/// Variable fields of a signed menu header. Domain, hash algorithm and version are fixed.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct HeaderFields {
    pub program_id: [u8; 32],
    pub merchant_pda: [u8; 32],
    pub menu_root: [u8; 32],
    pub issued_slot: u64,
    pub expires_slot: u64,
    pub menu_nonce: u64,
    pub item_count: u32,
}

/// Serialise the canonical 144-byte header. The program rebuilds this from instruction
/// arguments and compares it byte-for-byte with the Ed25519-signed message.
pub fn build_header(f: &HeaderFields) -> [u8; HEADER_LEN] {
    let mut h = [0u8; HEADER_LEN];
    h[0..16].copy_from_slice(&DOMAIN);
    h[16..48].copy_from_slice(&f.program_id);
    h[48..80].copy_from_slice(&f.merchant_pda);
    h[80..112].copy_from_slice(&f.menu_root);
    h[112..120].copy_from_slice(&f.issued_slot.to_le_bytes());
    h[120..128].copy_from_slice(&f.expires_slot.to_le_bytes());
    h[128..136].copy_from_slice(&f.menu_nonce.to_le_bytes());
    h[136..140].copy_from_slice(&f.item_count.to_le_bytes());
    h[140] = HASH_ALG_SHA256;
    h[141] = HEADER_VERSION;
    h
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> HeaderFields {
        HeaderFields {
            program_id: [1; 32],
            merchant_pda: [2; 32],
            menu_root: [3; 32],
            issued_slot: 0x0102030405060708,
            expires_slot: 9,
            menu_nonce: 42,
            item_count: 8,
        }
    }

    #[test]
    fn header_is_144_bytes_with_exact_layout() {
        let h = build_header(&sample());
        assert_eq!(h.len(), 144);
        assert_eq!(&h[0..16], b"TALLY_MENU_V1\0\0\0");
        assert_eq!(&h[16..48], &[1; 32]);
        assert_eq!(&h[48..80], &[2; 32]);
        assert_eq!(&h[80..112], &[3; 32]);
        assert_eq!(&h[112..120], &0x0102030405060708u64.to_le_bytes());
        assert_eq!(&h[120..128], &9u64.to_le_bytes());
        assert_eq!(&h[128..136], &42u64.to_le_bytes());
        assert_eq!(&h[136..140], &8u32.to_le_bytes());
        assert_eq!(h[140], 0);
        assert_eq!(h[141], 1);
        assert_eq!(&h[142..144], &[0, 0]);
    }

    #[test]
    fn any_field_change_changes_header() {
        let a = build_header(&sample());
        let mut s = sample();
        s.menu_nonce = 43;
        assert_ne!(a, build_header(&s));
    }

    #[test]
    fn sha256v_matches_known_vector() {
        let expected = hex::decode("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad").unwrap();
        assert_eq!(crate::hash::sha256v(&[b"a", b"bc"]).to_vec(), expected);
    }
}
