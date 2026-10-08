//! LiteSVM harness for the Tally program. Loads `target/deploy/tally.so`, creates test mints
//! (USDC 6dp, SKR 6dp), initialises `Config`, and offers builders for merchants, capsules
//! and `pay_offer` transactions so each threat-model test is a few lines.
use anchor_lang::{AccountDeserialize, InstructionData, ToAccountMetas};
use litesvm::{
    types::{FailedTransactionMetadata, TransactionMetadata},
    LiteSVM,
};
use litesvm_token::{CreateAssociatedTokenAccount, CreateMint, MintTo};
use solana_instruction::Instruction;
use solana_instruction_error::InstructionError;
use solana_keypair::Keypair;
use solana_pubkey::Pubkey;
use solana_signer::Signer;
use solana_transaction::Transaction;
use solana_transaction_error::TransactionError;

pub use tally::{constants::*, errors::TallyError};

pub type TxResult = Result<TransactionMetadata, FailedTransactionMetadata>;

pub const SPL_TOKEN: Pubkey = anchor_spl::token::ID;
pub const SYSTEM: Pubkey = anchor_lang::system_program::ID;
pub const ATA_PROGRAM: Pubkey = anchor_spl::associated_token::ID;

pub fn tally_err(e: TallyError) -> u32 {
    e.into()
}

pub fn anchor_err(e: anchor_lang::error::ErrorCode) -> u32 {
    e.into()
}

/// Assert the transaction failed with `InstructionError::Custom(code)` in any instruction.
pub fn assert_custom_err(r: &TxResult, code: u32) {
    match r {
        Err(f) => match &f.err {
            TransactionError::InstructionError(_, InstructionError::Custom(c)) if *c == code => {}
            other => panic!("expected custom error {code}, got {other:?}\nlogs:\n{}", f.meta.logs.join("\n")),
        },
        Ok(m) => panic!("expected custom error {code}, but tx succeeded\nlogs:\n{}", m.logs.join("\n")),
    }
}

pub fn pda(seeds: &[&[u8]]) -> Pubkey {
    Pubkey::find_program_address(seeds, &tally::ID).0
}

pub struct Env {
    pub svm: LiteSVM,
    pub admin: Keypair,
    pub usdc: Pubkey,
    pub skr: Pubkey,
    pub config: Pubkey,
    pub merchant_auth: Keypair,
    pub merchant_signer: ed25519_dalek::SigningKey,
    pub merchant: Pubkey,
    pub payout: Pubkey,
}

impl Default for Env {
    fn default() -> Self {
        Self::new()
    }
}

impl Env {
    pub fn new() -> Self {
        let mut svm = LiteSVM::new();
        let so = concat!(env!("CARGO_MANIFEST_DIR"), "/../../target/deploy/tally.so");
        assert!(std::path::Path::new(so).exists(), "run `cargo build-sbf --manifest-path programs/tally/Cargo.toml` first");
        svm.add_program_from_file(tally::ID, so).expect("load tally.so");
        let admin = Keypair::new();
        svm.airdrop(&admin.pubkey(), 100_000_000_000).unwrap();
        let usdc = CreateMint::new(&mut svm, &admin).decimals(6).send().unwrap();
        let skr = CreateMint::new(&mut svm, &admin).decimals(6).send().unwrap();
        let merchant_auth = Keypair::new();
        svm.airdrop(&merchant_auth.pubkey(), 10_000_000_000).unwrap();
        let merchant = pda(&[MERCHANT_SEED, merchant_auth.pubkey().as_ref()]);
        let mut env = Env {
            svm,
            admin,
            usdc,
            skr,
            config: pda(&[CONFIG_SEED]),
            merchant_auth,
            merchant_signer: ed25519_dalek::SigningKey::from_bytes(&[7u8; 32]),
            merchant,
            payout: Pubkey::default(),
        };
        env.initialize_config().expect("initialize_config");
        env
    }

    pub fn send(&mut self, ixs: &[Instruction], signers: &[&Keypair]) -> TxResult {
        let payer = signers.first().map(|k| k.pubkey());
        let tx = Transaction::new_signed_with_payer(ixs, payer.as_ref(), signers, self.svm.latest_blockhash());
        let r = self.svm.send_transaction(tx);
        self.svm.expire_blockhash();
        r
    }

    pub fn fetch<T: AccountDeserialize>(&self, key: &Pubkey) -> T {
        let acc = self.svm.get_account(key).unwrap_or_else(|| panic!("account {key} missing"));
        T::try_deserialize(&mut &acc.data[..]).expect("deserialize")
    }

    pub fn exists(&self, key: &Pubkey) -> bool {
        self.svm.get_account(key).is_some_and(|a| a.lamports > 0)
    }

    pub fn token_balance(&self, account: &Pubkey) -> u64 {
        litesvm_token::get_spl_account::<litesvm_token::spl_token::state::Account>(&self.svm, account)
            .map(|a| a.amount)
            .unwrap_or(0)
    }

    pub fn slot(&self) -> u64 {
        self.svm.get_sysvar::<solana_clock::Clock>().slot
    }

    pub fn warp(&mut self, slot: u64) {
        self.svm.warp_to_slot(slot);
    }

    pub fn create_mint(&mut self, decimals: u8) -> Pubkey {
        CreateMint::new(&mut self.svm, &self.admin).decimals(decimals).send().unwrap()
    }

    pub fn ata(&mut self, owner: &Pubkey, mint: &Pubkey) -> Pubkey {
        CreateAssociatedTokenAccount::new(&mut self.svm, &self.admin, mint).owner(owner).send().unwrap()
    }

    pub fn mint_to(&mut self, mint: &Pubkey, account: &Pubkey, amount: u64) {
        MintTo::new(&mut self.svm, &self.admin, mint, account, amount).send().unwrap();
    }

    pub fn pool(&self) -> Pubkey {
        pda(&[POOL_SEED])
    }

    pub fn bond_vault(&self) -> Pubkey {
        pda(&[BOND_SEED, self.merchant.as_ref()])
    }

    fn initialize_config(&mut self) -> TxResult {
        let ix = Instruction {
            program_id: tally::ID,
            accounts: tally::accounts::InitializeConfig {
                admin: self.admin.pubkey(),
                config: self.config,
                usdc_mint: self.usdc,
                skr_mint: self.skr,
                pool: self.pool(),
                token_program: SPL_TOKEN,
                system_program: SYSTEM,
            }
            .to_account_metas(None),
            data: tally::instruction::InitializeConfig {
                arbiter: self.admin.pubkey(),
                sgt_group: Pubkey::default(),
                slash_bps: 5_000,
                bond_unlock_delay: 1_000,
            }
            .data(),
        };
        let admin = self.admin.insecure_clone();
        self.send(&[ix], &[&admin])
    }

    /// Register the default merchant with `bond` SKR. Mints the SKR to the authority first.
    pub fn with_merchant(mut self, bond: u64) -> Self {
        self.try_register_merchant(self.skr, bond).expect("register_merchant");
        self
    }

    pub fn try_register_merchant(&mut self, skr_mint: Pubkey, bond: u64) -> TxResult {
        let auth = self.merchant_auth.insecure_clone();
        let usdc = self.usdc;
        self.payout = self.ata(&auth.pubkey(), &usdc);
        let auth_skr = self.ata(&auth.pubkey(), &skr_mint);
        if bond > 0 {
            self.mint_to(&skr_mint, &auth_skr, bond);
        }
        let mut name = [0u8; 32];
        name[..16].copy_from_slice(b"Blue Door Coffee");
        let ix = Instruction {
            program_id: tally::ID,
            accounts: tally::accounts::RegisterMerchant {
                authority: auth.pubkey(),
                config: self.config,
                merchant: self.merchant,
                payout: self.payout,
                skr_mint,
                authority_skr: auth_skr,
                bond_vault: self.bond_vault(),
                token_program: SPL_TOKEN,
                system_program: SYSTEM,
            }
            .to_account_metas(None),
            data: tally::instruction::RegisterMerchant {
                name,
                signing_key: Pubkey::new_from_array(self.merchant_signer.verifying_key().to_bytes()),
                bond,
            }
            .data(),
        };
        self.send(&[ix], &[&auth])
    }

    pub fn merchant_admin_ix(&self, authority: &Pubkey, data: Vec<u8>) -> Instruction {
        Instruction {
            program_id: tally::ID,
            accounts: tally::accounts::MerchantAdmin { authority: *authority, merchant: self.merchant }.to_account_metas(None),
            data,
        }
    }

    pub fn try_rotate_signing_key_as(&mut self, signer: &Keypair, new_key: Pubkey) -> TxResult {
        let ix = self.merchant_admin_ix(&signer.pubkey(), tally::instruction::RotateSigningKey { new_key }.data());
        self.svm.airdrop(&signer.pubkey(), 1_000_000_000).unwrap();
        self.send(&[ix], &[signer])
    }

    pub fn rotate_signing_key(&mut self, new_signer: ed25519_dalek::SigningKey) {
        let auth = self.merchant_auth.insecure_clone();
        self.try_rotate_signing_key_as(&auth, Pubkey::new_from_array(new_signer.verifying_key().to_bytes())).unwrap();
        self.merchant_signer = new_signer;
    }

    pub fn set_merchant_active(&mut self, active: bool) {
        let auth = self.merchant_auth.insecure_clone();
        let ix = self.merchant_admin_ix(&auth.pubkey(), tally::instruction::SetActive { active }.data());
        self.send(&[ix], &[&auth]).unwrap();
    }

    pub fn set_pow_difficulty(&mut self, difficulty: u8) {
        let auth = self.merchant_auth.insecure_clone();
        let ix = self.merchant_admin_ix(&auth.pubkey(), tally::instruction::SetPowDifficulty { difficulty }.data());
        self.send(&[ix], &[&auth]).unwrap();
    }
}
