// Write the cluster addresses created by init-devnet.ts into the Android app's assets, so the
// APK knows which test USDC / mock SKR mints and SGT collection to use.
// Usage: TALLY_CLUSTER=devnet pnpm -C scripts exec tsx export-app-config.ts
import { writeFileSync } from 'node:fs';
import { CLUSTER, loadState } from './lib/env';

const s = loadState();
if (!s.usdcMint || !s.skrMint) throw new Error('run init-devnet.ts first');
const cfg = {
  cluster: CLUSTER === 'localnet' ? 'custom' : 'devnet',
  rpcUrl: process.env.TALLY_RPC_URL ?? 'https://api.devnet.solana.com',
  usdcMint: s.usdcMint,
  skrMint: s.skrMint,
  sgtGroup: s.sgtGroup ?? '',
};
const out = new URL('../app/app/src/main/assets/cluster.json', import.meta.url);
writeFileSync(out, JSON.stringify(cfg, null, 2) + '\n');
console.log(`wrote ${out.pathname}\n${JSON.stringify(cfg, null, 2)}`);
