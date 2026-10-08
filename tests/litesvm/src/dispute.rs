//! Builders for receipt disputes and the merchant bond-unlock flow.
use anchor_lang::{InstructionData, ToAccountMetas};
use solana_instruction::Instruction;
use solana_keypair::Keypair;
use solana_pubkey::Pubkey;
use solana_signer::Signer;

use crate::{default_items, pda, Env, TxResult, DISPUTE_SEED, SPL_TOKEN, SYSTEM};

impl Env {
    pub fn dispute_address(&self, receipt: &Pubkey) -> Pubkey {
        pda(&[DISPUTE_SEED, receipt.as_ref()])
    }

    /// Capsule owner disputes the receipt for (`nonce`, default item `item`).
    pub fn try_open_dispute(&mut self, nonce: u64, item: usize) -> TxResult {
        let receipt = self.receipt_address(nonce, &default_items()[item].id);
        let owner = self.owner.insecure_clone();
        let ix = Instruction {
            program_id: tally::ID,
            accounts: tally::accounts::OpenDispute {
                owner: owner.pubkey(),
                capsule: self.capsule,
                receipt,
                merchant: self.merchant,
                dispute: self.dispute_address(&receipt),
                system_program: SYSTEM,
            }
            .to_account_metas(None),
            data: tally::instruction::OpenDispute { evidence_hash: [9; 32] }.data(),
        };
        self.send(&[ix], &[&owner])
    }

    pub fn owner_skr(&mut self) -> Pubkey {
        let (o, skr) = (self.owner.pubkey(), self.skr);
        let ata = anchor_spl::associated_token::get_associated_token_address(&o, &skr);
        if !self.exists(&ata) {
            self.ata(&o, &skr);
        }
        ata
    }

    pub fn try_resolve_dispute(&mut self, signer: &Keypair, nonce: u64, item: usize, upheld: bool, slash_amount: u64) -> TxResult {
        let receipt = self.receipt_address(nonce, &default_items()[item].id);
        let claimant_skr = self.owner_skr();
        let ix = Instruction {
            program_id: tally::ID,
            accounts: tally::accounts::ResolveDispute {
                arbiter: signer.pubkey(),
                config: self.config,
                dispute: self.dispute_address(&receipt),
                merchant: self.merchant,
                bond_vault: self.bond_vault(),
                skr_mint: self.skr,
                claimant_skr,
                token_program: SPL_TOKEN,
            }
            .to_account_metas(None),
            data: tally::instruction::ResolveDispute { upheld, slash_amount }.data(),
        };
        self.send(&[ix], &[signer])
    }

    pub fn try_request_bond_unlock(&mut self) -> TxResult {
        let auth = self.merchant_auth.insecure_clone();
        let ix = Instruction {
            program_id: tally::ID,
            accounts: tally::accounts::BondAdmin { authority: auth.pubkey(), config: self.config, merchant: self.merchant }.to_account_metas(None),
            data: tally::instruction::RequestBondUnlock {}.data(),
        };
        self.send(&[ix], &[&auth])
    }

    pub fn try_withdraw_bond(&mut self, amount: u64) -> TxResult {
        let auth = self.merchant_auth.insecure_clone();
        let auth_skr = anchor_spl::associated_token::get_associated_token_address(&auth.pubkey(), &self.skr);
        let ix = Instruction {
            program_id: tally::ID,
            accounts: tally::accounts::WithdrawBond {
                authority: auth.pubkey(),
                config: self.config,
                merchant: self.merchant,
                skr_mint: self.skr,
                bond_vault: self.bond_vault(),
                authority_skr: auth_skr,
                token_program: SPL_TOKEN,
            }
            .to_account_metas(None),
            data: tally::instruction::WithdrawBond { amount }.data(),
        };
        self.send(&[ix], &[&auth])
    }
}
