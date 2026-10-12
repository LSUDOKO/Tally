// "Was this you?" in Telegram, even with the app closed. Each linked chat gets its own Helius
// webhook watching its capsule; the chat and owner ride in the webhook URL, so the link survives
// the free Render instance sleeping and restarting. Read-only: alerts can't move money, and the
// reply always points back to the app, where the owner can freeze.
import { usd } from './policy.js';

/** The parts of a raw (RPC-shaped) transaction an alert needs. */
export interface RawTx {
  slot?: number;
  meta?: {
    err?: unknown;
    preTokenBalances?: TokenBalance[];
    postTokenBalances?: TokenBalance[];
  } | null;
  transaction?: { signatures?: string[]; message?: { accountKeys?: (string | { pubkey: string })[] } };
}

interface TokenBalance {
  accountIndex: number;
  mint: string;
  owner?: string;
  uiTokenAmount: { amount: string };
}

/**
 * One line about what a transaction did to this capsule's vault, or null when it didn't touch it.
 * Money out to anyone but the owner is the agent paying; out to the owner is a withdrawal; in is a top-up.
 */
export function describe(tx: RawTx, capsule: string, owner: string, names: ReadonlyMap<string, string>, explorer: (sig: string) => string): string | null {
  const meta = tx.meta;
  if (!meta || meta.err) return null;
  const amount = (list: TokenBalance[] | undefined, i: number) => BigInt(list?.find((b) => b.accountIndex === i)?.uiTokenAmount.amount ?? '0');
  const indexes = new Set([...(meta.preTokenBalances ?? []), ...(meta.postTokenBalances ?? [])].map((b) => b.accountIndex));
  const deltas = [...indexes].map((i) => {
    const b = [...(meta.postTokenBalances ?? []), ...(meta.preTokenBalances ?? [])].find((x) => x.accountIndex === i)!;
    return { owner: b.owner ?? '', delta: amount(meta.postTokenBalances, i) - amount(meta.preTokenBalances, i) };
  });
  const vault = deltas.find((d) => d.owner === capsule && d.delta !== 0n);
  if (!vault) return null;
  const sig = tx.transaction?.signatures?.[0];
  const link = sig ? `\n${explorer(sig)}` : '';
  const value = usd(Number(vault.delta < 0n ? -vault.delta : vault.delta));
  if (vault.delta > 0n) return `You added ${value} to your Tally capsule.${link}`;
  const to = deltas.find((d) => d.delta > 0n && d.owner !== capsule);
  if (to?.owner === owner) return `You withdrew ${value} from your Tally capsule.${link}`;
  const where = to ? (names.get(to.owner) ?? `${to.owner.slice(0, 4)}…${to.owner.slice(-4)}`) : 'a merchant';
  return `Your agent paid ${value} at ${where}.\nWas this you? If not, open Tally and freeze your capsule: nothing more can be spent until you unfreeze.${link}`;
}

interface Webhook {
  webhookID: string;
  webhookURL: string;
}

export class Alerts {
  private readonly api = 'https://api.helius.xyz/v0/webhooks';

  constructor(
    private apiKey: string,
    private publicUrl: string,
    private secret: string,
    private http: typeof fetch = fetch,
  ) {}

  /** Webhooks deliver to /helius/<secret>/<chat>/<owner>. */
  get prefix() {
    return `${this.publicUrl}/helius/${this.secret}/`;
  }

  private async call<T>(method: string, path: string, body?: unknown): Promise<T> {
    const res = await this.http(`${this.api}${path}?api-key=${this.apiKey}`, {
      method,
      headers: { 'content-type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!res.ok) throw new Error(`helius ${method} ${res.status}: ${(await res.text()).slice(0, 200)}`);
    return (method === 'DELETE' ? undefined : await res.json()) as T;
  }

  private async ours(): Promise<Webhook[]> {
    return (await this.call<Webhook[]>('GET', '')).filter((w) => w.webhookURL.startsWith(this.prefix));
  }

  /** Every linked chat, rebuilt from Helius after a restart. */
  async restore(): Promise<Map<number, string>> {
    const out = new Map<number, string>();
    for (const w of await this.ours()) {
      const [chat, owner] = w.webhookURL.slice(this.prefix.length).split('/');
      if (chat && owner) out.set(Number(chat), owner);
    }
    return out;
  }

  /** Watch this capsule's vault for this chat (one webhook per chat; relinking replaces it). */
  async watch(chat: number, owner: string, vault: string): Promise<void> {
    for (const w of await this.ours()) if (w.webhookURL.startsWith(`${this.prefix}${chat}/`)) await this.call('DELETE', `/${w.webhookID}`);
    await this.call('POST', '', {
      webhookURL: `${this.prefix}${chat}/${owner}`,
      transactionTypes: ['ANY'],
      accountAddresses: [vault],
      webhookType: 'rawDevnet',
      authHeader: this.secret,
    });
  }
}
