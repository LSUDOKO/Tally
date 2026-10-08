//! Builders for `prove_floor_fraud`: anyone can submit two leaves from one signed menu that
//! contradict the merchant's certified category floor.
use anchor_lang::{InstructionData, ToAccountMetas};
use ed25519_dalek::{Signer as _, SigningKey};
use solana_instruction::Instruction;
use solana_keypair::Keypair;
use solana_pubkey::Pubkey;
use solana_signer::Signer;
use tally::instructions::slashing::{LeafArgs, MenuHeaderArgs};
use tally_core::{
    ed25519::build_ix_data,
    header::{build_header, HeaderFields},
    menu::{leaf_hash, Item},
    merkle::MerkleTree,
};

use crate::{default_items, Env, TxResult, ED25519_PROGRAM, SPL_TOKEN};

/// Default menu plus a cheaper drip coffee whose own leaf is honest (floor 3.25) while every
/// other coffee leaf still certifies a 4.50 floor. Index 0 = drip, index 1 = oat latte.
pub fn fraud_items() -> Vec<Item> {
    let mut v = default_items();
    v.insert(0, Item { id: [0xd1; 16], category: 3, price: 3_250_000, floor: 3_250_000 });
    v
}

fn leaf_args(i: &Item) -> LeafArgs {
    LeafArgs { item_id: i.id, category: i.category, price: i.price, floor: i.floor }
}

impl Env {
    pub fn reporter(&mut self) -> (Keypair, Pubkey) {
        let k = Keypair::new();
        self.svm.airdrop(&k.pubkey(), 1_000_000_000).unwrap();
        let skr = self.skr;
        let ata = self.ata(&k.pubkey(), &skr);
        (k, ata)
    }

    pub fn try_prove_fraud(&mut self, items: &[Item], a: usize, b: usize, signer: &SigningKey, reporter: &Keypair, reporter_skr: Pubkey) -> TxResult {
        let tree = MerkleTree::new(items.iter().map(leaf_hash).collect());
        let now = self.slot();
        let h = MenuHeaderArgs { menu_root: tree.root(), issued_slot: now, expires_slot: now + 100, menu_nonce: 777, item_count: items.len() as u32 };
        let header = build_header(&HeaderFields {
            program_id: tally::ID.to_bytes(),
            merchant_pda: self.merchant.to_bytes(),
            menu_root: h.menu_root,
            issued_slot: h.issued_slot,
            expires_slot: h.expires_slot,
            menu_nonce: h.menu_nonce,
            item_count: h.item_count,
        });
        let sig = signer.sign(&header).to_bytes();
        let ed = Instruction { program_id: ED25519_PROGRAM, accounts: vec![], data: build_ix_data(&signer.verifying_key().to_bytes(), &sig, &header) };
        let ix = Instruction {
            program_id: tally::ID,
            accounts: tally::accounts::ProveFloorFraud {
                reporter: reporter.pubkey(),
                config: self.config,
                merchant: self.merchant,
                bond_vault: self.bond_vault(),
                skr_mint: self.skr,
                reporter_skr,
                pool: self.pool(),
                fraud_record: crate::pda(&[tally::constants::FRAUD_SEED, self.merchant.as_ref(), &tree.root()]),
                system_program: crate::SYSTEM,
                ix_sysvar: solana_sdk_ids::sysvar::instructions::ID,
                token_program: SPL_TOKEN,
            }
            .to_account_metas(None),
            data: tally::instruction::ProveFloorFraud {
                header: h,
                leaf_a: leaf_args(&items[a]),
                proof_a: tree.proof(a),
                leaf_b: leaf_args(&items[b]),
                proof_b: tree.proof(b),
            }
            .data(),
        };
        self.send(&[ed, ix], &[reporter])
    }
}
