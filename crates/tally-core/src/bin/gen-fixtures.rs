//! Generates `fixtures/menu-v1.json`: the byte-exact contract shared by the Rust program tests,
//! the TypeScript SDK and the Kotlin app. Keys are deterministic and for TESTS ONLY.
//!
//! Usage: `cargo run -p tally-core --features fixtures --bin gen-fixtures -- fixtures/menu-v1.json`
use std::str::FromStr;

use ed25519_dalek::{Signer, SigningKey};
use serde_json::{json, Value};
use solana_pubkey::Pubkey;
use tally_core::{ed25519, hash::sha256v, header::*, menu::*, merkle::MerkleTree, pow, PROGRAM_ID_STR};

const NAMES: [(&str, u16, u64); 8] = [
    ("Oat latte (regular)", 3, 4_500_000),
    ("Oat latte (large)", 3, 5_900_000),
    ("Cappuccino", 3, 5_200_000),
    ("Reserve roast", 3, 18_000_000),
    ("Croissant", 0, 3_000_000),
    ("Bagel", 0, 3_800_000),
    ("Metro single", 4, 2_750_000),
    ("Day pass", 4, 9_000_000),
];

fn item_id(i: usize) -> [u8; 16] {
    sha256v(&[format!("tally-item-{i}").as_bytes()])[..16].try_into().unwrap()
}

fn menu_json(items: &[Item], names: &[&str], tree: &MerkleTree) -> Vec<Value> {
    items.iter().enumerate().map(|(i, it)| json!({
        "id_hex": hex::encode(it.id), "category": it.category, "price": it.price, "floor": it.floor,
        "name": names[i], "leaf_hex": hex::encode(leaf_hash(it)),
        "proof_hex": tree.proof(i).iter().map(hex::encode).collect::<Vec<_>>(),
    })).collect()
}

fn main() {
    let out = std::env::args().nth(1).expect("output path");
    let program_id = Pubkey::from_str(PROGRAM_ID_STR).unwrap();
    let signer = SigningKey::from_bytes(&[7u8; 32]);
    let authority = Pubkey::new_from_array(SigningKey::from_bytes(&[8u8; 32]).verifying_key().to_bytes());
    let (merchant_pda, _) = Pubkey::find_program_address(&[b"merchant", authority.as_ref()], &program_id);

    // Honest menu.
    let mut items: Vec<Item> = NAMES.iter().enumerate()
        .map(|(i, (_, c, p))| Item { id: item_id(i), category: *c, price: *p, floor: 0 }).collect();
    compute_floors(&mut items);
    let names: Vec<&str> = NAMES.iter().map(|n| n.0).collect();
    let tree = MerkleTree::new(items.iter().map(leaf_hash).collect());
    let fields = HeaderFields {
        program_id: program_id.to_bytes(), merchant_pda: merchant_pda.to_bytes(), menu_root: tree.root(),
        issued_slot: 1000, expires_slot: 1100, menu_nonce: 42, item_count: items.len() as u32,
    };
    let header = build_header(&fields);
    let sig = signer.sign(&header).to_bytes();

    // Fraud menu: a cheaper drip coffee appears, but coffee floors still claim 4.50.
    let mut fraud = items.clone();
    fraud.insert(0, Item { id: item_id(100), category: 3, price: 3_250_000, floor: 4_500_000 });
    let mut fraud_names = vec!["Drip coffee"];
    fraud_names.extend(names.iter());
    let (cheap, claimed) = find_floor_contradiction(&fraud).expect("contradiction");
    let ftree = MerkleTree::new(fraud.iter().map(leaf_hash).collect());
    let ffields = HeaderFields { menu_root: ftree.root(), menu_nonce: 43, item_count: fraud.len() as u32, ..fields };
    let fheader = build_header(&ffields);
    let fsig = signer.sign(&fheader).to_bytes();

    // PoW vector.
    let capsule = Pubkey::new_from_array(sha256v(&[b"fixture-capsule"]));
    let slot_hash = sha256v(&[b"fixture-slot-hash"]);
    let challenge = pow::pow_challenge(42, &capsule.to_bytes(), &slot_hash);
    let nonce = pow::solve_hashcash(&challenge, 12);

    let doc = json!({
        "version": 1,
        "warning": "TEST KEYS ONLY - never use these keys on mainnet",
        "program_id": program_id.to_string(),
        "merchant_authority": authority.to_string(),
        "merchant_authority_seed_hex": hex::encode([8u8; 32]),
        "merchant_pda": merchant_pda.to_string(),
        "merchant_signing_seed_hex": hex::encode([7u8; 32]),
        "merchant_signing_pubkey": bs58::encode(signer.verifying_key().to_bytes()).into_string(),
        "header_fields": {"issued_slot": 1000, "expires_slot": 1100, "menu_nonce": 42, "item_count": items.len()},
        "items": menu_json(&items, &names, &tree),
        "root_hex": hex::encode(tree.root()),
        "header_hex": hex::encode(header),
        "signature_hex": hex::encode(sig),
        "ed25519_ix_data_hex": hex::encode(ed25519::build_ix_data(&signer.verifying_key().to_bytes(), &sig, &header)),
        "fraud": {
            "header_fields": {"issued_slot": 1000, "expires_slot": 1100, "menu_nonce": 43, "item_count": fraud.len()},
            "items": menu_json(&fraud, &fraud_names, &ftree),
            "root_hex": hex::encode(ftree.root()), "header_hex": hex::encode(fheader), "signature_hex": hex::encode(fsig),
            "cheap_index": cheap, "claimed_index": claimed,
        },
        "pow": {
            "menu_nonce": 42, "capsule": capsule.to_string(), "slot_hash_hex": hex::encode(slot_hash),
            "challenge_hex": hex::encode(challenge), "difficulty": 12, "nonce_hex": hex::encode(nonce),
        },
    });
    std::fs::write(&out, serde_json::to_string_pretty(&doc).unwrap() + "\n").unwrap();
    eprintln!("wrote {out}");
}
