// Byte-exact TypeScript port of crates/tally-core (header, leaves, Merkle tree, Ed25519
// precompile data, floors, proof-of-work). Every layout is pinned by fixtures/menu-v1.json.
import { sha256 } from '@noble/hashes/sha2.js';

export const DOMAIN = new TextEncoder().encode('TALLY_MENU_V1\0\0\0');
export const HEADER_LEN = 144;
export const MAX_AUTONOMOUS_WINDOW = 150n;
export const MAX_PROOF_DEPTH = 16;
const LEAF_PREFIX = 0x00;
const NODE_PREFIX = 0x01;

export interface Item {
  /** 16-byte item id. */
  id: Uint8Array;
  category: number;
  /** Price in micro-USDC. */
  price: bigint;
  /** Merchant-certified cheapest price in `category`. */
  floor: bigint;
}

export interface HeaderFields {
  programId: Uint8Array;
  merchantPda: Uint8Array;
  menuRoot: Uint8Array;
  issuedSlot: bigint;
  expiresSlot: bigint;
  menuNonce: bigint;
  itemCount: number;
}

function concat(...parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let o = 0;
  for (const p of parts) {
    out.set(p, o);
    o += p.length;
  }
  return out;
}

export function sha256v(...parts: Uint8Array[]): Uint8Array {
  return sha256(concat(...parts));
}

export function u16le(v: number): Uint8Array {
  const b = new Uint8Array(2);
  new DataView(b.buffer).setUint16(0, v, true);
  return b;
}

export function u32le(v: number): Uint8Array {
  const b = new Uint8Array(4);
  new DataView(b.buffer).setUint32(0, v, true);
  return b;
}

export function u64le(v: bigint): Uint8Array {
  const b = new Uint8Array(8);
  new DataView(b.buffer).setBigUint64(0, v, true);
  return b;
}

/** The canonical 144-byte header a merchant signs (docs/07). */
export function buildHeader(f: HeaderFields): Uint8Array {
  const h = concat(
    DOMAIN,
    f.programId,
    f.merchantPda,
    f.menuRoot,
    u64le(f.issuedSlot),
    u64le(f.expiresSlot),
    u64le(f.menuNonce),
    u32le(f.itemCount),
    new Uint8Array([0 /* hash_alg sha256 */, 1 /* header_version */, 0, 0]),
  );
  if (h.length !== HEADER_LEN) throw new Error(`header is ${h.length} bytes`);
  return h;
}

/** `SHA256(0x00 || id || category_le16 || price_le64 || floor_le64)`. */
export function leafHash(i: Item): Uint8Array {
  return sha256v(new Uint8Array([LEAF_PREFIX]), i.id, u16le(i.category), u64le(i.price), u64le(i.floor));
}

function lessOrEqual(a: Uint8Array, b: Uint8Array): boolean {
  for (let k = 0; k < a.length; k++) if (a[k] !== b[k]) return a[k] < b[k];
  return true;
}

/** Sorted-pair node: `SHA256(0x01 || min || max)`. */
export function nodeHash(a: Uint8Array, b: Uint8Array): Uint8Array {
  return lessOrEqual(a, b) ? sha256v(new Uint8Array([NODE_PREFIX]), a, b) : sha256v(new Uint8Array([NODE_PREFIX]), b, a);
}

export class MerkleTree {
  private readonly levels: Uint8Array[][];

  constructor(leaves: Uint8Array[]) {
    if (leaves.length === 0) throw new Error('empty menu');
    this.levels = [leaves];
    while (this.levels[this.levels.length - 1].length > 1) {
      const prev = this.levels[this.levels.length - 1];
      const next: Uint8Array[] = [];
      for (let i = 0; i < prev.length; i += 2) next.push(i + 1 < prev.length ? nodeHash(prev[i], prev[i + 1]) : prev[i]);
      this.levels.push(next);
    }
  }

  root(): Uint8Array {
    return this.levels[this.levels.length - 1][0];
  }

  proof(index: number): Uint8Array[] {
    const out: Uint8Array[] = [];
    for (const level of this.levels.slice(0, -1)) {
      const sib = index ^ 1;
      if (sib < level.length) out.push(level[sib]);
      index = Math.floor(index / 2);
    }
    return out;
  }
}

export function verifyProof(leaf: Uint8Array, proof: Uint8Array[], root: Uint8Array): boolean {
  if (proof.length > MAX_PROOF_DEPTH) return false;
  const node = proof.reduce((n, s) => nodeHash(n, s), leaf);
  return node.length === root.length && node.every((b, k) => b === root[k]);
}

/** Return items with each floor set to the minimum price in its category. */
export function computeFloors(items: Item[]): Item[] {
  return items.map((i) => ({
    ...i,
    floor: items.filter((o) => o.category === i.category).reduce((m, o) => (o.price < m ? o.price : m), i.price),
  }));
}

/** `[cheap, claimed]` when a leaf is priced below another same-category leaf's certified floor. */
export function findFloorContradiction(items: Item[]): [number, number] | null {
  for (let c = 0; c < items.length; c++)
    for (let k = 0; k < items.length; k++)
      if (items[c].category === items[k].category && items[c].price < items[k].floor) return [c, k];
  return null;
}

/** Single-signature Ed25519 precompile data with every argument inside the instruction. */
export function ed25519IxData(pubkey: Uint8Array, signature: Uint8Array, message: Uint8Array): Uint8Array {
  const PK = 16;
  const SIG = 48;
  const MSG = 112;
  const MAX = 0xffff;
  const header = concat(
    new Uint8Array([1, 0]),
    ...[SIG, MAX, PK, MAX, MSG, message.length, MAX].map(u16le),
  );
  return concat(header, pubkey, signature, message);
}

/** `SHA256("TALLY_POW_V1" || menu_nonce_le64 || capsule || slot_hash)`. */
export function powChallenge(menuNonce: bigint, capsule: Uint8Array, slotHash: Uint8Array): Uint8Array {
  return sha256v(new TextEncoder().encode('TALLY_POW_V1'), u64le(menuNonce), capsule, slotHash);
}

export function leadingZeroBits(h: Uint8Array): number {
  let n = 0;
  for (const b of h) {
    if (b === 0) {
      n += 8;
      continue;
    }
    return n + Math.clz32(b) - 24;
  }
  return n;
}

export function verifyHashcash(challenge: Uint8Array, nonce: Uint8Array, difficulty: number): boolean {
  return leadingZeroBits(sha256v(challenge, nonce)) >= difficulty;
}

export function solveHashcash(challenge: Uint8Array, difficulty: number): Uint8Array {
  for (let i = 0n; ; i++) {
    const nonce = u64le(i);
    if (verifyHashcash(challenge, nonce, difficulty)) return nonce;
  }
}
