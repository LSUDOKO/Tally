//! `fixtures/menu-v1.json` is the cross-language contract (Rust program, TS SDK, Kotlin app).
//! These tests recompute every value from `tally-core` and fail on any drift.
use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use serde_json::Value;
use tally_core::{ed25519, header::*, menu::*, merkle::*, pow};

fn fixture() -> Value {
    serde_json::from_str(include_str!("../../../fixtures/menu-v1.json")).unwrap()
}
fn b58(s: &Value) -> [u8; 32] {
    bs58::decode(s.as_str().unwrap()).into_vec().unwrap().try_into().unwrap()
}
fn hx(s: &Value) -> Vec<u8> {
    hex::decode(s.as_str().unwrap()).unwrap()
}
fn items_of(v: &Value) -> Vec<Item> {
    v.as_array().unwrap().iter().map(|x| Item {
        id: hx(&x["id_hex"]).try_into().unwrap(),
        category: x["category"].as_u64().unwrap() as u16,
        price: x["price"].as_u64().unwrap(),
        floor: x["floor"].as_u64().unwrap(),
    }).collect()
}
fn header_of(j: &Value, root: [u8; 32], hf: &Value) -> [u8; 144] {
    build_header(&HeaderFields {
        program_id: b58(&j["program_id"]),
        merchant_pda: b58(&j["merchant_pda"]),
        menu_root: root,
        issued_slot: hf["issued_slot"].as_u64().unwrap(),
        expires_slot: hf["expires_slot"].as_u64().unwrap(),
        menu_nonce: hf["menu_nonce"].as_u64().unwrap(),
        item_count: hf["item_count"].as_u64().unwrap() as u32,
    })
}

#[test]
fn program_id_matches_core_constant() {
    assert_eq!(fixture()["program_id"].as_str().unwrap(), tally_core::PROGRAM_ID_STR);
}

#[test]
fn honest_menu_reproduces() {
    let j = fixture();
    let items = items_of(&j["items"]);
    let mut recomputed = items.clone();
    compute_floors(&mut recomputed);
    assert_eq!(items, recomputed, "floors must equal per-category minimum");
    let leaves: Vec<[u8; 32]> = items.iter().map(leaf_hash).collect();
    let tree = MerkleTree::new(leaves.clone());
    assert_eq!(hex::encode(tree.root()), j["root_hex"].as_str().unwrap());
    for (i, x) in j["items"].as_array().unwrap().iter().enumerate() {
        assert_eq!(hex::encode(leaves[i]), x["leaf_hex"].as_str().unwrap());
        let proof: Vec<[u8; 32]> = x["proof_hex"].as_array().unwrap().iter().map(|p| hx(p).try_into().unwrap()).collect();
        assert_eq!(proof, tree.proof(i));
        assert!(verify_proof(leaves[i], &proof, tree.root()));
    }
    let h = header_of(&j, tree.root(), &j["header_fields"]);
    assert_eq!(hex::encode(h), j["header_hex"].as_str().unwrap());
}

#[test]
fn signature_and_precompile_data_verify() {
    let j = fixture();
    let pk = VerifyingKey::from_bytes(&b58(&j["merchant_signing_pubkey"])).unwrap();
    let sig = Signature::from_slice(&hx(&j["signature_hex"])).unwrap();
    let header = hx(&j["header_hex"]);
    pk.verify(&header, &sig).unwrap();
    let data = ed25519::build_ix_data(pk.as_bytes(), &sig.to_bytes(), &header);
    assert_eq!(hex::encode(&data), j["ed25519_ix_data_hex"].as_str().unwrap());
    assert_eq!(ed25519::parse_single(&data, 144).unwrap().message, &header[..]);
}

#[test]
fn fraud_menu_is_contradictory_and_signed() {
    let j = fixture();
    let f = &j["fraud"];
    let items = items_of(&f["items"]);
    let (c, k) = find_floor_contradiction(&items).expect("fraud menu must contradict itself");
    assert_eq!((c as u64, k as u64), (f["cheap_index"].as_u64().unwrap(), f["claimed_index"].as_u64().unwrap()));
    let tree = MerkleTree::new(items.iter().map(leaf_hash).collect());
    assert_eq!(hex::encode(tree.root()), f["root_hex"].as_str().unwrap());
    let h = header_of(&j, tree.root(), &f["header_fields"]);
    assert_eq!(hex::encode(h), f["header_hex"].as_str().unwrap());
    let pk = VerifyingKey::from_bytes(&b58(&j["merchant_signing_pubkey"])).unwrap();
    pk.verify(&h, &Signature::from_slice(&hx(&f["signature_hex"])).unwrap()).unwrap();
}

#[test]
fn pow_vector_verifies() {
    let p = &fixture()["pow"];
    let c = pow::pow_challenge(p["menu_nonce"].as_u64().unwrap(), &b58(&p["capsule"]), &hx(&p["slot_hash_hex"]).try_into().unwrap());
    assert_eq!(hex::encode(c), p["challenge_hex"].as_str().unwrap());
    let n: [u8; 8] = hx(&p["nonce_hex"]).try_into().unwrap();
    assert!(pow::verify_hashcash(&c, &n, p["difficulty"].as_u64().unwrap() as u8));
}
