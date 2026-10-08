// Demo faucet: give a phone's wallet what the demo needs on a cluster we initialised —
// SOL for fees, test USDC to fund a capsule, mock SKR to bond as a merchant.
// Usage: TALLY_CLUSTER=devnet pnpm -C scripts exec tsx faucet.ts <wallet-address> [usdc=50] [skr=5000] [sol=0.2]
import { address, lamports, type Instruction } from '@solana/kit';
import { getTransferSolInstruction } from '@solana-program/system';
import { TOKEN_PROGRAM_ADDRESS, findAssociatedTokenPda, getCreateAssociatedTokenIdempotentInstructionAsync, getMintToInstruction } from '@solana-program/token';
import { loadState, payer, sendOk } from './lib/env';

async function main() {
  const [who, usdcArg = '50', skrArg = '5000', solArg = '0.2'] = process.argv.slice(2);
  if (!who) throw new Error('usage: faucet.ts <wallet-address> [usdc] [skr] [sol]');
  const to = address(who);
  const s = loadState();
  if (!s.usdcMint || !s.skrMint) throw new Error('run init-devnet.ts first');
  const fee = await payer();
  const ixs: Instruction[] = [getTransferSolInstruction({ source: fee, destination: to, amount: lamports(BigInt(Math.round(Number(solArg) * 1e9))) })];
  for (const [mint, amount] of [[s.usdcMint, usdcArg], [s.skrMint, skrArg]] as const) {
    const [ata] = await findAssociatedTokenPda({ owner: to, mint, tokenProgram: TOKEN_PROGRAM_ADDRESS });
    ixs.push(await getCreateAssociatedTokenIdempotentInstructionAsync({ payer: fee, owner: to, mint }));
    ixs.push(getMintToInstruction({ mint, token: ata, mintAuthority: fee, amount: BigInt(Math.round(Number(amount) * 1e6)) }));
  }
  await sendOk(fee, ixs, `faucet → ${to}: ${solArg} SOL, ${usdcArg} USDC, ${skrArg} SKR`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
