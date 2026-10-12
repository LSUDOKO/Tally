// Telegram front door for Tally. Chat captures intent and shows what the owner's rules allow; it
// never pays. Every offer comes with a one-time "Open in Tally" link (and a QR) that hands the
// proposal to the app, where the owner taps to pay and the chain re-checks everything.
import { randomBytes } from 'node:crypto';
import { address } from '@solana/kit';
import { toProposalLink, toProposalQr } from '@tally/sdk';
import QRCode from 'qrcode';
import { CATEGORY_NAMES, bestMatch, judge, usd, type ItemVerdict } from './policy.js';
import type { Alerts } from './alerts.js';
import { readMenu, readRules } from './tools.js';

export type Command =
  | { kind: 'start' }
  | { kind: 'help' }
  | { kind: 'wallet'; address: string | null }
  | { kind: 'rules' }
  | { kind: 'menu' }
  | { kind: 'share' }
  | { kind: 'want'; request: string };

/** Map a chat message to a command; plain text is a shopping request. */
export function parseCommand(text: string): Command {
  const t = text.trim();
  const [head, ...rest] = t.split(/\s+/);
  const cmd = head.toLowerCase().replace(/@\w+$/, '');
  const arg = rest.join(' ').trim();
  switch (cmd) {
    case '/start':
      return { kind: 'start' };
    case '/help':
      return { kind: 'help' };
    case '/wallet':
      return { kind: 'wallet', address: arg || null };
    case '/rules':
      return { kind: 'rules' };
    case '/menu':
      return { kind: 'menu' };
    case '/share':
      return { kind: 'share' };
    default:
      return { kind: 'want', request: (cmd.startsWith('/') ? arg : t).slice(0, 200) };
  }
}

export function isWalletAddress(s: string): boolean {
  try {
    address(s);
    return true;
  } catch {
    return false;
  }
}

/** One-time proposal hand-offs, kept briefly in memory (menus expire within a minute anyway). */
export class Proposals {
  private store = new Map<string, { link: string; png: Buffer; title: string; expires: number }>();

  put(link: string, png: Buffer, title: string, ttlMs = 120_000): string {
    this.sweep();
    const id = randomBytes(9).toString('base64url');
    this.store.set(id, { link, png, title, expires: Date.now() + ttlMs });
    return id;
  }

  get(id: string) {
    this.sweep();
    return this.store.get(id);
  }

  private sweep() {
    const now = Date.now();
    for (const [k, v] of this.store) if (v.expires < now) this.store.delete(k);
  }
}

const HELP =
  'I find things within your Tally rules and hand them to the app for you to approve. I can never pay.\n\n' +
  '/wallet <address>: link your Tally wallet\n/rules: what your capsule allows today\n/menu: the café\'s signed menu\n/share: a link to your capsule\'s rules for someone else\n' +
  'Or just say what you want, e.g. "oat latte".';

export class TallyBot {
  private owners = new Map<number, string>();

  constructor(
    private token: string,
    private publicUrl: string,
    private counterUrl: string,
    private proposals: Proposals,
    private alerts?: Alerts,
  ) {}

  /** Restore chat links (e.g. from Helius after a restart). */
  link(chat: number, owner: string) {
    this.owners.set(chat, owner);
  }

  /** Send a message to a linked chat (payment alerts). */
  notify(chat: number, text: string) {
    return this.say(chat, text);
  }

  private async api(method: string, body: Record<string, unknown> | FormData) {
    const res = await fetch(`https://api.telegram.org/bot${this.token}/${method}`, {
      method: 'POST',
      ...(body instanceof FormData ? { body } : { headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) }),
    });
    const out = (await res.json()) as { ok: boolean; description?: string };
    if (!out.ok) throw new Error(`telegram ${method}: ${out.description}`);
    return out;
  }

  private say(chat: number, text: string, extra: Record<string, unknown> = {}) {
    return this.api('sendMessage', { chat_id: chat, text, disable_web_page_preview: true, ...extra });
  }

  /** Point Telegram at our webhook (secret path + header) and publish the command list. */
  async register(secret: string) {
    await this.api('setWebhook', { url: `${this.publicUrl}/telegram/${secret}`, secret_token: secret, allowed_updates: ['message'] });
    await this.api('setMyCommands', {
      commands: [
        { command: 'wallet', description: 'Link your Tally wallet address' },
        { command: 'rules', description: 'What your capsule allows today' },
        { command: 'menu', description: "The café's signed menu" },
        { command: 'share', description: 'A link to your capsule\'s rules' },
        { command: 'help', description: 'How this works' },
      ],
    });
  }

  async handle(update: { message?: { chat: { id: number }; text?: string } }) {
    const msg = update.message;
    if (!msg?.text) return;
    const chat = msg.chat.id;
    try {
      await this.route(chat, parseCommand(msg.text));
    } catch (e) {
      await this.say(chat, `Something went wrong: ${(e as Error).message}`).catch(() => undefined);
    }
  }

  private async route(chat: number, c: Command) {
    switch (c.kind) {
      case 'start':
      case 'help':
        return this.say(chat, `Hi, I'm the Tally bot. ${HELP}`);
      case 'wallet': {
        if (!c.address) return this.say(chat, 'Send /wallet followed by your Tally wallet address (the one shown on Tally\'s home screen).');
        if (!isWalletAddress(c.address)) return this.say(chat, "That doesn't look like a Solana address.");
        const { vault, rules } = await readRules(c.address, 1);
        this.owners.set(chat, c.address);
        let watching = '';
        if (this.alerts) {
          watching = await this.alerts.watch(chat, c.address, vault).then(
            () => "\n\nI'll message you here whenever money moves in your capsule, even with Tally closed.",
            () => '',
          );
        }
        return this.say(chat, `Linked. Your capsule allows ${rules.categories.map((x) => CATEGORY_NAMES[x] ?? x).join(', ')}, up to ${usd(rules.perTxCapMicro)} each, ${usd(rules.dailyRemainingMicro)} left today.${watching}`);
      }
      case 'rules': {
        const owner = this.owners.get(chat);
        if (!owner) return this.say(chat, 'Link your wallet first: /wallet <address>');
        const { rules } = await readRules(owner, 1);
        return this.say(
          chat,
          `Allowed: ${rules.categories.map((x) => CATEGORY_NAMES[x] ?? x).join(', ')}\nPer purchase: ${usd(rules.perTxCapMicro)}\nToday: ${usd(rules.dailyRemainingMicro)} of ${usd(rules.dailyCapMicro)} left` +
            (rules.frozen ? '\nFrozen: nothing can be spent until you unfreeze in Tally.' : '') +
            '\n\nPricier-than-cheapest picks always need your approval in the app.',
        );
      }
      case 'menu': {
        const m = await readMenu(this.counterUrl);
        return this.say(chat, `${m.merchantName} (signature verified on-chain):\n` + m.items.map((i) => `• ${i.name}: ${usd(i.priceMicro)}${i.priceMicro === i.floorMicro ? ' (cheapest)' : ''}`).join('\n'));
      }
      case 'share': {
        const owner = this.owners.get(chat);
        if (!owner) return this.say(chat, 'Link your wallet first: /wallet <address>');
        return this.say(chat, `Your capsule's rules, read live from chain: ${this.publicUrl}/c/${owner}`);
      }
      case 'want':
        return this.offer(chat, c.request);
    }
  }

  private async offer(chat: number, request: string) {
    const owner = this.owners.get(chat);
    if (!owner) return this.say(chat, 'Link your wallet first so I can check your rules: /wallet <address>');
    const [menu, { rules }] = await Promise.all([readMenu(this.counterUrl), readRules(owner, 1)]);
    const window = menu.payload.menu.expiresSlot - menu.payload.menu.issuedSlot;
    const judged: ItemVerdict[] = menu.items.map((i) => judge(i, rules, menu.merchantBondMicro, window));
    const best = bestMatch(request, judged);
    if (!best) {
      const refused = judged.find((j) => j.verdict === 'refused' && request.toLowerCase().split(/\s+/).some((w) => w.length > 2 && j.name.toLowerCase().includes(w)));
      return this.say(chat, refused ? `${refused.name} isn't possible: ${refused.reasons.join('; ')}.` : `Nothing on ${menu.merchantName}'s menu matches "${request}" within your rules.`);
    }
    const id = Uint8Array.from(Buffer.from(best.id, 'hex'));
    const link = toProposalLink(menu.payload, id);
    const png = await QRCode.toBuffer(toProposalQr(menu.payload, id), { errorCorrectionLevel: 'L', margin: 2, width: 640 });
    const title = `${best.name} · ${usd(best.priceMicro)} at ${menu.merchantName}`;
    const handoff = this.proposals.put(link, png, title);
    const verdict =
      best.verdict === 'autonomous'
        ? 'The cheapest match and within every rule: one tap in Tally pays it.'
        : `Matches, but needs your approval in Tally: ${best.reasons.join('; ')}.`;
    const form = new FormData();
    form.append('chat_id', String(chat));
    form.append('photo', new Blob([new Uint8Array(png)], { type: 'image/png' }), 'tally-proposal.png');
    form.append('caption', `${title}\n${verdict}\n\nNothing is paid yet. Open it in Tally on your phone (or scan this QR with Tally) within about ${menu.expiresInSeconds} s.`);
    form.append('reply_markup', JSON.stringify({ inline_keyboard: [[{ text: 'Open in Tally', url: `${this.publicUrl}/p/${handoff}` }]] }));
    return this.api('sendPhoto', form);
  }
}
