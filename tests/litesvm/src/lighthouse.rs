//! Hand-encoded Lighthouse `AssertTokenAccount { Amount == value }` (Jac0xb/lighthouse,
//! clients/rust/src/generated). Layout: `[9][log_level u8][TokenAccountAssertion::Amount = 2]
//! [value u64 LE][IntegerOperator::Equal = 0]`, single read-only account = target.
//! Validated against the real mainnet program in tests/lighthouse.rs.
use solana_instruction::{AccountMeta, Instruction};
use solana_pubkey::Pubkey;

pub const LIGHTHOUSE_ID: Pubkey = tally::constants::LIGHTHOUSE_ID;
const ASSERT_TOKEN_ACCOUNT: u8 = 9;
const LOG_SILENT: u8 = 0;
const ASSERTION_AMOUNT: u8 = 2;
const OP_EQUAL: u8 = 0;

pub fn assert_token_amount_eq(token_account: Pubkey, amount: u64) -> Instruction {
    let mut data = vec![ASSERT_TOKEN_ACCOUNT, LOG_SILENT, ASSERTION_AMOUNT];
    data.extend_from_slice(&amount.to_le_bytes());
    data.push(OP_EQUAL);
    Instruction {
        program_id: LIGHTHOUSE_ID,
        accounts: vec![AccountMeta::new_readonly(token_account, false)],
        data,
    }
}

/// Same assertion with an arbitrary `IntegerOperator` (e.g. 4 = GreaterThanOrEqual), used to
/// show that a trivially-true assertion does not satisfy a capsule's Lighthouse requirement.
pub fn assert_token_amount_op(token_account: Pubkey, amount: u64, operator: u8) -> Instruction {
    let mut ix = assert_token_amount_eq(token_account, amount);
    let last = ix.data.len() - 1;
    ix.data[last] = operator;
    ix
}
