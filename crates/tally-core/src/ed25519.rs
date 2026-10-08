//! Building and *defensively* parsing native Ed25519 precompile instruction data
//! (docs/06, invariant 3). Layout: `[num_sigs u8][pad u8]` then per signature seven u16 LE:
//! `sig_off, sig_ix, pk_off, pk_ix, msg_off, msg_len, msg_ix`.

use crate::ED25519_HEADER_LEN;
use alloc::vec::Vec;

/// Why a precompile instruction was refused. Every variant maps to an on-chain revert.
#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum SigIxError {
    TooShort,
    /// Exactly one signature is allowed.
    NotSingle,
    /// An `*_instruction_index` points outside this instruction (the offset-hijack bug class).
    CrossInstruction,
    /// An offset points back into the 16-byte offsets header.
    OffsetInHeader,
    OutOfBounds,
    BadMessageLen,
}

/// Borrowed views of the verified pubkey, signature and message.
pub struct SigView<'a> {
    pub pubkey: &'a [u8],
    pub signature: &'a [u8],
    pub message: &'a [u8],
}

const PK_OFF: u16 = 16;
const SIG_OFF: u16 = 48;
const MSG_OFF: u16 = 112;

/// Canonical single-signature data with every argument inside this instruction.
pub fn build_ix_data(pubkey: &[u8; 32], sig: &[u8; 64], msg: &[u8]) -> Vec<u8> {
    let mut d = Vec::with_capacity(MSG_OFF as usize + msg.len());
    d.extend_from_slice(&[1, 0]);
    for v in [
        SIG_OFF,
        u16::MAX,
        PK_OFF,
        u16::MAX,
        MSG_OFF,
        msg.len() as u16,
        u16::MAX,
    ] {
        d.extend_from_slice(&v.to_le_bytes());
    }
    d.extend_from_slice(pubkey);
    d.extend_from_slice(sig);
    d.extend_from_slice(msg);
    d
}

/// Parse precompile data, refusing anything but one self-contained signature over a
/// message of exactly `expected_msg_len` bytes.
pub fn parse_single(d: &[u8], expected_msg_len: usize) -> Result<SigView<'_>, SigIxError> {
    if d.len() < ED25519_HEADER_LEN {
        return Err(SigIxError::TooShort);
    }
    if d[0] != 1 {
        return Err(SigIxError::NotSingle);
    }
    let u = |i: usize| u16::from_le_bytes([d[i], d[i + 1]]);
    let (sig_off, sig_ix, pk_off, pk_ix) = (u(2) as usize, u(4), u(6) as usize, u(8));
    let (msg_off, msg_len, msg_ix) = (u(10) as usize, u(12) as usize, u(14));
    if sig_ix != u16::MAX || pk_ix != u16::MAX || msg_ix != u16::MAX {
        return Err(SigIxError::CrossInstruction);
    }
    if sig_off < ED25519_HEADER_LEN || pk_off < ED25519_HEADER_LEN || msg_off < ED25519_HEADER_LEN {
        return Err(SigIxError::OffsetInHeader);
    }
    let fits = |o: usize, n: usize| o.checked_add(n).is_some_and(|e| e <= d.len());
    if !(fits(sig_off, 64) && fits(pk_off, 32) && fits(msg_off, msg_len)) {
        return Err(SigIxError::OutOfBounds);
    }
    if msg_len != expected_msg_len {
        return Err(SigIxError::BadMessageLen);
    }
    Ok(SigView {
        pubkey: &d[pk_off..pk_off + 32],
        signature: &d[sig_off..sig_off + 64],
        message: &d[msg_off..msg_off + msg_len],
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::vec::Vec;

    fn ok_data() -> Vec<u8> {
        build_ix_data(&[7; 32], &[8; 64], &[9; 144])
    }
    fn set_u16(d: &mut [u8], at: usize, v: u16) {
        d[at..at + 2].copy_from_slice(&v.to_le_bytes());
    }
    fn err(d: &[u8], len: usize) -> Option<SigIxError> {
        parse_single(d, len).err()
    }

    #[test]
    fn roundtrip() {
        let d = ok_data();
        let v = parse_single(&d, 144).unwrap();
        assert_eq!(v.pubkey, &[7; 32]);
        assert_eq!(v.signature, &[8; 64]);
        assert_eq!(v.message, &[9; 144]);
    }
    #[test]
    fn too_short() {
        assert_eq!(err(&[1, 0, 0], 144), Some(SigIxError::TooShort));
    }
    #[test]
    fn two_signatures() {
        let mut d = ok_data();
        d[0] = 2;
        assert_eq!(err(&d, 144), Some(SigIxError::NotSingle));
    }
    #[test]
    fn cross_instruction_sig() {
        let mut d = ok_data();
        set_u16(&mut d, 4, 0);
        assert_eq!(err(&d, 144), Some(SigIxError::CrossInstruction));
    }
    #[test]
    fn cross_instruction_pk() {
        let mut d = ok_data();
        set_u16(&mut d, 8, 1);
        assert_eq!(err(&d, 144), Some(SigIxError::CrossInstruction));
    }
    #[test]
    fn cross_instruction_msg() {
        let mut d = ok_data();
        set_u16(&mut d, 14, 2);
        assert_eq!(err(&d, 144), Some(SigIxError::CrossInstruction));
    }
    #[test]
    fn offset_into_header() {
        let mut d = ok_data();
        set_u16(&mut d, 10, 15);
        assert_eq!(err(&d, 144), Some(SigIxError::OffsetInHeader));
    }
    #[test]
    fn out_of_bounds() {
        let mut d = ok_data();
        set_u16(&mut d, 12, 145);
        assert_eq!(err(&d, 145), Some(SigIxError::OutOfBounds));
    }
    #[test]
    fn wrong_msg_len() {
        let d = build_ix_data(&[7; 32], &[8; 64], &[9; 100]);
        assert_eq!(err(&d, 144), Some(SigIxError::BadMessageLen));
    }
    #[test]
    fn huge_offset_no_overflow() {
        let mut d = ok_data();
        set_u16(&mut d, 6, u16::MAX - 1);
        assert_eq!(err(&d, 144), Some(SigIxError::OutOfBounds));
    }
}
