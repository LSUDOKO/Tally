// Demo faucet: give a phone's wallet what the demo needs on a cluster we initialised —
// SOL for fees, test USDC to fund a capsule, mock SKR to bond as a merchant, and (with --sgt) a mock
// Seeker Genesis Token so the Seeker-only features can be tried on any phone.
// Usage: TALLY_CLUSTER=devnet pnpm -C scripts exec tsx faucet.ts <wallet-address> [usdc=50] [skr=5000] [sol=0.2] [--sgt]
import { address, lamports, type Instruction } from '@solana/kit';
import { getTransferSolInstruction } from '@solana-program/system';
import { TOKEN_PROGRAM_ADDRESS, findAssociatedTokenPda, getCreateAssociatedTokenIdempotentInstructionAsync, getMintToInstruction } from '@solana-program/token';
import { loadState, payer, sendOk } from './lib/env';
import { mintSgt } from './lib/sgt';

async function main() {
  const args = process.argv.slice(2);
  const withSgt = args.includes('--sgt');
  const [who, usdcArg = '50', skrArg = '5000', solArg = '0.2'] = args.filter((a) => a !== '--sgt');
  if (!who) throw new Error('usage: faucet.ts <wallet-address> [usdc] [skr] [sol] [--sgt]');
  const to = address(who);
  const s = loadState();
  if (!s.usdcMint || !s.skrMint) throw new Error('run init-devnet.ts first');
  const fee = await payer();
  // Zero amounts are skipped, so `faucet.ts <addr> 0 0 0 --sgt` only mints the token.
  const ixs: Instruction[] = [];
  const sol = BigInt(Math.round(Number(solArg) * 1e9));
  if (sol > 0n) ixs.push(getTransferSolInstruction({ source: fee, destination: to, amount: lamports(sol) }));
  for (const [mint, amountArg] of [[s.usdcMint, usdcArg], [s.skrMint, skrArg]] as const) {
    const amount = BigInt(Math.round(Number(amountArg) * 1e6));
    if (amount === 0n) continue;
    const [ata] = await findAssociatedTokenPda({ owner: to, mint, tokenProgram: TOKEN_PROGRAM_ADDRESS });
    ixs.push(await getCreateAssociatedTokenIdempotentInstructionAsync({ payer: fee, owner: to, mint }));
    ixs.push(getMintToInstruction({ mint, token: ata, mintAuthority: fee, amount }));
  }
  if (ixs.length > 0) await sendOk(fee, ixs, `faucet → ${to}: ${solArg} SOL, ${usdcArg} USDC, ${skrArg} SKR`);
  if (withSgt) {
    if (!s.sgtGroup) throw new Error('run init-devnet.ts first (no SGT collection)');
    await mintSgt(fee, s.sgtGroup, to);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
