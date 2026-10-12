// Tally cloud: one small web service for the hosted demo.
//   /            landing page          /counter   live café QR (scan with Tally)
//   /menu.json   machine-readable menu /mcp       MCP over Streamable HTTP (stateless)
//   /p/<id>      "Open in Tally" hand-off for a proposal (from the Telegram bot)
//   /c/<owner>[/<id>]  a capsule's rules, read from chain (share with whoever you gave an allowance)
//   /telegram/<secret>  Telegram webhook   /health
//   /helius/<secret>/<chat>/<owner>  Helius webhook: payment alerts in Telegram
//   /places/merchant/<pda>  a registered counter's Google Maps place   /places/photo?ref=...
// Env: PORT, PUBLIC_URL, TALLY_RPC_URL, TALLY_MERCHANT, TALLY_MENU_SECRET (JSON array of 32 bytes),
//      TELEGRAM_BOT_TOKEN, TELEGRAM_WEBHOOK_SECRET, HELIUS_API_KEY, GOOGLE_PLACES_KEY, PLACE_PINS.
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { randomBytes } from 'node:crypto';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { address, createSolanaRpc } from '@solana/kit';
import { Alerts, describe, type RawTx } from './alerts.js';
import { Proposals, TallyBot } from './bot.js';
import { Places, parsePins } from './places.js';
import { Counter } from './counter.js';
import { createTallyServer, merchantName, payoutNames, readRules } from './tools.js';
import { findCapsulePda } from '@tally/sdk';
import { CATEGORY_NAMES, usd } from './policy.js';

const env = (k: string, fallback?: string) => {
  const v = process.env[k] ?? fallback;
  if (v === undefined) throw new Error(`missing env ${k}`);
  return v;
};

const PORT = Number(process.env.PORT ?? 8788);
const PUBLIC_URL = (process.env.PUBLIC_URL ?? process.env.RENDER_EXTERNAL_URL ?? `http://localhost:${PORT}`).replace(/\/$/, '');
const rpc = createSolanaRpc(env('TALLY_RPC_URL', 'https://api.devnet.solana.com'));
const counter = new Counter(rpc, address(env('TALLY_MERCHANT')), Uint8Array.from(JSON.parse(env('TALLY_MENU_SECRET')) as number[]));
const proposals = new Proposals();
const webhookSecret = process.env.TELEGRAM_WEBHOOK_SECRET ?? randomBytes(24).toString('base64url');
const alerts = process.env.HELIUS_API_KEY && PUBLIC_URL.startsWith('https://') ? new Alerts(process.env.HELIUS_API_KEY, PUBLIC_URL, webhookSecret) : undefined;
const bot = process.env.TELEGRAM_BOT_TOKEN ? new TallyBot(process.env.TELEGRAM_BOT_TOKEN, PUBLIC_URL, PUBLIC_URL, proposals, alerts) : undefined;
const places = process.env.GOOGLE_PLACES_KEY ? new Places(process.env.GOOGLE_PLACES_KEY, parsePins(process.env.PLACE_PINS)) : undefined;

/** Payout → merchant name, refreshed at most every ten minutes, to name who an agent paid. */
let names: { at: number; map: Map<string, string> } = { at: 0, map: new Map() };
async function merchantNames() {
  if (Date.now() - names.at > 600_000) names = { at: Date.now(), map: await payoutNames().catch(() => names.map) };
  return names.map;
}

const page = (title: string, body: string) => `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title}</title>
<style>
:root{--ink:#040a1e;--text:#f4f7ff;--muted:#a3b1d6;--brand:#0066ff;--bright:#3382f6}
*{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(120% 70% at 50% -10%,#0b3fb8 0%,#071233 45%,var(--ink) 100%);color:var(--text);font:16px/1.5 system-ui,sans-serif;display:grid;place-items:center;padding:24px}
main{max-width:560px;width:100%;text-align:center}h1{font:600 40px/1.1 Georgia,serif;margin:.2em 0 .4em}.k{letter-spacing:.14em;color:var(--muted);font-size:12px;text-transform:uppercase}
p{color:var(--muted)}a.btn{display:inline-block;margin-top:12px;padding:14px 26px;border-radius:999px;background:linear-gradient(#4d93ff,var(--brand));color:#fff;text-decoration:none;font-weight:600;box-shadow:0 10px 30px #0066ff66}
.card{background:#ffffff14;border:1px solid #ffffff29;border-radius:24px;padding:18px;margin:16px 0;text-align:left}code{color:#cfe0ff;word-break:break-all}
img.qr{background:#fff;border-radius:24px;padding:12px;width:min(420px,100%);box-shadow:0 0 0 4px var(--bright),0 20px 60px #0066ff66}
</style><main>${body}</main></html>`;

function send(res: ServerResponse, status: number, type: string, body: string | Buffer) {
  res.writeHead(status, { 'content-type': type, 'cache-control': 'no-store' });
  res.end(body);
}

async function readBody(req: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  for await (const c of req) chunks.push(c as Buffer);
  const raw = Buffer.concat(chunks).toString('utf8');
  return raw ? JSON.parse(raw) : undefined;
}

async function route(req: IncomingMessage, res: ServerResponse) {
  const url = new URL(req.url ?? '/', PUBLIC_URL);
  const path = url.pathname;

  if (path === '/health') return send(res, 200, 'application/json', '{"ok":true}');
  if (path === '/menu.json') return send(res, 200, 'application/json', (await counter.current()).json);
  if (path === '/qr.png') return send(res, 200, 'image/png', await counter.png());

  if (path === '/mcp') {
    // Stateless: a fresh server and transport per request, as the SDK recommends for horizontal scale.
    const server = createTallyServer(PUBLIC_URL);
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    res.on('close', () => {
      void transport.close();
      void server.close();
    });
    await server.connect(transport);
    return transport.handleRequest(req, res, req.method === 'POST' ? await readBody(req) : undefined);
  }

  if (path === `/telegram/${webhookSecret}` && req.method === 'POST') {
    if (req.headers['x-telegram-bot-api-secret-token'] !== webhookSecret) return send(res, 401, 'text/plain', 'no');
    const update = await readBody(req);
    send(res, 200, 'application/json', '{}'); // acknowledge first; Telegram retries slow webhooks
    await bot?.handle(update as Parameters<TallyBot['handle']>[0]);
    return;
  }

  if (path.startsWith(`/helius/${webhookSecret}/`) && req.method === 'POST') {
    if (req.headers.authorization !== webhookSecret) return send(res, 401, 'text/plain', 'no');
    const [chat, owner] = path.slice(`/helius/${webhookSecret}/`.length).split('/');
    const txs = ((await readBody(req)) ?? []) as RawTx[];
    send(res, 200, 'application/json', '{}');
    if (!bot || !chat || !owner) return;
    const [capsule] = await findCapsulePda({ owner: address(owner), capsuleId: 1n });
    const map = await merchantNames();
    for (const tx of txs) {
      const line = describe(tx, capsule, owner, map, (sig) => `https://explorer.solana.com/tx/${sig}?cluster=devnet`);
      if (line) await bot.notify(Number(chat), line).catch((e) => console.error('alert:', String(e)));
    }
    return;
  }

  if (path.startsWith('/places/merchant/')) {
    if (!places) return send(res, 404, 'application/json', '{"error":"places not configured"}');
    const merchant = path.slice('/places/merchant/'.length);
    const name = await merchantName(merchant).catch(() => null);
    if (!name) return send(res, 404, 'application/json', '{"error":"not a registered merchant"}');
    const view = await places.forMerchant(merchant, name);
    return send(res, 200, 'application/json', JSON.stringify({ merchant, merchantName: name, place: view }));
  }

  if (path === '/places/photo') {
    const img = await places?.photo(url.searchParams.get('ref') ?? '');
    if (!img) return send(res, 404, 'text/plain', 'not found');
    res.writeHead(200, { 'content-type': img.type, 'cache-control': 'public, max-age=86400' });
    return res.end(img.body);
  }

  if (path.startsWith('/p/')) {
    const p = proposals.get(path.slice(3));
    if (!p) return send(res, 410, 'text/html; charset=utf-8', page('Expired · Tally', '<p class="k">Tally</p><h1>This offer expired</h1><p>Menus are signed for about a minute. Ask the bot again for a fresh one.</p>'));
    const qr = p.link.slice('tally://checkout?p='.length);
    const intent = `intent://checkout?p=${qr}#Intent;scheme=tally;package=app.tally;end`;
    return send(
      res,
      200,
      'text/html; charset=utf-8',
      page(
        'Open in Tally',
        `<p class="k">Proposed purchase</p><h1>${p.title.replace(/</g, '&lt;')}</h1><p>Nothing is paid yet. Tally checks the signed menu and your rules; you tap to pay.</p>
<a class="btn" href="${intent}">Open in Tally</a><p style="margin-top:22px">Or scan with Tally:</p><img class="qr" alt="Proposal QR" src="data:image/png;base64,${p.png.toString('base64')}">`,
      ),
    );
  }

  if (path.startsWith('/c/')) {
    const [owner, id = '1'] = path.slice(3).split('/');
    const esc = (t: string) => t.replace(/[<>&]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' })[c] as string);
    let body: string;
    try {
      const { capsule, rules } = await readRules(owner, Number(id));
      const cats = rules.categories.map((x) => CATEGORY_NAMES[x] ?? String(x)).join(', ') || 'none';
      body =
        `<p class="k">Tally capsule</p><h1>${rules.frozen ? 'Frozen' : rules.expired ? 'Expired' : 'Active'}</h1>` +
        `<div class="card"><b>What the agent may buy</b><br>${esc(cats)}</div>` +
        `<div class="card"><b>Per purchase</b> up to ${usd(rules.perTxCapMicro)}<br><b>Today</b> ${usd(rules.dailyRemainingMicro)} of ${usd(rules.dailyCapMicro)} left<br><b>Merchant bond</b> at least ${usd(rules.minMerchantBondMicro).replace('$', '')} SKR</div>` +
        `<div class="card"><b>Owner</b> <code>${esc(owner)}</code><br><b>Capsule</b> <code>${capsule}</code></div>` +
        `<p>Read live from Solana devnet. Pricier-than-cheapest picks always need the owner's tap in Tally.</p>`;
    } catch (e) {
      body = `<p class="k">Tally capsule</p><h1>Not found</h1><p>${esc(String((e as Error).message))}</p>`;
    }
    return send(res, 200, 'text/html; charset=utf-8', page('Capsule · Tally', body));
  }

  if (path === '/counter') {
    return send(
      res,
      200,
      'text/html; charset=utf-8',
      page(
        `${counter.name} · Tally counter`,
        `<p class="k">Tally counter</p><h1>${counter.name}</h1><img id="q" class="qr" src="/qr.png" alt="Signed menu QR"><p>Scan with Tally · signed on demand, valid about 48 s</p>
<script>setInterval(()=>{document.getElementById('q').src='/qr.png?'+Date.now()},8000)</script>`,
      ),
    );
  }

  if (path === '/') {
    return send(
      res,
      200,
      'text/html; charset=utf-8',
      page(
        'Tally cloud',
        `<p class="k">Tally · devnet</p><h1>Agents propose.<br>Tally checks.</h1>
<p>A demo café, an MCP server and a Telegram bot. None of them can pay: every purchase opens in the Tally app, where you approve and the chain re-checks it.</p>
<div class="card"><b>Café counter</b><br><a href="/counter" style="color:#9cc2ff">/counter</a> (scan with Tally) · <a href="/menu.json" style="color:#9cc2ff">/menu.json</a></div>
<div class="card"><b>MCP server</b> (Streamable HTTP)<br><code>${PUBLIC_URL}/mcp</code><br><small>claude mcp add --transport http tally ${PUBLIC_URL}/mcp</small></div>
<div class="card"><b>Telegram</b><br>Message the bot "oat latte" after <code>/wallet &lt;your address&gt;</code>.</div>
<div class="card"><b>Share a capsule</b><br><code>${PUBLIC_URL}/c/&lt;owner address&gt;</code>: its rules, read from chain.</div>`,
      ),
    );
  }

  send(res, 404, 'text/plain', 'not found');
}

createServer((req, res) => {
  route(req, res).catch((e) => {
    console.error(e);
    if (!res.headersSent) send(res, 500, 'application/json', JSON.stringify({ error: String((e as Error).message) }));
  });
}).listen(PORT, async () => {
  console.log(`tally cloud on ${PUBLIC_URL} (port ${PORT})`);
  if (bot && PUBLIC_URL.startsWith('https://')) {
    await bot.register(webhookSecret).then(() => console.log('telegram webhook registered'), (e) => console.error('telegram:', String(e)));
  }
  if (bot && alerts) {
    await alerts.restore().then(
      (links) => {
        for (const [chat, owner] of links) bot.link(chat, owner);
        console.log(`payment alerts: ${links.size} linked chat(s)`);
      },
      (e) => console.error('helius:', String(e)),
    );
  }
});
