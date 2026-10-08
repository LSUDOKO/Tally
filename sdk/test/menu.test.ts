// Byte-exact parity with crates/tally-core, pinned by fixtures/menu-v1.json.
import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { getBase58Encoder } from '@solana/kit';
import {
  MerkleTree,
  buildHeader,
  computeFloors,
  ed25519IxData,
  findFloorContradiction,
  leafHash,
  powChallenge,
  verifyHashcash,
  verifyProof,
  type Item,
} from '../src/menu';

const fx = JSON.parse(readFileSync(new URL('../../fixtures/menu-v1.json', import.meta.url), 'utf8'));
const hex = (b: Uint8Array) => Buffer.from(b).toString('hex');
const unhex = (s: string) => new Uint8Array(Buffer.from(s, 'hex'));
const b58 = (s: string) => new Uint8Array(getBase58Encoder().encode(s));
const itemsOf = (arr: any[]): Item[] =>
  arr.map((x) => ({ id: unhex(x.id_hex), category: x.category, price: BigInt(x.price), floor: BigInt(x.floor) }));
const headerOf = (root: Uint8Array, hf: any) =>
  buildHeader({
    programId: b58(fx.program_id),
    merchantPda: b58(fx.merchant_pda),
    menuRoot: root,
    issuedSlot: BigInt(hf.issued_slot),
    expiresSlot: BigInt(hf.expires_slot),
    menuNonce: BigInt(hf.menu_nonce),
    itemCount: hf.item_count,
  });

describe('menu parity with tally-core', () => {
  it('reproduces floors, leaves, proofs, root and header', () => {
    const items = itemsOf(fx.items);
    const recomputed = computeFloors(items.map((i) => ({ ...i, floor: 0n })));
    expect(recomputed.map((i) => i.floor)).toEqual(items.map((i) => i.floor));
    const leaves = items.map(leafHash);
    const tree = new MerkleTree(leaves);
    expect(hex(tree.root())).toBe(fx.root_hex);
    fx.items.forEach((x: any, i: number) => {
      expect(hex(leaves[i])).toBe(x.leaf_hex);
      expect(tree.proof(i).map(hex)).toEqual(x.proof_hex);
      expect(verifyProof(leaves[i], tree.proof(i), tree.root())).toBe(true);
    });
    const header = headerOf(tree.root(), fx.header_fields);
    expect(header.length).toBe(144);
    expect(hex(header)).toBe(fx.header_hex);
  });

  it('builds the canonical ed25519 precompile data', () => {
    const data = ed25519IxData(b58(fx.merchant_signing_pubkey), unhex(fx.signature_hex), unhex(fx.header_hex));
    expect(hex(data)).toBe(fx.ed25519_ix_data_hex);
  });

  it('detects the fraud fixture contradiction', () => {
    expect(findFloorContradiction(itemsOf(fx.fraud.items))).toEqual([fx.fraud.cheap_index, fx.fraud.claimed_index]);
    expect(findFloorContradiction(itemsOf(fx.items))).toBeNull();
  });

  it('reproduces the proof-of-work challenge and accepts the fixture nonce', () => {
    const c = powChallenge(BigInt(fx.pow.menu_nonce), b58(fx.pow.capsule), unhex(fx.pow.slot_hash_hex));
    expect(hex(c)).toBe(fx.pow.challenge_hex);
    expect(verifyHashcash(c, unhex(fx.pow.nonce_hex), fx.pow.difficulty)).toBe(true);
  });

  it('round-trips proofs for menus of 1..17 items (odd-node promotion)', () => {
    for (let n = 1; n <= 17; n++) {
      const leaves = Array.from({ length: n }, (_, i) => leafHash({ id: new Uint8Array(16).fill(i + 1), category: 3, price: 1n, floor: 1n }));
      const t = new MerkleTree(leaves);
      leaves.forEach((l, i) => expect(verifyProof(l, t.proof(i), t.root())).toBe(true));
    }
  });
});
