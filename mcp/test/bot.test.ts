import { describe, expect, it } from 'vitest';
import { Proposals, isWalletAddress, parseCommand } from '../src/bot';

describe('telegram bot', () => {
  it('parses commands, including the @botname suffix Telegram adds in groups', () => {
    expect(parseCommand('/start')).toEqual({ kind: 'start' });
    expect(parseCommand('/rules@TallyBot')).toEqual({ kind: 'rules' });
    expect(parseCommand('/wallet 9tGAS2N4DNVpLC3WwYxN8SFCR9m1AF22jVwvNAXLQX9R')).toEqual({ kind: 'wallet', address: '9tGAS2N4DNVpLC3WwYxN8SFCR9m1AF22jVwvNAXLQX9R' });
    expect(parseCommand('/wallet')).toEqual({ kind: 'wallet', address: null });
  });

  it('treats plain text as a shopping request', () => {
    expect(parseCommand('  large oat latte ')).toEqual({ kind: 'want', request: 'large oat latte' });
  });

  it('checks wallet addresses', () => {
    expect(isWalletAddress('9tGAS2N4DNVpLC3WwYxN8SFCR9m1AF22jVwvNAXLQX9R')).toBe(true);
    expect(isWalletAddress('not-an-address')).toBe(false);
  });

  it('hand-off links are one-time ids that expire', async () => {
    const p = new Proposals();
    const id = p.put('tally://checkout?p=X', Buffer.from('png'), 'Oat latte', 30);
    expect(p.get(id)?.link).toBe('tally://checkout?p=X');
    await new Promise((r) => setTimeout(r, 60));
    expect(p.get(id)).toBeUndefined();
  });
});
