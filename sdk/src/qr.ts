// The signed-menu QR/NFC wire format (docs/07), byte-compatible with the Android app's Payload:
// "TALLY:V1:" + Base45(CBOR map). Only the CBOR subset the format needs is implemented: unsigned
// integers, byte strings, text strings, arrays and text-keyed maps.
import { getAddressDecoder, getAddressEncoder, type Address } from '@solana/kit';
import type { Item } from './menu';
import type { SignedMenu } from './pay';
import { menuHeader } from './pay';

export const QR_PREFIX = 'TALLY:V1:';
const B45 = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:';

/** A signed menu plus the display-only text the customer sees (never sent to the agent). */
export interface MenuPayload {
  menu: SignedMenu;
  names: string[];
  descriptions: (string | null)[];
}

export function base45Encode(input: Uint8Array): string {
  let out = '';
  let i = 0;
  for (; i + 1 < input.length; i += 2) {
    let x = (input[i] << 8) + input[i + 1];
    for (let k = 0; k < 3; k++) {
      out += B45[x % 45];
      x = Math.floor(x / 45);
    }
  }
  if (i < input.length) {
    let x = input[i];
    for (let k = 0; k < 2; k++) {
      out += B45[x % 45];
      x = Math.floor(x / 45);
    }
  }
  return out;
}

export function base45Decode(s: string): Uint8Array {
  const v = [...s].map((c) => {
    const d = B45.indexOf(c);
    if (d < 0) throw new Error(`invalid base45 '${c}'`);
    return d;
  });
  const out: number[] = [];
  let i = 0;
  for (; i + 2 < v.length; i += 3) {
    const x = v[i] + v[i + 1] * 45 + v[i + 2] * 45 * 45;
    if (x > 0xffff) throw new Error('base45 overflow');
    out.push(x >> 8, x & 0xff);
  }
  if (i < v.length) {
    if (v.length - i !== 2) throw new Error('dangling base45 character');
    out.push(v[i] + v[i + 1] * 45);
  }
  return Uint8Array.from(out);
}

// ---------------------------------------------------------------------------------------- CBOR

type Cbor = bigint | number | Uint8Array | string | Cbor[] | Map<string, Cbor>;

function head(major: number, n: bigint, out: number[]) {
  const m = major << 5;
  if (n < 24n) out.push(m | Number(n));
  else if (n < 0x100n) out.push(m | 24, Number(n));
  else if (n < 0x10000n) out.push(m | 25, Number(n >> 8n), Number(n & 0xffn));
  else if (n < 0x100000000n) out.push(m | 26, ...[24n, 16n, 8n, 0n].map((s) => Number((n >> s) & 0xffn)));
  else out.push(m | 27, ...[56n, 48n, 40n, 32n, 24n, 16n, 8n, 0n].map((s) => Number((n >> s) & 0xffn)));
}

function encode(v: Cbor, out: number[]) {
  if (typeof v === 'bigint' || typeof v === 'number') {
    const n = BigInt(v);
    if (n < 0n) throw new Error('negative integers are not part of the menu format');
    head(0, n, out);
  } else if (v instanceof Uint8Array) {
    head(2, BigInt(v.length), out);
    out.push(...v);
  } else if (typeof v === 'string') {
    const b = new TextEncoder().encode(v);
    head(3, BigInt(b.length), out);
    out.push(...b);
  } else if (Array.isArray(v)) {
    head(4, BigInt(v.length), out);
    v.forEach((x) => encode(x, out));
  } else {
    head(5, BigInt(v.size), out);
    for (const [k, x] of v) {
      encode(k, out);
      encode(x, out);
    }
  }
}

function decode(b: Uint8Array): Cbor {
  let o = 0;
  const len = (info: number): bigint => {
    if (info < 24) return BigInt(info);
    const size = { 24: 1, 25: 2, 26: 4, 27: 8 }[info];
    if (!size) throw new Error(`unsupported CBOR length ${info}`);
    let n = 0n;
    for (let k = 0; k < size; k++) n = (n << 8n) | BigInt(b[o++]);
    return n;
  };
  const item = (): Cbor => {
    const first = b[o++];
    const major = first >> 5;
    const info = first & 0x1f;
    if (info === 31) {
      // Indefinite length (some encoders emit maps/arrays this way): read until the 0xff break.
      if (major === 4) {
        const a: Cbor[] = [];
        while (b[o] !== 0xff) a.push(item());
        o++;
        return a;
      }
      if (major === 5) {
        const m = new Map<string, Cbor>();
        while (b[o] !== 0xff) m.set(item() as string, item());
        o++;
        return m;
      }
      throw new Error('unsupported indefinite CBOR item');
    }
    const n = len(info);
    switch (major) {
      case 0:
        return n;
      case 2:
        return b.slice(o, (o += Number(n)));
      case 3:
        return new TextDecoder().decode(b.slice(o, (o += Number(n))));
      case 4:
        return Array.from({ length: Number(n) }, item);
      case 5: {
        const m = new Map<string, Cbor>();
        for (let k = 0n; k < n; k++) m.set(item() as string, item());
        return m;
      }
      case 7:
        if (info === 22) return null as unknown as Cbor;
        throw new Error('unsupported CBOR simple value');
      default:
        throw new Error(`unsupported CBOR major type ${major}`);
    }
  };
  return item();
}

// ------------------------------------------------------------------------------------- payload

export function encodePayload(p: MenuPayload): Uint8Array {
  const m = p.menu;
  const items: Cbor[] = m.items.map((it, i) => {
    const e = new Map<string, Cbor>([
      ['id', it.id],
      ['c', it.category],
      ['p', it.price],
      ['f', it.floor],
      ['n', p.names[i]],
    ]);
    const d = p.descriptions[i];
    if (d != null) e.set('d', d);
    return e;
  });
  const wire = new Map<string, Cbor>([
    ['v', 1],
    ['m', new Uint8Array(getAddressEncoder().encode(m.merchant))],
    ['r', m.root],
    ['iss', m.issuedSlot],
    ['ex', m.expiresSlot],
    ['n', m.menuNonce],
    ['k', m.signerPubkey],
    ['s', m.signature],
    ['i', items],
  ]);
  const out: number[] = [];
  encode(wire, out);
  return Uint8Array.from(out);
}

export function decodePayload(bytes: Uint8Array): MenuPayload {
  const w = decode(bytes) as Map<string, Cbor>;
  if (Number(w.get('v')) !== 1) throw new Error(`unsupported menu version ${w.get('v')}`);
  const merchant = getAddressDecoder().decode(w.get('m') as Uint8Array) as Address;
  const rows = (w.get('i') as Map<string, Cbor>[]).map((e) => ({
    item: { id: e.get('id') as Uint8Array, category: Number(e.get('c')), price: BigInt(e.get('p') as bigint), floor: BigInt(e.get('f') as bigint) } satisfies Item,
    name: e.get('n') as string,
    description: (e.get('d') as string | undefined) ?? null,
  }));
  const fields = {
    merchant,
    issuedSlot: BigInt(w.get('iss') as bigint),
    expiresSlot: BigInt(w.get('ex') as bigint),
    menuNonce: BigInt(w.get('n') as bigint),
    items: rows.map((r) => r.item),
  };
  const root = w.get('r') as Uint8Array;
  return {
    menu: { ...fields, root, header: menuHeader(fields, root), signerPubkey: w.get('k') as Uint8Array, signature: w.get('s') as Uint8Array },
    names: rows.map((r) => r.name),
    descriptions: rows.map((r) => r.description),
  };
}

export const toQr = (p: MenuPayload): string => QR_PREFIX + base45Encode(encodePayload(p));

export function fromQr(text: string): MenuPayload {
  if (!text.startsWith(QR_PREFIX)) throw new Error('not a Tally menu');
  return decodePayload(base45Decode(text.slice(QR_PREFIX.length)));
}
