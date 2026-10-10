import { describe, expect, it } from 'vitest';
import { allows, bestMatch, judge, rulesView, type CapsuleFields, type MenuItemView } from '../src/policy';

const capsule: CapsuleFields = {
  perTxCap: 6_000_000n,
  dailyCap: 15_000_000n,
  spentInCycle: 10_400_000n,
  cycleStartSlot: 1_000n,
  allowedCategories: [8n | 1n, 0n, 0n, 0n], // coffee (3) and food (0)
  minMerchantBond: 1_000_000_000n,
  expirySlot: 10_000_000n,
  frozen: false,
};

const menu: MenuItemView[] = [
  { id: '01', name: 'Oat latte (regular)', category: 3, priceMicro: 4_500_000, floorMicro: 4_500_000 },
  { id: '02', name: 'Oat latte (large)', category: 3, priceMicro: 5_900_000, floorMicro: 4_500_000 },
  { id: '03', name: 'Reserve roast', category: 3, priceMicro: 18_000_000, floorMicro: 4_500_000 },
  { id: '04', name: 'Metro single', category: 4, priceMicro: 2_750_000, floorMicro: 2_750_000 },
];

describe('policy', () => {
  it('reads the 256-bit category mask', () => {
    expect(allows(capsule.allowedCategories, 3)).toBe(true);
    expect(allows(capsule.allowedCategories, 0)).toBe(true);
    expect(allows(capsule.allowedCategories, 4)).toBe(false);
  });

  it("computes today's remaining budget like pay_offer, including the cycle reset", () => {
    expect(rulesView(capsule, 2_000n).dailyRemainingMicro).toBe(4_600_000);
    expect(rulesView(capsule, 1_000n + 216_000n).dailyRemainingMicro).toBe(15_000_000);
  });

  it('judges each item: autonomous, needs approval, or refused with reasons', () => {
    const r = rulesView({ ...capsule, spentInCycle: 0n }, 2_000n);
    const [reg, large, roast, metro] = menu.map((m) => judge(m, r, 5_000_000_000, 120n));
    expect(reg.verdict).toBe('autonomous');
    expect(large.verdict).toBe('needs_owner_approval');
    expect(roast.verdict).toBe('refused');
    expect(roast.reasons[0]).toContain('per-purchase');
    expect(metro.verdict).toBe('refused');
    expect(metro.reasons[0]).toContain('Transit');
  });

  it('an under-bonded merchant or a long-lived menu needs the owner', () => {
    const r = rulesView({ ...capsule, spentInCycle: 0n }, 2_000n);
    expect(judge(menu[0], r, 0, 120n).verdict).toBe('needs_owner_approval');
    expect(judge(menu[0], r, 5_000_000_000, 600n).verdict).toBe('needs_owner_approval');
  });

  it('frozen capsules refuse everything', () => {
    const r = rulesView({ ...capsule, frozen: true }, 2_000n);
    expect(judge(menu[0], r, 5_000_000_000, 120n).verdict).toBe('refused');
  });

  it('picks the cheapest best match unless the request names something only a pricier item has', () => {
    const r = rulesView({ ...capsule, spentInCycle: 0n }, 2_000n);
    const judged = menu.map((m) => judge(m, r, 5_000_000_000, 120n));
    expect(bestMatch('an oat latte please', judged)?.id).toBe('01');
    expect(bestMatch('large oat latte', judged)?.id).toBe('02');
    expect(bestMatch('metro ticket', judged)).toBeUndefined(); // refused category
  });
});
