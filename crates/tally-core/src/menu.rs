//! Catalog items, leaf hashing and category-floor rules (docs/07, "Data model" and "Floor rule").

use crate::{hash::sha256v, LEAF_PREFIX};

/// One catalog entry as committed in the Merkle tree. Display fields (name, description)
/// are deliberately absent: they never reach the leaf or the agent.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Item {
    pub id: [u8; 16],
    pub category: u16,
    /// Price in micro-USDC.
    pub price: u64,
    /// Merchant-certified cheapest price in `category`.
    pub floor: u64,
}

/// `SHA256(0x00 || id || category_le16 || price_le64 || floor_le64)`.
pub fn leaf_hash(i: &Item) -> [u8; 32] {
    sha256v(&[
        &[LEAF_PREFIX],
        &i.id,
        &i.category.to_le_bytes(),
        &i.price.to_le_bytes(),
        &i.floor.to_le_bytes(),
    ])
}

/// Set every item's floor to the minimum price among items of its category.
pub fn compute_floors(items: &mut [Item]) {
    for idx in 0..items.len() {
        let cat = items[idx].category;
        let min = items
            .iter()
            .filter(|o| o.category == cat)
            .map(|o| o.price)
            .min()
            .unwrap_or(items[idx].price);
        items[idx].floor = min;
    }
}

/// Return `(cheap, claimed)` when an item is priced below the floor another item in the same
/// category certifies — a self-contradiction that `prove_floor_fraud` can slash.
pub fn find_floor_contradiction(items: &[Item]) -> Option<(usize, usize)> {
    for (c, cheap) in items.iter().enumerate() {
        for (k, claimed) in items.iter().enumerate() {
            if cheap.category == claimed.category && cheap.price < claimed.floor {
                return Some((c, k));
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::vec::Vec;

    fn it(n: u8, cat: u16, price: u64) -> Item {
        Item {
            id: [n; 16],
            category: cat,
            price,
            floor: 0,
        }
    }

    #[test]
    fn leaf_preimage_is_prefix_id_cat_price_floor() {
        let i = Item {
            id: [9; 16],
            category: 0x0103,
            price: 4_500_000,
            floor: 3_500_000,
        };
        let mut pre = alloc::vec![0x00u8];
        pre.extend_from_slice(&[9; 16]);
        pre.extend_from_slice(&0x0103u16.to_le_bytes());
        pre.extend_from_slice(&4_500_000u64.to_le_bytes());
        pre.extend_from_slice(&3_500_000u64.to_le_bytes());
        assert_eq!(pre.len(), 35);
        assert_eq!(leaf_hash(&i), crate::hash::sha256v(&[&pre]));
    }

    #[test]
    fn changing_price_changes_leaf() {
        let a = Item {
            id: [1; 16],
            category: 3,
            price: 450,
            floor: 450,
        };
        let b = Item { price: 990, ..a };
        assert_ne!(leaf_hash(&a), leaf_hash(&b));
    }

    #[test]
    fn floors_are_min_per_category() {
        let mut v = alloc::vec![it(1, 3, 590), it(2, 3, 450), it(3, 0, 300), it(4, 3, 520)];
        compute_floors(&mut v);
        assert_eq!(
            v.iter().map(|i| i.floor).collect::<Vec<_>>(),
            [450, 450, 300, 450]
        );
        assert_eq!(find_floor_contradiction(&v), None);
    }

    #[test]
    fn contradiction_detected() {
        let mut v = alloc::vec![it(1, 3, 590), it(2, 3, 450)];
        compute_floors(&mut v);
        v[0].floor = 590; // merchant claims the large latte is the cheapest coffee
        assert_eq!(find_floor_contradiction(&v), Some((1, 0)));
    }
}
