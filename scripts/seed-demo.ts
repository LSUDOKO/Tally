// Seed the demo world on a cluster initialised by init-devnet.ts (idempotent):
//   - merchant "Blue Door Coffee": authority + menu-signing key, USDC payout, 5,000 SKR bond
//   - capsule owner with a Seeker Genesis Token, an agent key, a coffee capsule funded with $20
//
// In the real app the owner is the Seeker's Seed Vault wallet; here it is a script key.
// Usage: TALLY_CLUSTER=devnet pnpm -C scripts seed
import { getAddressDecoder, lamports, type Address, type KeyPairSigner } from '@solana/kit';
import { getTransferSolInstruction } from '@solana-program/system';
import { TOKEN_PROGRAM_ADDRESS, findAssociatedTokenPda, getCreateAssociatedTokenIdempotentInstructionAsync, getMintToInstruction } from '@solana-program/token';
import * as ed from '@noble/ed25519';
import {
  fetchMaybeCapsule,
  fetchMaybeMerchant,
  findCapsulePda,
  findMerchantPda,
  getCreateCapsuleWithSgtInstructionAsync,
  getFundInstruction,
  getRegisterMerchantInstructionAsync,
} from '@tally/sdk';
import { mintSgt } from './lib/sgt';
import { explorerAddress, loadState, namedSecret, namedSigner, payer, rpc, saveState, sendOk } from './lib/env';

const USDC = 1_000_000n;
const CAPSULE_ID = 1n;

async function ata(fee: KeyPairSigner, owner: Address, mint: Address): Promise<Address> {
  const [address] = await findAssociatedTokenPda({ owner, mint, tokenProgram: TOKEN_PROGRAM_ADDRESS });
  await sendOk(fee, [await getCreateAssociatedTokenIdempotentInstructionAsync({ payer: fee, owner, mint })], `ATA ${address}`);
  return address;
}

async function fundSol(fee: KeyPairSigner, to: Address, sol: number) {
  const bal = await rpc.getBalance(to).send();
  if (bal.value < 50_000_000n) {
    await sendOk(fee, [getTransferSolInstruction({ source: fee, destination: to, amount: lamports(BigInt(Math.round(sol * 1e9))) })], `fund ${to} with ${sol} SOL`);
  }
}

async function main() {
  const fee = await payer();
  const state = loadState();
  if (!state.usdcMint || !state.skrMint || !state.sgtGroup) throw new Error('run init-devnet.ts first');

  // ---------------------------------------------------------------- merchant
  const authority = await namedSigner('merchant-authority');
  const signingSecret = namedSecret('merchant-menu-key');
  const signingKey = getAddressDecoder().decode(await ed.getPublicKeyAsync(signingSecret));
  await fundSol(fee, authority.address, 0.1);
  const [merchant] = await findMerchantPda({ authority: authority.address });
  if (!(await fetchMaybeMerchant(rpc, merchant)).exists) {
    const payout = await ata(fee, authority.address, state.usdcMint);
    const authoritySkr = await ata(fee, authority.address, state.skrMint);
    await sendOk(fee, [getMintToInstruction({ mint: state.skrMint, token: authoritySkr, mintAuthority: fee, amount: 5_000n * USDC })], 'mint 5,000 SKR to merchant');
    const name = new Uint8Array(32);
    name.set(new TextEncoder().encode('Blue Door Coffee'));
    await sendOk(
      authority,
      [await getRegisterMerchantInstructionAsync({ authority, payout, skrMint: state.skrMint, authoritySkr, name, signingKey, bond: 5_000n * USDC })],
      `register_merchant ${merchant}`,
    );
    state.merchantPayout = payout;
  }
  state.merchant = merchant;
  saveState(state);

  // ---------------------------------------------------------------- capsule
  const owner = await namedSigner('capsule-owner');
  const agent = await namedSigner('capsule-agent');
  await fundSol(fee, owner.address, 0.2);
  await fundSol(fee, agent.address, 0.2);
  const [capsule] = await findCapsulePda({ owner: owner.address, capsuleId: CAPSULE_ID });
  const [vault] = await findAssociatedTokenPda({ owner: capsule, mint: state.usdcMint, tokenProgram: TOKEN_PROGRAM_ADDRESS });
  if (!(await fetchMaybeCapsule(rpc, capsule)).exists) {
    const sgt = await mintSgt(fee, state.sgtGroup, owner.address);
    state.sgtMint = sgt.mint;
    const slot = await rpc.getSlot().send();
    const categories = [1n << 3n, 0n, 0n, 0n]; // coffee only
    await sendOk(
      owner,
      [
        await getCreateCapsuleWithSgtInstructionAsync({
          owner,
          mint: state.usdcMint,
          sgtTokenAccount: sgt.account,
          sgtMint: sgt.mint,
          capsuleId: CAPSULE_ID,
          rules: {
            perTxCap: 6n * USDC,
            dailyCap: 15n * USDC,
            allowedCategories: categories,
            minMerchantBond: 1_000n * USDC,
            expirySlot: slot + 50_000_000n,
            flags: 0,
          },
          agent: agent.address,
        }),
      ],
      `create_capsule_with_sgt ${capsule}`,
    );
    const ownerUsdc = await ata(fee, owner.address, state.usdcMint);
    await sendOk(fee, [getMintToInstruction({ mint: state.usdcMint, token: ownerUsdc, mintAuthority: fee, amount: 20n * USDC })], 'mint $20 test USDC to owner');
    await sendOk(owner, [getFundInstruction({ owner, capsule, mint: state.usdcMint, vault, ownerToken: ownerUsdc, amount: 20n * USDC })], 'fund capsule with $20');
  }
  state.capsule = capsule;
  state.vault = vault;
  saveState(state);

  console.log(`\nmerchant ${explorerAddress(merchant)}\ncapsule  ${explorerAddress(capsule)}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
