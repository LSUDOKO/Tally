import { describe as suite, expect, it } from 'vitest';
import { Alerts, describe, type RawTx } from '../src/alerts.js';
import { Places, parsePins } from '../src/places.js';

const CAPSULE = 'Caps1111111111111111111111111111111111111111';
const OWNER = 'Owner111111111111111111111111111111111111111';
const PAYOUT = 'Payout11111111111111111111111111111111111111';
const USDC = 'Usdc1111111111111111111111111111111111111111';
const explorer = (s: string) => `https://x/${s}`;

const bal = (accountIndex: number, owner: string, amount: number) => ({ accountIndex, mint: USDC, owner, uiTokenAmount: { amount: String(amount) } });
const tx = (pre: ReturnType<typeof bal>[], post: ReturnType<typeof bal>[], err: unknown = null): RawTx => ({
  meta: { err, preTokenBalances: pre, postTokenBalances: post },
  transaction: { signatures: ['SIG'] },
});

suite('payment alerts', () => {
  const names = new Map([[PAYOUT, 'Tally Demo Café']]);

  it('names the counter an agent paid and asks "was this you?"', () => {
    const line = describe(tx([bal(1, CAPSULE, 12_000_000), bal(2, PAYOUT, 0)], [bal(1, CAPSULE, 7_500_000), bal(2, PAYOUT, 4_500_000)]), CAPSULE, OWNER, names, explorer);
    expect(line).toContain('Your agent paid $4.50 at Tally Demo Café');
    expect(line).toContain('Was this you?');
    expect(line).toContain('https://x/SIG');
  });

  it('tells top-ups and withdrawals apart from purchases', () => {
    expect(describe(tx([bal(1, CAPSULE, 0)], [bal(1, CAPSULE, 10_000_000)]), CAPSULE, OWNER, names, explorer)).toMatch(/^You added \$10\.00/);
    expect(describe(tx([bal(1, CAPSULE, 5_000_000), bal(2, OWNER, 0)], [bal(1, CAPSULE, 0), bal(2, OWNER, 5_000_000)]), CAPSULE, OWNER, names, explorer)).toMatch(/^You withdrew \$5\.00/);
  });

  it('stays quiet for failed transactions and ones that never touched the vault', () => {
    expect(describe(tx([bal(1, CAPSULE, 5)], [bal(1, CAPSULE, 0)], { InstructionError: [0, 'x'] }), CAPSULE, OWNER, names, explorer)).toBeNull();
    expect(describe(tx([bal(1, PAYOUT, 0)], [bal(1, PAYOUT, 9)]), CAPSULE, OWNER, names, explorer)).toBeNull();
  });

  it('keeps one webhook per chat and rebuilds the links from Helius', async () => {
    const hooks: { webhookID: string; webhookURL: string }[] = [{ webhookID: 'other', webhookURL: 'https://elsewhere/hook' }];
    const fake = (async (url: string, init?: RequestInit) => {
      const u = new URL(url);
      if (init?.method === 'GET') return Response.json(hooks);
      if (init?.method === 'DELETE') {
        hooks.splice(hooks.findIndex((h) => u.pathname.endsWith(h.webhookID)), 1);
        return new Response(null);
      }
      const body = JSON.parse(String(init?.body));
      expect(body.authHeader).toBe('s3cret');
      hooks.push({ webhookID: `id${hooks.length}`, webhookURL: body.webhookURL });
      return Response.json(hooks.at(-1));
    }) as typeof fetch;
    const a = new Alerts('k', 'https://cloud', 's3cret', fake);
    await a.watch(42, OWNER, 'Vault');
    await a.watch(42, OWNER, 'Vault'); // relinking replaces, never duplicates
    await a.watch(7, PAYOUT, 'Vault2');
    expect(hooks.filter((h) => h.webhookURL.startsWith('https://cloud/'))).toHaveLength(2);
    expect([...(await a.restore())]).toEqual([[42, OWNER], [7, PAYOUT]]);
  });
});

suite('places', () => {
  const place = { id: 'P1', displayName: { text: 'Blue Door Café' }, formattedAddress: '1 Main St', rating: 4.6, userRatingCount: 210, photos: [{ name: 'places/P1/photos/A1' }] };

  it('matches by name, caches, and proxies only photos it handed out', async () => {
    let calls = 0;
    const fake = (async (url: string) => {
      calls++;
      if (String(url).includes('searchText')) return Response.json({ places: [place] });
      return new Response(new Uint8Array([1, 2, 3]), { headers: { 'content-type': 'image/jpeg' } });
    }) as typeof fetch;
    const p = new Places('k', new Map(), fake);
    const v = await p.forMerchant('M1', 'Blue Door');
    expect(v).toMatchObject({ pinned: false, name: 'Blue Door Café', rating: 4.6, photo: '/places/photo?ref=places%2FP1%2Fphotos%2FA1' });
    await p.forMerchant('M1', 'Blue Door');
    expect(calls).toBe(1);
    expect((await p.photo('places/P1/photos/A1'))?.body.length).toBe(3);
    expect(await p.photo('places/OTHER/photos/Z9')).toBeNull();
  });

  it('uses a maintainer pin when there is one', async () => {
    const fake = (async (url: string) => {
      expect(String(url)).toContain('/places/P1');
      return Response.json(place);
    }) as typeof fetch;
    const p = new Places('k', parsePins('M1=P1, M2=P2'), fake);
    expect(await p.forMerchant('M1', 'whatever')).toMatchObject({ pinned: true, address: '1 Main St' });
  });
});
