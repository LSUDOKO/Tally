// Replay the demo against a seeded cluster: one honest agent purchase, then the attacks from
// docs/14 (red-team segment). Failing transactions are sent with preflight skipped so they land
// on-chain and their reverts are visible on the explorer. Writes docs/DEVNET.md (devnet only).
//
// Run once per day per capsule: the demo spends $10.40 of the capsule's $15 daily cap.
// Usage: TALLY_CLUSTER=devnet pnpm -C scripts attack
import { writeFileSync } from 'node:fs';
import { type Address } from '@solana/kit';
import { findAssociatedTokenPda, getCreateAssociatedTokenIdempotentInstructionAsync, TOKEN_PROGRAM_ADDRESS } from '@solana-program/token';
import {
  buildPayOfferInstructions,
  computeFloors,
  errorName,
  explainError,
  sha256v,
  signMenu,
  type Item,
  type SignedMenu,
} from '@tally/sdk';
import { CLUSTER, explorerAddress, explorerTx, landedErrorCode, loadState, namedSecret, namedSigner, payer, rpc, send } from './lib/env';

const NAMES = ['Oat latte (regular)', 'Oat latte (large)', 'Cappuccino', 'Reserve roast', 'Croissant', 'Bagel', 'Metro single', 'Day pass'];
const RAW: Array<[number, bigint]> = [
  [3, 4_500_000n],
  [3, 5_900_000n],
  [3, 5_200_000n],
  [3, 18_000_000n],
  [0, 3_000_000n],
  [0, 3_800_000n],
  [4, 2_750_000n],
  [4, 9_000_000n],
];
const menuItems = (): Item[] =>
  computeFloors(RAW.map(([category, price], i) => ({ id: sha256v(new TextEncoder().encode(`tally-item-${i}`)).slice(0, 16), category, price, floor: 0n })));

interface Row {
  scenario: string;
  expected: string;
  result: string;
  signature?: string;
}

async function main() {
  const state = loadState();
  if (!state.merchant || !state.capsule || !state.usdcMint || !state.vault) throw new Error('run init-devnet.ts and seed-demo.ts first');
  const fee = await payer();
  const owner = await namedSigner('capsule-owner');
  const agent = await namedSigner('capsule-agent');
  const authority = await namedSigner('merchant-authority');
  const secret = namedSecret('merchant-menu-key');
  const [payout] = await findAssociatedTokenPda({ owner: authority.address, mint: state.usdcMint, tokenProgram: TOKEN_PROGRAM_ADDRESS });
  const capsule = { address: state.capsule as Address, owner: owner.address, vault: state.vault as Address, mint: state.usdcMint as Address };
  let nonce = BigInt(Date.now());
  const rows: Row[] = [];

  const freshMenu = async (opts: { issuedOffset?: bigint; window?: bigint } = {}): Promise<SignedMenu> => {
    const slot = await rpc.getSlot({ commitment: 'confirmed' }).send();
    const off = opts.issuedOffset ?? 0n;
    const issued = slot + off < 0n ? 0n : slot + off; // young localnets start near slot 0
    return signMenu({ merchant: state.merchant as Address, issuedSlot: issued, expiresSlot: issued + (opts.window ?? 120n), menuNonce: nonce++, items: menuItems() }, secret);
  };

  const run = async (scenario: string, expected: string, build: () => Promise<Parameters<typeof send>[1]>, expectOk: boolean) => {
    // Always land (skip preflight) so every outcome, success or revert, is an on-chain transaction.
    const r = await send(agent, await build(), { land: true });
    let result: string;
    if (r.ok) result = 'paid';
    else {
      // Read the program error code from the landed transaction's meta (authoritative).
      const code = (await landedErrorCode(r.signature)) ?? r.code;
      result = code !== undefined ? `reverted: ${errorName(code) ?? code} (${code}) — ${explainError(code)}` : `reverted: ${r.error}`;
    }
    rows.push({ scenario, expected, result, signature: r.signature });
    console.log(`${r.ok === expectOk ? '✓' : '✗'} ${scenario}: ${result}\n    ${explorerTx(r.signature)}`);
  };

  // 1. Honest purchase: cheapest oat latte, at the category floor → autonomous.
  await run('Honest: agent buys the $4.50 oat latte (cheapest match)', 'paid, no human tap', async () =>
    buildPayOfferInstructions({ menu: await freshMenu(), index: 0, capsule, agent, payout }), true);

  // 2. Payee swap: the QR/agent supplies an attacker payout account.
  const attacker = await namedSigner('attacker');
  const [attackerUsdc] = await findAssociatedTokenPda({ owner: attacker.address, mint: state.usdcMint, tokenProgram: TOKEN_PROGRAM_ADDRESS });
  await send(fee, [await getCreateAssociatedTokenIdempotentInstructionAsync({ payer: fee, owner: attacker.address, mint: state.usdcMint })]);
  await run('Payee swap: pay the attacker instead of the registered payout', 'revert ConstraintHasOne', async () =>
    buildPayOfferInstructions({ menu: await freshMenu(), index: 0, capsule, agent, payout: attackerUsdc }), false);

  // 3. Phantom add-on / price tampering: change an item after the merchant signed.
  await run('Phantom add-on: a $2.99 "service fee" the café never signed', 'revert NotOnMenu', async () => {
    const m = await freshMenu();
    const fee_: Item = { id: new Uint8Array(16).fill(0xfe), category: 3, price: 2_990_000n, floor: 2_990_000n };
    return buildPayOfferInstructions({ menu: { ...m, items: [...m.items, fee_] }, index: m.items.length, capsule, agent, payout });
  }, false);

  // 4. Stale menu: replay a menu whose slot window has passed.
  await run('Stale menu: replay a QR from 300 slots ago', 'revert Stale', async () =>
    buildPayOfferInstructions({ menu: await freshMenu({ issuedOffset: -300n, window: 1n }), index: 0, capsule, agent, payout }), false);

  // 5. Whisper upsell: "regular is discontinued" → agent picks the $5.90 large without the owner.
  await run('Whisper upsell: agent picks the $5.90 large latte on its own', 'revert OwnerCosignRequired (app shows Second Look)', async () =>
    buildPayOfferInstructions({ menu: await freshMenu(), index: 1, capsule, agent, payout }), false);

  // 6. Same upsell, but the owner approves on the Second Look sheet → co-signed purchase.
  await run('Owner approves the large latte on the Second Look sheet', 'paid with owner co-sign', async () =>
    buildPayOfferInstructions({ menu: await freshMenu(), index: 1, capsule, agent, payout, ownerSigner: owner }), true);

  console.table(rows.map((r) => ({ scenario: r.scenario, result: r.result })));

  if (CLUSTER === 'devnet') {
    const programId = '3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk';
    const md = [
      '# Devnet deployment',
      '',
      `Generated by \`scripts/attack-demo.ts\` on ${new Date().toISOString()}.`,
      '',
      '| Account | Address |',
      '|---|---|',
      `| Program | [\`${programId}\`](${explorerAddress(programId)}) |`,
      `| Config | [\`${state.config}\`](${explorerAddress(state.config!)}) |`,
      `| Test USDC mint | [\`${state.usdcMint}\`](${explorerAddress(state.usdcMint)}) |`,
      `| Mock SKR mint | [\`${state.skrMint}\`](${explorerAddress(state.skrMint!)}) |`,
      `| SGT collection | [\`${state.sgtGroup}\`](${explorerAddress(state.sgtGroup!)}) |`,
      `| Merchant (Blue Door Coffee) | [\`${state.merchant}\`](${explorerAddress(state.merchant)}) |`,
      `| Capsule | [\`${state.capsule}\`](${explorerAddress(state.capsule)}) |`,
      '',
      '## Demo transcript',
      '',
      '| Scenario | Expected | Result | Transaction |',
      '|---|---|---|---|',
      ...rows.map((r) => `| ${r.scenario} | ${r.expected} | ${r.result} | [view](${explorerTx(r.signature!)}) |`),
      '',
    ].join('\n');
    writeFileSync(new URL('../docs/DEVNET.md', import.meta.url), md);
    console.log('wrote docs/DEVNET.md');
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
