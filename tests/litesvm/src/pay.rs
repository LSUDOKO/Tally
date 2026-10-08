//! `PayBuilder`: builds `[Ed25519 verify, pay_offer]` (plus an optional terminal Lighthouse
//! assertion) for the default menu, with hooks to tamper with any part — which is how every
//! attack in docs/11 is expressed in tests.
use anchor_lang::{InstructionData, ToAccountMetas};
use ed25519_dalek::{Signer as _, SigningKey};
use solana_instruction::{AccountMeta, Instruction};
use solana_keypair::Keypair;
use solana_pubkey::Pubkey;
use solana_signer::Signer;
use tally::instructions::pay_offer::{PayOfferArgs, PowSolution};
use tally_core::{
    ed25519::build_ix_data,
    header::{build_header, HeaderFields},
    menu::{compute_floors, leaf_hash, Item},
    merkle::MerkleTree,
};

use crate::{pda, Env, TxResult, RECEIPT_SEED, SPL_TOKEN, SYSTEM};

pub const ED25519_PROGRAM: Pubkey = solana_sdk_ids::ed25519_program::ID;

/// The demo café menu (same shape as fixtures/menu-v1.json).
pub fn default_items() -> Vec<Item> {
    let raw: [(u16, u64); 8] = [
        (3, 4_500_000),
        (3, 5_900_000),
        (3, 5_200_000),
        (3, 18_000_000),
        (0, 3_000_000),
        (0, 3_800_000),
        (4, 2_750_000),
        (4, 9_000_000),
    ];
    let mut items: Vec<Item> = raw
        .iter()
        .enumerate()
        .map(|(i, (c, p))| Item {
            id: tally_core::hash::sha256v(&[format!("tally-item-{i}").as_bytes()])[..16]
                .try_into()
                .unwrap(),
            category: *c,
            price: *p,
            floor: 0,
        })
        .collect();
    compute_floors(&mut items);
    items
}

type ArgsHook = Box<dyn FnOnce(&mut PayOfferArgs)>;
type IxsHook = Box<dyn FnOnce(&mut Vec<Instruction>)>;

pub struct PayBuilder<'a> {
    env: &'a mut Env,
    item: usize,
    items: Vec<Item>,
    owner_signs: bool,
    window: u64,
    issued_at: Option<u64>,
    menu_nonce: Option<u64>,
    menu_signer: Option<SigningKey>,
    agent: Option<Keypair>,
    payout: Option<Pubkey>,
    merchant: Option<(Pubkey, Pubkey)>,
    pow: Option<PowSolution>,
    lighthouse: Option<bool>,
    lighthouse_delta: i64,
    extra_signers: Vec<Keypair>,
    args_hooks: Vec<ArgsHook>,
    ixs_hooks: Vec<IxsHook>,
}

impl Env {
    pub fn pay(&mut self) -> PayBuilder<'_> {
        PayBuilder {
            env: self,
            item: 0,
            items: default_items(),
            owner_signs: false,
            window: 100,
            issued_at: None,
            menu_nonce: None,
            menu_signer: None,
            agent: None,
            payout: None,
            merchant: None,
            pow: None,
            lighthouse: None,
            lighthouse_delta: 0,
            extra_signers: vec![],
            args_hooks: vec![],
            ixs_hooks: vec![],
        }
    }

    pub fn receipt_address(&self, menu_nonce: u64, item_id: &[u8; 16]) -> Pubkey {
        pda(&[
            RECEIPT_SEED,
            self.capsule.as_ref(),
            &menu_nonce.to_le_bytes(),
            item_id,
        ])
    }
}

impl<'a> PayBuilder<'a> {
    pub fn item(mut self, i: usize) -> Self {
        self.item = i;
        self
    }
    pub fn items(mut self, items: Vec<Item>) -> Self {
        self.items = items;
        self
    }
    pub fn owner_signs(mut self) -> Self {
        self.owner_signs = true;
        self
    }
    pub fn window(mut self, w: u64) -> Self {
        self.window = w;
        self
    }
    pub fn issued_at(mut self, slot: u64) -> Self {
        self.issued_at = Some(slot);
        self
    }
    pub fn nonce(mut self, n: u64) -> Self {
        self.menu_nonce = Some(n);
        self
    }
    pub fn menu_signer(mut self, k: SigningKey) -> Self {
        self.menu_signer = Some(k);
        self
    }
    pub fn agent(mut self, k: Keypair) -> Self {
        self.agent = Some(k);
        self
    }
    pub fn payout(mut self, p: Pubkey) -> Self {
        self.payout = Some(p);
        self
    }
    /// Pay through a different merchant registry entry `(merchant_pda, payout)`.
    pub fn merchant(mut self, m: (Pubkey, Pubkey)) -> Self {
        self.merchant = Some(m);
        self
    }
    pub fn pow(mut self, p: PowSolution) -> Self {
        self.pow = Some(p);
        self
    }
    pub fn lighthouse(mut self, on: bool) -> Self {
        self.lighthouse = Some(on);
        self
    }
    pub fn lighthouse_amount_off_by(mut self, d: i64) -> Self {
        self.lighthouse_delta = d;
        self.lighthouse = Some(true);
        self
    }
    pub fn extra_signer(mut self, k: Keypair) -> Self {
        self.extra_signers.push(k);
        self
    }
    pub fn args(mut self, f: impl FnOnce(&mut PayOfferArgs) + 'static) -> Self {
        self.args_hooks.push(Box::new(f));
        self
    }
    pub fn ixs(mut self, f: impl FnOnce(&mut Vec<Instruction>) + 'static) -> Self {
        self.ixs_hooks.push(Box::new(f));
        self
    }

    /// Fresh nonce per call so successive purchases do not collide on the receipt PDA.
    pub fn fresh_menu(self) -> Self {
        let n = self.env.next_nonce();
        self.nonce(n)
    }

    pub fn send(mut self) -> TxResult {
        let args_hooks = std::mem::take(&mut self.args_hooks);
        let ixs_hooks = std::mem::take(&mut self.ixs_hooks);
        let (ixs, signers) = self.build(args_hooks, ixs_hooks);
        let refs: Vec<&Keypair> = signers.iter().collect();
        self.env.send(&ixs, &refs)
    }

    fn build(
        &self,
        args_hooks: Vec<ArgsHook>,
        ixs_hooks: Vec<IxsHook>,
    ) -> (Vec<Instruction>, Vec<Keypair>) {
        let env = &*self.env;
        let now = env.slot();
        let issued = self.issued_at.unwrap_or(now);
        let nonce = self.menu_nonce.unwrap_or(env.menu_nonce);
        let (merchant, payout) = self.merchant.unwrap_or((env.merchant, env.payout));
        let tree = MerkleTree::new(self.items.iter().map(leaf_hash).collect());
        let it = self.items[self.item];
        let mut args = PayOfferArgs {
            menu_root: tree.root(),
            issued_slot: issued,
            expires_slot: issued + self.window,
            menu_nonce: nonce,
            item_count: self.items.len() as u32,
            item_id: it.id,
            category: it.category,
            price: it.price,
            category_floor: it.floor,
            proof: tree.proof(self.item),
            pow: self.pow.clone(),
        };
        let header = build_header(&HeaderFields {
            program_id: tally::ID.to_bytes(),
            merchant_pda: merchant.to_bytes(),
            menu_root: args.menu_root,
            issued_slot: args.issued_slot,
            expires_slot: args.expires_slot,
            menu_nonce: args.menu_nonce,
            item_count: args.item_count,
        });
        let signer = self
            .menu_signer
            .clone()
            .unwrap_or_else(|| env.merchant_signer.clone());
        let sig = signer.sign(&header).to_bytes();
        let ed_ix = Instruction {
            program_id: ED25519_PROGRAM,
            accounts: vec![],
            data: build_ix_data(&signer.verifying_key().to_bytes(), &sig, &header),
        };

        // args hooks run *after* signing: they model a client tampering with the payload.
        for h in args_hooks {
            h(&mut args);
        }
        let item_id_for_receipt = args.item_id;
        let agent = self
            .agent
            .as_ref()
            .map(|k| k.insecure_clone())
            .unwrap_or_else(|| env.agent.insecure_clone());
        let mut metas = tally::accounts::PayOffer {
            capsule: env.capsule,
            agent: agent.pubkey(),
            owner: env.owner.pubkey(),
            merchant,
            mint: env.usdc,
            vault: env.vault,
            payout: self.payout.unwrap_or(payout),
            receipt: env.receipt_address(args.menu_nonce, &item_id_for_receipt),
            ix_sysvar: solana_sdk_ids::sysvar::instructions::ID,
            slot_hashes: solana_sdk_ids::sysvar::slot_hashes::ID,
            token_program: SPL_TOKEN,
            system_program: SYSTEM,
        }
        .to_account_metas(None);
        if self.owner_signs {
            for m in metas.iter_mut() {
                if m.pubkey == env.owner.pubkey() {
                    *m = AccountMeta::new_readonly(m.pubkey, true);
                }
            }
        }
        let pay_ix = Instruction {
            program_id: tally::ID,
            accounts: metas,
            data: tally::instruction::PayOffer { args: args.clone() }.data(),
        };
        let mut ixs = vec![ed_ix, pay_ix];
        let want_lighthouse = self
            .lighthouse
            .unwrap_or_else(|| env.capsule_flags() & crate::FLAG_REQUIRE_LIGHTHOUSE != 0);
        if want_lighthouse {
            let expected = (env.token_balance(&env.vault) as i64 - args.price as i64
                + self.lighthouse_delta) as u64;
            ixs.push(crate::lighthouse::assert_token_amount_eq(
                env.vault, expected,
            ));
        }
        for h in ixs_hooks {
            h(&mut ixs);
        }
        let mut signers = vec![agent];
        if self.owner_signs {
            signers.push(env.owner.insecure_clone());
        }
        signers.extend(self.extra_signers.iter().map(|k| k.insecure_clone()));
        (ixs, signers)
    }
}

// ------------------------------------------------------------ attack helpers

pub fn patch_u16(data: &mut [u8], at: usize, v: u16) {
    data[at..at + 2].copy_from_slice(&v.to_le_bytes());
}

/// The real offset-hijack exploit. The precompile is told (`message_instruction_index = 1`) to
/// verify the *genuine* signed header, which the attacker appends to pay_offer's data. At the
/// same offset inside the precompile's *own* data sits a spoofed header (nonce + 1) that matches
/// the tampered args. A program that ignores the instruction index would read the spoofed bytes,
/// see them equal its rebuilt header, and accept. Use together with `.args(|a| a.menu_nonce += 1)`.
pub fn cross_ix_exploit(ixs: &mut Vec<Instruction>) {
    let genuine = ixs[0].data[112..112 + 144].to_vec();
    let mut spoof = genuine.clone();
    let n = u64::from_le_bytes(spoof[128..136].try_into().unwrap()) + 1;
    spoof[128..136].copy_from_slice(&n.to_le_bytes());
    let off = ixs[1].data.len();
    ixs[1].data.extend_from_slice(&genuine);
    let d = &mut ixs[0].data;
    d.truncate(112);
    d.resize(off.max(112), 0);
    d.extend_from_slice(&spoof);
    patch_u16(d, 10, off as u16);
    patch_u16(d, 14, 1);
}

/// Rewrite the precompile instruction to carry two valid signatures (header + a junk message).
pub fn make_two_sig_ix(ix: &mut Instruction, extra: &SigningKey) {
    let d = ix.data.clone();
    let (pk, sig, msg) = (&d[16..48], &d[48..112], &d[112..]);
    let junk = b"second signature";
    let sig2 = extra.sign(junk).to_bytes();
    let pk2 = extra.verifying_key().to_bytes();
    let base: u16 = 2 + 14 * 2;
    let (pk1o, s1o, m1o) = (base, base + 32, base + 96);
    let (pk2o, s2o) = (m1o + msg.len() as u16, m1o + msg.len() as u16 + 32);
    let m2o = s2o + 64;
    let mut out = vec![2u8, 0];
    for v in [
        s1o,
        u16::MAX,
        pk1o,
        u16::MAX,
        m1o,
        msg.len() as u16,
        u16::MAX,
        s2o,
        u16::MAX,
        pk2o,
        u16::MAX,
        m2o,
        junk.len() as u16,
        u16::MAX,
    ] {
        out.extend_from_slice(&v.to_le_bytes());
    }
    out.extend_from_slice(pk);
    out.extend_from_slice(sig);
    out.extend_from_slice(msg);
    out.extend_from_slice(&pk2);
    out.extend_from_slice(&sig2);
    out.extend_from_slice(junk);
    ix.data = out;
}

// ------------------------------------------------------------ proof of work
impl Env {
    /// Most recent SlotHashes entry, as the program reads it.
    pub fn recent_slot_hash(&self) -> [u8; 32] {
        let sh = self.svm.get_sysvar::<solana_slot_hashes::SlotHashes>();
        sh.first().map(|(_, h)| h.to_bytes()).unwrap_or([0; 32])
    }

    /// Solve the hashcash challenge for `capsule` and `menu_nonce` (client-side work).
    pub fn solve_pow_for(&self, capsule: Pubkey, menu_nonce: u64, difficulty: u8) -> PowSolution {
        let challenge = tally_core::pow::pow_challenge(
            menu_nonce,
            &capsule.to_bytes(),
            &self.recent_slot_hash(),
        );
        PowSolution {
            nonce: tally_core::pow::solve_hashcash(&challenge, difficulty),
        }
    }
}
