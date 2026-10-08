//! Sorted-pair, domain-separated Merkle tree over catalog leaves (docs/07, "Leaf and node hashing").

use crate::{hash::sha256v, MAX_PROOF_DEPTH, NODE_PREFIX};
use alloc::vec::Vec;

/// `SHA256(0x01 || min(a,b) || max(a,b))`: sorted so proofs need no direction bits.
pub fn node_hash(a: &[u8; 32], b: &[u8; 32]) -> [u8; 32] {
    let (x, y) = if a <= b { (a, b) } else { (b, a) };
    sha256v(&[&[NODE_PREFIX], x, y])
}

/// A full tree kept level by level so any leaf's proof can be produced.
/// An odd node at the end of a level is promoted unchanged.
pub struct MerkleTree {
    levels: Vec<Vec<[u8; 32]>>,
}

impl MerkleTree {
    /// Build from leaf hashes. Panics on an empty slice: a menu always has at least one item.
    pub fn new(leaves: Vec<[u8; 32]>) -> Self {
        assert!(!leaves.is_empty(), "empty menu");
        let mut levels = alloc::vec![leaves];
        loop {
            let prev = &levels[levels.len() - 1];
            if prev.len() == 1 {
                break;
            }
            let next = prev
                .chunks(2)
                .map(|p| {
                    if p.len() == 2 {
                        node_hash(&p[0], &p[1])
                    } else {
                        p[0]
                    }
                })
                .collect();
            levels.push(next);
        }
        Self { levels }
    }

    pub fn root(&self) -> [u8; 32] {
        self.levels[self.levels.len() - 1][0]
    }

    /// Sibling hashes from leaf to root; promoted levels contribute no sibling.
    pub fn proof(&self, mut index: usize) -> Vec<[u8; 32]> {
        let mut out = Vec::new();
        for level in &self.levels[..self.levels.len() - 1] {
            let sib = index ^ 1;
            if sib < level.len() {
                out.push(level[sib]);
            }
            index /= 2;
        }
        out
    }
}

/// Fold `proof` onto `leaf` and compare with `root`. Proofs deeper than 16 are rejected
/// to bound on-chain compute.
pub fn verify_proof(leaf: [u8; 32], proof: &[[u8; 32]], root: [u8; 32]) -> bool {
    if proof.len() > MAX_PROOF_DEPTH {
        return false;
    }
    proof.iter().fold(leaf, |node, sib| node_hash(&node, sib)) == root
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::vec::Vec;

    fn leaves(n: usize) -> Vec<[u8; 32]> {
        (0..n)
            .map(|i| crate::hash::sha256v(&[&[i as u8]]))
            .collect()
    }

    #[test]
    fn single_leaf_root_is_leaf_and_proof_empty() {
        let t = MerkleTree::new(leaves(1));
        assert_eq!(t.root(), leaves(1)[0]);
        assert!(t.proof(0).is_empty());
        assert!(verify_proof(leaves(1)[0], &[], t.root()));
    }

    #[test]
    fn every_leaf_verifies_for_sizes_1_to_17() {
        for n in 1..=17 {
            let l = leaves(n);
            let t = MerkleTree::new(l.clone());
            for (i, leaf) in l.iter().enumerate() {
                assert!(verify_proof(*leaf, &t.proof(i), t.root()), "n={n} i={i}");
            }
        }
    }

    #[test]
    fn wrong_leaf_fails() {
        let l = leaves(5);
        let t = MerkleTree::new(l.clone());
        assert!(!verify_proof(l[1], &t.proof(0), t.root()));
    }

    #[test]
    fn internal_node_differs_from_every_leaf() {
        let l = leaves(4);
        let t = MerkleTree::new(l.clone());
        let n01 = node_hash(&l[0], &l[1]);
        let n23 = node_hash(&l[2], &l[3]);
        // An internal node with a 1-step proof reaches the root, which is why the program
        // only ever hashes (id, category, price, floor) into a leaf itself.
        assert!(verify_proof(n01, &[n23], t.root()));
        assert!(l.iter().all(|x| *x != n01));
    }

    #[test]
    fn proof_longer_than_16_rejected() {
        let p = alloc::vec![[0u8; 32]; 17];
        assert!(!verify_proof([0; 32], &p, [0; 32]));
    }
}
