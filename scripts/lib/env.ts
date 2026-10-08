// Shared plumbing for Tally scripts: cluster selection, key store, sending with explorer links.
//
//   TALLY_CLUSTER=devnet   (default) https://api.devnet.solana.com
//   TALLY_CLUSTER=localnet http://127.0.0.1:8899 (solana-test-validator)
//   TALLY_RPC_URL / TALLY_WS_URL override either.
//
// State and generated demo keys live in ~/.config/tally/<cluster>/ and are never committed.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import * as ed from '@noble/ed25519';
import {
  appendTransactionMessageInstructions,
  createKeyPairSignerFromBytes,
  createSolanaRpc,
  createSolanaRpcSubscriptions,
  createTransactionMessage,
  getSignatureFromTransaction,
  pipe,
  sendAndConfirmTransactionFactory,
  setTransactionMessageFeePayerSigner,
  setTransactionMessageLifetimeUsingBlockhash,
  signTransactionMessageWithSigners,
  type Address,
  type Instruction,
  type KeyPairSigner,
} from '@solana/kit';

export const CLUSTER = (process.env.TALLY_CLUSTER ?? 'devnet') as 'devnet' | 'localnet';
const RPC_URL = process.env.TALLY_RPC_URL ?? (CLUSTER === 'localnet' ? 'http://127.0.0.1:8899' : 'https://api.devnet.solana.com');
const WS_URL = process.env.TALLY_WS_URL ?? RPC_URL.replace('https://', 'wss://').replace('http://', 'ws://').replace(':8899', ':8900');

export const rpc = createSolanaRpc(RPC_URL);
export const rpcSubscriptions = createSolanaRpcSubscriptions(WS_URL);
const sendAndConfirm = sendAndConfirmTransactionFactory({ rpc, rpcSubscriptions });

export const STATE_DIR = join(homedir(), '.config', 'tally', CLUSTER);
mkdirSync(STATE_DIR, { recursive: true });

export function explorerTx(sig: string): string {
  const q = CLUSTER === 'localnet' ? `?cluster=custom&customUrl=${encodeURIComponent(RPC_URL)}` : '?cluster=devnet';
  return `https://explorer.solana.com/tx/${sig}${q}`;
}

export function explorerAddress(a: string): string {
  const q = CLUSTER === 'localnet' ? `?cluster=custom&customUrl=${encodeURIComponent(RPC_URL)}` : '?cluster=devnet';
  return `https://explorer.solana.com/address/${a}${q}`;
}

/** Solana CLI wallet (fee payer, mint authority, arbiter in the demo). */
export async function payer(): Promise<KeyPairSigner> {
  const path = process.env.TALLY_PAYER ?? join(homedir(), '.config', 'solana', 'id.json');
  return createKeyPairSignerFromBytes(new Uint8Array(JSON.parse(readFileSync(path, 'utf8'))));
}

/** Load or create a named keypair in the key store (Solana CLI JSON format). */
export async function namedSigner(name: string): Promise<KeyPairSigner> {
  const path = join(STATE_DIR, `${name}.json`);
  if (!existsSync(path)) {
    const secret = ed.utils.randomSecretKey();
    const full = new Uint8Array(64);
    full.set(secret);
    full.set(await ed.getPublicKeyAsync(secret), 32);
    writeFileSync(path, JSON.stringify(Array.from(full)), { mode: 0o600 });
  }
  return createKeyPairSignerFromBytes(new Uint8Array(JSON.parse(readFileSync(path, 'utf8'))));
}

/** Load or create a raw 32-byte Ed25519 secret (the merchant's menu-signing key). */
export function namedSecret(name: string): Uint8Array {
  const path = join(STATE_DIR, `${name}.secret.json`);
  if (!existsSync(path)) writeFileSync(path, JSON.stringify(Array.from(ed.utils.randomSecretKey())), { mode: 0o600 });
  return new Uint8Array(JSON.parse(readFileSync(path, 'utf8')));
}

export interface DemoState {
  usdcMint?: Address;
  skrMint?: Address;
  sgtGroup?: Address;
  config?: Address;
  merchant?: Address;
  merchantPayout?: Address;
  sgtMint?: Address;
  capsule?: Address;
  vault?: Address;
}

const STATE_FILE = join(STATE_DIR, 'state.json');
export function loadState(): DemoState {
  return existsSync(STATE_FILE) ? JSON.parse(readFileSync(STATE_FILE, 'utf8')) : {};
}
export function saveState(s: DemoState): void {
  writeFileSync(STATE_FILE, JSON.stringify(s, null, 2));
}

export interface SendResult {
  signature: string;
  ok: boolean;
  error?: string;
  code?: number;
  logs?: string[];
}

/**
 * Sign and send. With `land: true` preflight is skipped so a failing transaction still lands
 * on-chain and shows its revert on the explorer (used by the attack demo).
 */
export async function send(feePayer: KeyPairSigner, instructions: Instruction[], opts: { land?: boolean } = {}): Promise<SendResult> {
  const { value: blockhash } = await rpc.getLatestBlockhash().send();
  const message = pipe(
    createTransactionMessage({ version: 0 }),
    (m) => setTransactionMessageFeePayerSigner(feePayer, m),
    (m) => setTransactionMessageLifetimeUsingBlockhash(blockhash, m),
    (m) => appendTransactionMessageInstructions(instructions, m),
  );
  const tx = await signTransactionMessageWithSigners(message);
  const signature = getSignatureFromTransaction(tx);
  try {
    await sendAndConfirm(tx as Parameters<typeof sendAndConfirm>[0], { commitment: 'confirmed', skipPreflight: opts.land ?? false });
    return { signature, ok: true };
  } catch (e) {
    const text = String((e as Error)?.message ?? e);
    const cause = JSON.stringify((e as { cause?: unknown }).cause ?? (e as { context?: unknown }).context ?? {});
    const m = /custom program error: (0x[0-9a-f]+)|"Custom":(\d+)|code[":\s]+(\d{4})/i.exec(text + cause);
    const code = m ? (m[1] ? parseInt(m[1], 16) : Number(m[2] ?? m[3])) : undefined;
    return { signature, ok: false, error: text.split('\n')[0], code };
  }
}

/** Throw unless the send succeeded. */
export async function sendOk(feePayer: KeyPairSigner, instructions: Instruction[], what: string): Promise<string> {
  const r = await send(feePayer, instructions);
  if (!r.ok) throw new Error(`${what} failed: ${r.error} (code ${r.code ?? '?'})\n${explorerTx(r.signature)}`);
  console.log(`✓ ${what}  ${explorerTx(r.signature)}`);
  return r.signature;
}

/** Program error code of a landed, failed transaction (reads the confirmed meta). */
export async function landedErrorCode(signature: string): Promise<number | undefined> {
  for (let i = 0; i < 20; i++) {
    const tx = await rpc
      .getTransaction(signature as Parameters<typeof rpc.getTransaction>[0], { maxSupportedTransactionVersion: 0, commitment: 'confirmed', encoding: 'json' })
      .send();
    if (tx?.meta) {
      const err = tx.meta.err as unknown;
      const s = JSON.stringify(err, (_, v) => (typeof v === 'bigint' ? Number(v) : v));
      const m = /"Custom":(\d+)/.exec(s);
      return m ? Number(m[1]) : undefined;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  return undefined;
}
