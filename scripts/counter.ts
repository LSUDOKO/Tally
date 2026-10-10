// A laptop "counter" for the demo merchant: re-signs the café menu every 20 s (120-slot window,
// as the phone terminal does) and serves the live Tally QR on a local page that refreshes itself.
// Point a phone running Tally at the screen: Scan or tap to pay → scan.
// Usage: TALLY_CLUSTER=devnet pnpm -C scripts exec tsx counter.ts [port=8787]
import { createServer } from 'node:http';
import { type Address } from '@solana/kit';
import QRCode from 'qrcode';
import { computeFloors, sha256v, signMenu, toQr, type Item, type MenuPayload } from '@tally/sdk';
import { loadState, namedSecret, rpc } from './lib/env';

// Same café as the app's built-in catalog (merchant/Catalog.kt), so item ids match across tools.
const CATALOG: Array<[string, number, bigint]> = [
  ['Oat latte (regular)', 3, 4_500_000n],
  ['Oat latte (large)', 3, 5_900_000n],
  ['Cappuccino', 3, 5_200_000n],
  ['Reserve roast', 3, 18_000_000n],
  ['Croissant', 0, 3_000_000n],
  ['Bagel', 0, 3_800_000n],
  ['Metro single', 4, 2_750_000n],
  ['Day pass', 4, 9_000_000n],
];
const WINDOW = 120n; // slots (~48 s); the program refuses autonomous payments on longer windows
const RESIGN_MS = 20_000;

async function main() {
  const port = Number(process.argv[2] ?? 8787);
  const state = loadState();
  if (!state.merchant) throw new Error('run init-devnet.ts and seed-demo.ts first');
  const secret = namedSecret('merchant-menu-key');
  const items: Item[] = computeFloors(
    CATALOG.map(([, category, price], i) => ({ id: sha256v(new TextEncoder().encode(`tally-item-${i}`)).slice(0, 16), category, price, floor: 0n })),
  );
  let nonce = BigInt(Date.now());
  let png: Buffer = Buffer.alloc(0);
  let expires = 0n;
  let menuJson = '{}';

  const resign = async () => {
    const slot = await rpc.getSlot().send();
    const menu = await signMenu({ merchant: state.merchant as Address, issuedSlot: slot, expiresSlot: slot + WINDOW, menuNonce: nonce++, items }, secret);
    const payload: MenuPayload = { menu, names: CATALOG.map((c) => c[0]), descriptions: CATALOG.map(() => null) };
    const qrText = toQr(payload);
    png = await QRCode.toBuffer(qrText, { errorCorrectionLevel: 'L', margin: 2, width: 720 });
    expires = menu.expiresSlot;
    // Machine-readable menu for agents (the Tally MCP server reads this): the signed QR plus plain fields.
    menuJson = JSON.stringify({
      merchant: menu.merchant,
      name: 'Blue Door Coffee',
      issuedSlot: Number(menu.issuedSlot),
      expiresSlot: Number(menu.expiresSlot),
      qr: qrText,
      items: menu.items.map((it, i) => ({
        id: Buffer.from(it.id).toString('hex'),
        name: CATALOG[i][0],
        category: it.category,
        priceMicro: Number(it.price),
        floorMicro: Number(it.floor),
      })),
    });
    console.log(`menu #${menu.menuNonce} signed at slot ${slot}, valid to ${expires}`);
  };
  // Flaky networks are normal at a counter: keep trying rather than crash; the last good QR stays up.
  for (let attempt = 1; ; attempt++) {
    try {
      await resign();
      break;
    } catch (e) {
      console.error(`first sign failed (attempt ${attempt}): ${String(e)}; retrying`);
      await new Promise((r) => setTimeout(r, 3_000));
    }
  }
  setInterval(() => resign().catch((e) => console.error('re-sign failed, keeping the last menu:', String(e))), RESIGN_MS);

  createServer((req, res) => {
    if (req.url?.startsWith('/menu.json')) {
      res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
      res.end(menuJson);
      return;
    }
    if (req.url?.startsWith('/qr.png')) {
      res.writeHead(200, { 'content-type': 'image/png', 'cache-control': 'no-store' });
      res.end(png);
      return;
    }
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
    res.end(`<!doctype html><meta charset="utf-8"><title>Blue Door Coffee · Tally counter</title>
<body style="margin:0;background:radial-gradient(120% 70% at 50% -10%,#0b3fb8 0%,#071233 45%,#040a1e 100%);color:#f4f7ff;font:16px system-ui;display:grid;place-items:center;min-height:100vh">
<div style="text-align:center"><p style="letter-spacing:.14em;color:#a3b1d6;font-size:12px;margin:0">TALLY COUNTER</p><h1 style="font:600 40px Georgia,serif;margin:.2em 0 .6em">Blue Door Coffee</h1>
<img id="q" src="/qr.png" width="520" height="520" style="background:#fff;border-radius:28px;padding:14px;box-shadow:0 0 0 4px #3382f6,0 20px 60px #0066ff66">
<p>Scan with Tally · re-signed every 20 s · valid to slot <span id="e">${expires}</span></p></div>
<script>setInterval(()=>{document.getElementById('q').src='/qr.png?'+Date.now()},5000)</script>`);
  }).listen(port, () => console.log(`counter at http://localhost:${port}`));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
