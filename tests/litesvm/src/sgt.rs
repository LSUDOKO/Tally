//! Builds a *real* Seeker-Genesis-Token-shaped asset with the Token-2022 program loaded in LiteSVM:
//! a collection mint with `GroupPointer` + `TokenGroup`, and a member mint with
//! `GroupMemberPointer` + `TokenGroupMember`, held (amount 1) by a given owner. The program's
//! hand-written TLV parser is therefore tested against bytes produced by Token-2022 itself.
use anchor_lang::{InstructionData, ToAccountMetas};
use solana_instruction::Instruction;
use solana_keypair::Keypair;
use solana_pubkey::Pubkey;
use solana_signer::Signer;
use spl_token_2022_interface::{
    extension::{group_member_pointer, group_pointer, ExtensionType},
    instruction as t22,
    state::Mint,
    ID as TOKEN_2022,
};

use crate::{pda, Env, TxResult, ATA_PROGRAM, SGT_SEED, SPL_TOKEN, SYSTEM};

pub struct Sgt {
    pub group: Pubkey,
    pub mint: Pubkey,
    pub token_account: Pubkey,
}

impl Env {
    fn create_t22_mint(&mut self, kp: &Keypair, ext: ExtensionType, variable_extra: usize) -> Vec<Instruction> {
        let payer = self.admin.pubkey();
        let len = ExtensionType::try_calculate_account_len::<Mint>(&[ext]).unwrap();
        let lamports = self.svm.minimum_balance_for_rent_exemption(len + variable_extra + 64);
        vec![solana_system_interface::instruction::create_account(&payer, &kp.pubkey(), lamports, len as u64, &TOKEN_2022)]
    }

    /// Create an SGT collection (group) and return its mint address.
    pub fn create_sgt_group(&mut self) -> Pubkey {
        let admin = self.admin.insecure_clone();
        let g = Keypair::new();
        let mut ixs = self.create_t22_mint(&g, ExtensionType::GroupPointer, 80);
        ixs.push(group_pointer::instruction::initialize(&TOKEN_2022, &g.pubkey(), Some(admin.pubkey()), Some(g.pubkey())).unwrap());
        ixs.push(t22::initialize_mint2(&TOKEN_2022, &g.pubkey(), &admin.pubkey(), None, 0).unwrap());
        ixs.push(spl_token_group_interface::instruction::initialize_group(&TOKEN_2022, &g.pubkey(), &g.pubkey(), &admin.pubkey(), Some(admin.pubkey()), 1_000_000));
        self.send(&ixs, &[&admin, &g]).expect("create sgt group");
        g.pubkey()
    }

    /// Mint one SGT of `group` to `owner`.
    pub fn mint_sgt(&mut self, group: Pubkey, owner: &Pubkey, amount: u64) -> Sgt {
        let admin = self.admin.insecure_clone();
        let m = Keypair::new();
        let mut ixs = self.create_t22_mint(&m, ExtensionType::GroupMemberPointer, 80);
        ixs.push(group_member_pointer::instruction::initialize(&TOKEN_2022, &m.pubkey(), Some(admin.pubkey()), Some(m.pubkey())).unwrap());
        ixs.push(t22::initialize_mint2(&TOKEN_2022, &m.pubkey(), &admin.pubkey(), None, 0).unwrap());
        ixs.push(spl_token_group_interface::instruction::initialize_member(&TOKEN_2022, &m.pubkey(), &m.pubkey(), &admin.pubkey(), &group, &admin.pubkey()));
        let ata = anchor_spl::associated_token::get_associated_token_address_with_program_id(owner, &m.pubkey(), &TOKEN_2022);
        ixs.push(anchor_spl::associated_token::spl_associated_token_account::instruction::create_associated_token_account(&admin.pubkey(), owner, &m.pubkey(), &TOKEN_2022));
        if amount > 0 {
            ixs.push(t22::mint_to(&TOKEN_2022, &m.pubkey(), &ata, &admin.pubkey(), &[], amount).unwrap());
        }
        self.send(&ixs, &[&admin, &m]).expect("mint sgt");
        Sgt { group, mint: m.pubkey(), token_account: ata }
    }

    /// Re-run `initialize_config` is not possible; instead tests create the config with this group.
    pub fn sgt_group(&self) -> Pubkey {
        self.fetch::<tally::state::Config>(&self.config).sgt_group
    }

    pub fn try_create_capsule_with_sgt(&mut self, sgt: &Sgt) -> TxResult {
        let rules = {
            let mut r = crate::coffee_rules();
            r.expiry_slot = self.slot() + crate::RELATIVE_EXPIRY;
            r
        };
        let owner = self.owner.insecure_clone();
        let ix = Instruction {
            program_id: tally::ID,
            accounts: tally::accounts::CreateCapsuleWithSgt {
                owner: owner.pubkey(),
                config: self.config,
                capsule: self.capsule,
                mint: self.usdc,
                vault: self.vault,
                sgt_token_account: sgt.token_account,
                sgt_mint: sgt.mint,
                token_program: SPL_TOKEN,
                associated_token_program: ATA_PROGRAM,
                system_program: SYSTEM,
            }
            .to_account_metas(None),
            data: tally::instruction::CreateCapsuleWithSgt { capsule_id: self.capsule_id, rules, agent: self.agent.pubkey() }.data(),
        };
        self.send(&[ix], &[&owner])
    }

    pub fn try_claim_promo(&mut self, promo_id: u64) -> TxResult {
        let owner = self.owner.insecure_clone();
        let sgt_mint = self.capsule_state().sgt_mint;
        let ix = Instruction {
            program_id: tally::ID,
            accounts: tally::accounts::ClaimPromo {
                owner: owner.pubkey(),
                capsule: self.capsule,
                sgt_claim: pda(&[SGT_SEED, sgt_mint.as_ref(), &promo_id.to_le_bytes()]),
                system_program: SYSTEM,
            }
            .to_account_metas(None),
            data: tally::instruction::ClaimPromo { promo_id }.data(),
        };
        self.send(&[ix], &[&owner])
    }
}
