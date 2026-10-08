//! Hand-encoded Lighthouse assertion (filled in Task 15). Until then, requesting one panics.
use solana_instruction::Instruction;
use solana_pubkey::Pubkey;

pub fn assert_token_amount_eq(_token_account: Pubkey, _amount: u64) -> Instruction {
    unimplemented!("Lighthouse encoding lands in Task 15")
}
