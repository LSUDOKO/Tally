// The demo café as a hosted counter: signs its menu on demand (no timers, so it survives a
// sleeping free-tier instance) and serves the same /menu.json the laptop counter does.
import type { Address, Rpc, SolanaRpcApi } from '@solana/kit';
import { computeFloors, sha256v, signMenu, toQr, type Item, type MenuPayload } from '@tally/sdk';
import QRCode from 'qrcode';

/** Same café as the app's built-in catalog and scripts/counter.ts, so item ids match everywhere. */
export const CATALOG: Array<[string, number, bigint]> = [
  ['Oat latte (regular)', 3, 4_500_000n],
  ['Oat latte (large)', 3, 5_900_000n],
  ['Cappuccino', 3, 5_200_000n],
  ['Reserve roast', 3, 18_000_000n],
  ['Croissant', 0, 3_000_000n],
  ['Bagel', 0, 3_800_000n],
  ['Metro single', 4, 2_750_000n],
  ['Day pass', 4, 9_000_000n],
];

const WINDOW = 120n; // slots (~48 s): autonomous payments refuse longer-lived menus
const RESIGN_BELOW = 40n; // re-sign when fewer slots than this remain (~16 s)

export interface CounterMenu {
  payload: MenuPayload;
  qr: string;
  json: string;
  expiresSlot: bigint;
}

export class Counter {
  private items: Item[];
  private cached?: CounterMenu;
  private nonce = BigInt(Date.now());
  private signing?: Promise<CounterMenu>;

  constructor(
    private rpc: Rpc<SolanaRpcApi>,
    readonly merchant: Address,
    private secret: Uint8Array,
    readonly name = 'Blue Door Coffee',
  ) {
    this.items = computeFloors(CATALOG.map(([, category, price], i) => ({ id: sha256v(new TextEncoder().encode(`tally-item-${i}`)).slice(0, 16), category, price, floor: 0n })));
  }

  /** The current menu, freshly signed if the last one is about to expire. */
  async current(): Promise<CounterMenu> {
    const slot = await this.rpc.getSlot().send();
    if (this.cached && this.cached.expiresSlot - slot >= RESIGN_BELOW) return this.cached;
    this.signing ??= this.sign(slot).finally(() => (this.signing = undefined));
    return this.signing;
  }

  async png(): Promise<Buffer> {
    return QRCode.toBuffer((await this.current()).qr, { errorCorrectionLevel: 'L', margin: 2, width: 720 });
  }

  private async sign(slot: bigint): Promise<CounterMenu> {
    const menu = await signMenu({ merchant: this.merchant, issuedSlot: slot, expiresSlot: slot + WINDOW, menuNonce: this.nonce++, items: this.items }, this.secret);
    const payload: MenuPayload = { menu, names: CATALOG.map((c) => c[0]), descriptions: CATALOG.map(() => null) };
    const qr = toQr(payload);
    const json = JSON.stringify({
      merchant: menu.merchant,
      name: this.name,
      issuedSlot: Number(menu.issuedSlot),
      expiresSlot: Number(menu.expiresSlot),
      qr,
      items: menu.items.map((it, i) => ({ id: Buffer.from(it.id).toString('hex'), name: CATALOG[i][0], category: it.category, priceMicro: Number(it.price), floorMicro: Number(it.floor) })),
    });
    this.cached = { payload, qr, json, expiresSlot: menu.expiresSlot };
    return this.cached;
  }
}
