// Initialise a cluster for the Tally demo (idempotent):
//   1. test USDC mint (6 dp) and mock SKR mint (6 dp), payer is mint authority
//   2. a Token-2022 "Seeker Genesis Token" collection mint (GroupPointer + TokenGroup)
//   3. the program Config (arbiter = payer, 50% slash, 1,500-slot bond unlock delay)
//
// Usage: TALLY_CLUSTER=devnet pnpm -C scripts init
import { generateKeyPairSigner, type Address, type KeyPairSigner } from '@solana/kit';
import { getCreateAccountInstruction } from '@solana-program/system';
import { getInitializeMint2Instruction, getMintSize, TOKEN_PROGRAM_ADDRESS } from '@solana-program/token';
import {
  TOKEN_2022_PROGRAM_ADDRESS,
  getInitializeGroupPointerInstruction,
  getInitializeMint2Instruction as getInitializeMint2Instruction22,
  getInitializeTokenGroupInstruction,
  getMintSize as getMintSize22,
} from '@solana-program/token-2022';
import { fetchMaybeConfig, findConfigPda, getInitializeConfigInstructionAsync } from '@tally/sdk';
import { CLUSTER, explorerAddress, loadState, payer, rpc, saveState, sendOk } from './lib/env';

async function createClassicMint(fee: KeyPairSigner, decimals: number, label: string): Promise<Address> {
  const mint = await generateKeyPairSigner();
  const space = getMintSize();
  const lamports = await rpc.getMinimumBalanceForRentExemption(BigInt(space)).send();
  await sendOk(
    fee,
    [
      getCreateAccountInstruction({ payer: fee, newAccount: mint, lamports, space, programAddress: TOKEN_PROGRAM_ADDRESS }),
      getInitializeMint2Instruction({ mint: mint.address, decimals, mintAuthority: fee.address }),
    ],
    `create ${label} mint ${mint.address}`,
  );
  return mint.address;
}

async function createSgtGroup(fee: KeyPairSigner): Promise<Address> {
  const group = await generateKeyPairSigner();
  const pointer = { __kind: 'GroupPointer' as const, authority: fee.address, groupAddress: group.address };
  const tokenGroup = { __kind: 'TokenGroup' as const, updateAuthority: fee.address, mint: group.address, size: 0n, maxSize: 1_000_000n };
  const space = getMintSize22([pointer]);
  const lamports = await rpc.getMinimumBalanceForRentExemption(BigInt(getMintSize22([pointer, tokenGroup]))).send();
  await sendOk(
    fee,
    [
      getCreateAccountInstruction({ payer: fee, newAccount: group, lamports, space, programAddress: TOKEN_2022_PROGRAM_ADDRESS }),
      getInitializeGroupPointerInstruction({ mint: group.address, authority: fee.address, groupAddress: group.address }),
      getInitializeMint2Instruction22({ mint: group.address, decimals: 0, mintAuthority: fee.address }),
      getInitializeTokenGroupInstruction({ group: group.address, mint: group.address, mintAuthority: fee, updateAuthority: fee.address, maxSize: 1_000_000n }),
    ],
    `create SGT collection ${group.address}`,
  );
  return group.address;
}

async function main() {
  const fee = await payer();
  const state = loadState();
  console.log(`cluster ${CLUSTER}, payer ${fee.address}`);

  state.usdcMint ??= await createClassicMint(fee, 6, 'test USDC');
  state.skrMint ??= await createClassicMint(fee, 6, 'mock SKR');
  state.sgtGroup ??= await createSgtGroup(fee);
  saveState(state);

  const [config] = await findConfigPda();
  const existing = await fetchMaybeConfig(rpc, config);
  if (existing.exists) {
    console.log(`config already initialised: ${explorerAddress(config)}`);
  } else {
    await sendOk(
      fee,
      [
        await getInitializeConfigInstructionAsync({
          admin: fee,
          usdcMint: state.usdcMint,
          skrMint: state.skrMint,
          arbiter: fee.address,
          sgtGroup: state.sgtGroup,
          slashBps: 5_000,
          bondUnlockDelay: 1_500n,
        }),
      ],
      `initialize_config ${config}`,
    );
  }
  state.config = config;
  saveState(state);
  console.log(JSON.stringify(state, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
