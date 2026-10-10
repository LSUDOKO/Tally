// Pure policy for the MCP server: what a capsule's on-chain rules allow on a signed menu.
// The chain enforces all of this again at payment time; this only lets an agent propose sensibly.

export const CYCLE_SLOTS = 216_000n;
export const MAX_AUTONOMOUS_WINDOW = 150n;
export const SLOT_SECONDS = 0.4;

export interface MenuItemView {
  id: string; // hex
  name: string;
  category: number;
  priceMicro: number;
  floorMicro: number;
}

export interface RulesView {
  perTxCapMicro: number;
  dailyCapMicro: number;
  dailyRemainingMicro: number;
  categories: number[];
  minMerchantBondMicro: number;
  frozen: boolean;
  expired: boolean;
}

export interface CapsuleFields {
  perTxCap: bigint;
  dailyCap: bigint;
  spentInCycle: bigint;
  cycleStartSlot: bigint;
  allowedCategories: bigint[];
  minMerchantBond: bigint;
  expirySlot: bigint;
  frozen: boolean;
}

export const CATEGORY_NAMES: Record<number, string> = { 0: 'Food', 1: 'Drinks', 2: 'Snacks', 3: 'Coffee', 4: 'Transit', 5: 'Groceries', 6: 'Digital', 7: 'Services' };

/** Bit `c` of the 256-bit category mask (four little-endian u64 words). */
export function allows(words: bigint[], c: number): boolean {
  if (c < 0 || c > 255) return false;
  return ((BigInt.asUintN(64, words[Math.floor(c / 64)] ?? 0n) >> BigInt(c % 64)) & 1n) === 1n;
}

/** The capsule as an agent should read it, with today's remaining budget (pay_offer's cycle rule). */
export function rulesView(c: CapsuleFields, slot: bigint): RulesView {
  const rolled = slot - c.cycleStartSlot >= CYCLE_SLOTS;
  const remaining = rolled ? c.dailyCap : c.dailyCap > c.spentInCycle ? c.dailyCap - c.spentInCycle : 0n;
  return {
    perTxCapMicro: Number(c.perTxCap),
    dailyCapMicro: Number(c.dailyCap),
    dailyRemainingMicro: Number(remaining),
    categories: Array.from({ length: 256 }, (_, i) => i).filter((i) => allows(c.allowedCategories, i)),
    minMerchantBondMicro: Number(c.minMerchantBond),
    frozen: c.frozen,
    expired: slot > c.expirySlot,
  };
}

export type Verdict = 'autonomous' | 'needs_owner_approval' | 'refused';

export interface ItemVerdict extends MenuItemView {
  verdict: Verdict;
  reasons: string[];
}

/**
 * Mirror of the program's checks for one item: refusals (frozen, expired, category, per-purchase,
 * daily budget) and the cases that need the owner's co-signature (pricier than the category's
 * cheapest, under-bonded merchant, long-lived menu).
 */
export function judge(item: MenuItemView, r: RulesView, merchantBondMicro: number, menuWindowSlots: bigint): ItemVerdict {
  const refuse: string[] = [];
  if (r.frozen) refuse.push('the capsule is frozen');
  if (r.expired) refuse.push('the capsule has expired');
  if (!r.categories.includes(item.category)) refuse.push(`${CATEGORY_NAMES[item.category] ?? `category ${item.category}`} is not an allowed category`);
  if (item.priceMicro > r.perTxCapMicro) refuse.push(`above the ${usd(r.perTxCapMicro)} per-purchase limit`);
  if (item.priceMicro > r.dailyRemainingMicro) refuse.push(`only ${usd(r.dailyRemainingMicro)} left of today's limit`);
  if (refuse.length) return { ...item, verdict: 'refused', reasons: refuse };
  const ask: string[] = [];
  if (item.priceMicro > item.floorMicro) ask.push(`pricier than the cheapest ${CATEGORY_NAMES[item.category] ?? 'item'} (${usd(item.floorMicro)})`);
  if (merchantBondMicro < r.minMerchantBondMicro) ask.push('the merchant is below your bond requirement');
  if (menuWindowSlots > MAX_AUTONOMOUS_WINDOW) ask.push('the menu is valid for longer than an autonomous payment allows');
  return { ...item, verdict: ask.length ? 'needs_owner_approval' : 'autonomous', reasons: ask };
}

const STOP = new Set(['a', 'an', 'the', 'my', 'me', 'i', 'want', 'get', 'buy', 'please', 'some', 'one', 'of', 'for', 'and', 'with', 'usual', 'under']);
const words = (s: string) => s.toLowerCase().split(/[^a-z]+/).filter((w) => w.length > 1 && !STOP.has(w));

/**
 * Best match for a request: most overlapping words, then the cheapest. Refused items are skipped;
 * an explicit word only a pricier item has (e.g. "large") is what makes it the match.
 */
export function bestMatch(request: string, judged: ItemVerdict[]): ItemVerdict | undefined {
  const want = new Set(words(request));
  const scored = judged.filter((j) => j.verdict !== 'refused').map((j) => ({ j, score: words(j.name).filter((w) => want.has(w)).length })).filter((x) => x.score > 0);
  if (!scored.length) return undefined;
  const top = Math.max(...scored.map((x) => x.score));
  return scored.filter((x) => x.score === top).sort((a, b) => a.j.priceMicro - b.j.priceMicro)[0].j;
}

export const usd = (micro: number) => `$${(micro / 1e6).toFixed(2)}`;
