# Tally Phase 2 — TypeScript SDK, devnet scripts and CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (chosen) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a typed TypeScript SDK for the deployed program, plus scripts that deploy to devnet, seed the demo world (mints, config, merchant, capsule) and replay the demo attacks with explorer links. Add GitHub Actions CI that keeps every phase green.

**Architecture:** `sdk/` is a pnpm TypeScript package. A Codama-generated client (from `idl/tally.json`) provides instruction builders and account decoders. `sdk/src/menu.ts` re-implements the byte layouts of `tally-core` and is pinned by `fixtures/menu-v1.json`. `sdk/src/pay.ts` assembles the exact transaction layout from docs/06 (`[CB…] · Ed25519 · pay_offer · Lighthouse?`). `sdk/src/errors.ts` maps every error code to a plain sentence (used by phase 3's copy). `scripts/*.ts` run with `tsx` against devnet.

**Tech Stack:** Node 22, pnpm 10, TypeScript, `@solana/kit` 8.4, `codama` 1.11 + `@codama/nodes-from-anchor` 1.5 + `@codama/renderers-js` 2.5, `@solana-program/{token,system,compute-budget}`, `@noble/ed25519` 3, `@noble/hashes` 2, vitest.

**Spec:** `docs/06-onchain-program.md` (as built), `docs/07-menu-and-qr-spec.md`, `docs/14-demo-script.md`, `idl/tally.json`, `fixtures/menu-v1.json`.

## Global Constraints

- Byte layouts must match `fixtures/menu-v1.json` exactly: leaves, root, proofs, 144-byte header, Ed25519 instruction data.
- Transaction order: optional ComputeBudget instructions **before** Ed25519; Ed25519 **immediately before** `pay_offer`; the optional Lighthouse assertion **last**.
- Program ID `3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk`; cluster devnet; RPC from `TALLY_RPC_URL` (default `https://api.devnet.solana.com`).
- Never commit keypairs. Scripts read `~/.config/solana/id.json` (payer) and write generated demo keypairs to `~/.config/tally/demo/`.
- Conventional Commits, no AI co-author trailer.

## Review Focus

1. Endianness of u16/u32/u64 fields in the TS header and leaf. *(Pinned by the fixtures test, Task 2.)*
2. Odd-sized menus in the TS Merkle tree. *(Test: sizes 1..17 round-trip, Task 2.)*
3. A blockhash expiring between simulation and send in scripts → retry with a fresh blockhash, never a duplicate payment (the receipt nullifier makes a retry safe). *(Task 4 `sendWithRetry`.)*
4. Devnet slot drift: a menu window of 100 slots must be signed immediately before sending. *(Task 3 builder signs at build time from `getSlot`.)*
5. Error codes not in the map → show the raw code, never crash. *(Task 3 test.)*

---

### Task 1: SDK package scaffold + Codama client

**Files:** `sdk/package.json`, `sdk/tsconfig.json`, `sdk/codama.mjs`, `sdk/src/generated/**` (generated), `sdk/src/index.ts`, `pnpm-workspace.yaml`, root `package.json`.

- [ ] Step 1: `pnpm init` in the workspace. Add dev deps `typescript tsx vitest codama @codama/nodes-from-anchor @codama/renderers-js` and deps `@solana/kit @solana-program/token @solana-program/system @solana-program/compute-budget @noble/ed25519 @noble/hashes`.
- [ ] Step 2: `sdk/codama.mjs`:

```js
import { createFromRoot } from 'codama';
import { rootNodeFromAnchor } from '@codama/nodes-from-anchor';
import { renderVisitor } from '@codama/renderers-js';
import { readFileSync } from 'node:fs';
const idl = JSON.parse(readFileSync(new URL('../idl/tally.json', import.meta.url)));
createFromRoot(rootNodeFromAnchor(idl)).accept(renderVisitor(new URL('./src/generated', import.meta.url).pathname));
```

- [ ] Step 3: `pnpm -C sdk codegen && pnpm -C sdk tsc --noEmit`. Expected: compiles. Generated code is committed so judges can read it without running codegen.
- [ ] Step 4: Commit `sdk: scaffold typescript sdk with codama-generated client`.

### Task 2: `menu.ts` (byte-exact port of tally-core)

**Interfaces:** `buildHeader(f: HeaderFields): Uint8Array` (144 B); `leafHash(i: Item): Uint8Array`; `nodeHash(a, b)`; `class MerkleTree { constructor(leaves); root(); proof(i) }`; `verifyProof(leaf, proof, root)`; `computeFloors(items)`; `findFloorContradiction(items): [number, number] | null`; `ed25519IxData(pubkey, sig, msg): Uint8Array`; `powChallenge(nonce: bigint, capsule: Uint8Array, slotHash: Uint8Array)`; `solveHashcash(challenge, difficulty): Uint8Array`.

- [ ] Step 1: Write `sdk/test/menu.test.ts`. Load `../../fixtures/menu-v1.json` and assert every `leaf_hex`, `root_hex`, every `proof_hex`, `header_hex`, `ed25519_ix_data_hex`, the fraud `cheap_index/claimed_index`, and the PoW `challenge_hex`. Also round-trip proofs for sizes 1..17.
- [ ] Step 2: Run `pnpm -C sdk vitest run` → FAIL (module missing).
- [ ] Step 3: Implement `sdk/src/menu.ts` with `@noble/hashes/sha2` `sha256`, using `DataView` little-endian writes that mirror `crates/tally-core/src/{header,menu,merkle,ed25519,pow}.rs` line for line.
- [ ] Step 4: Run → PASS. Commit `sdk: port menu header, leaves, merkle and ed25519 data with fixture parity`.

### Task 3: `pay.ts` transaction builder + `errors.ts`

**Interfaces:** `buildPayOfferTx({ rpcSlot, merchant, capsule, items, index, signer, agent, owner?, nonce, window, lighthouse?, pow?, priorityMicroLamports? }): { instructions, header, receipt }`; `TALLY_ERRORS: Record<number, { name, sentence }>`; `explainError(code: number): string`.

- [ ] Step 1: Tests:
  - instruction order and ids;
  - Ed25519 data parses back to the same header;
  - receipt PDA seeds `[receipt, capsule, nonce LE, item_id]`;
  - `explainError(6013)` → "That's pricier than the cheapest match, so your approval is needed.";
  - `explainError(9999)` → "Transaction failed (code 9999)".
- [ ] Step 2: RED → implement → GREEN.
- [ ] Step 3: Commit as two commits: `sdk: build pay_offer transactions in the canonical instruction order` and `sdk: map program error codes to plain-language sentences`.

### Task 4: Devnet scripts

**Files:** `scripts/deploy-devnet.sh`, `scripts/lib/env.ts` (rpc, payer, keypair store, `sendWithRetry`, explorer URL), `scripts/init-devnet.ts`, `scripts/seed-demo.ts`, `scripts/attack-demo.ts`.

- [ ] `deploy-devnet.sh`: `cargo build-sbf`, `solana program deploy target/deploy/tally.so --program-id ~/.config/tally/tally-program-keypair.json -u d`. It prints the program ID and the explorer link, and aborts early with a clear message if the payer balance is below the deploy cost: `solana rent $(stat -c%s target/deploy/tally.so)` × 2 + 0.5 SOL.
- [ ] `init-devnet.ts` (idempotent): creates a test USDC mint (6 dp) and a mock SKR mint (6 dp) whose mint authority is the payer; creates a Token-2022 SGT group mint; calls `initialize_config` (arbiter = payer, slash 50%, unlock delay 1,500 slots); writes `~/.config/tally/demo/devnet.json` (mints, config).
- [ ] `seed-demo.ts`: merchant authority + signing key (saved), `register_merchant` with a 5,000 SKR bond, owner + agent keys, `create_capsule` (coffee rules) funded with $20 USDC, and an SGT minted to the owner.
- [ ] `attack-demo.ts`: one honest pay plus payee swap, phantom add-on, stale menu, offset hijack and Whisper-without-co-sign. It prints a table of `{attack, result, error, explorer}`, and writes it to `docs/DEVNET.md` with the transaction signatures.
- [ ] Commits: `scripts: add devnet deploy with balance preflight`, `scripts: initialise devnet config, test mints and SGT group`, `scripts: seed demo merchant, capsule and SGT`, `scripts: replay demo attacks against devnet with explorer links`.

### Task 5: CI

**Files:** `.github/workflows/ci.yml`.

- [ ] Jobs:
  - `rust`: install Rust 1.97.1 and Solana CLI 4.0.x (cache `~/.cache/solana`), then `cargo fmt --check`, `cargo clippy -D warnings`, `cargo build-sbf`, `cargo test --workspace`.
  - `sdk`: pnpm install, `pnpm -C sdk vitest run`, `pnpm -C sdk tsc --noEmit`.
- [ ] Commit `ci: run rust, program and sdk test suites on every push`.

### Task 6: Devnet deployment record

- [ ] Run `deploy-devnet.sh` → `init-devnet.ts` → `seed-demo.ts` → `attack-demo.ts`.
- [ ] Commit `docs/DEVNET.md` (program ID, config/mints, merchant, capsule, signatures) as `docs: record devnet deployment and attack transcript`.
- [ ] **Requires:** ~8.5 devnet SOL on the payer (the deploy needs program-data rent plus a same-size buffer while writing).
