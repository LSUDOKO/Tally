// Mock Seeker Genesis Tokens for devnet/localnet: a Token-2022 mint that is a member of the
// collection init-devnet.ts created (GroupMemberPointer + TokenGroupMember), one token minted to the
// owner. The program checks exactly these extensions (docs/06, SGT check).
import { generateKeyPairSigner, type Address, type KeyPairSigner } from '@solana/kit';
import { getCreateAccountInstruction } from '@solana-program/system';
import {
  TOKEN_2022_PROGRAM_ADDRESS,
  getCreateAssociatedTokenIdempotentInstructionAsync as getCreateAta22,
  getInitializeGroupMemberPointerInstruction,
  getInitializeMint2Instruction as getInitializeMint2Instruction22,
  getInitializeTokenGroupMemberInstruction,
  getMintSize as getMintSize22,
  getMintToInstruction as getMintToInstruction22,
} from '@solana-program/token-2022';
import { rpc, sendOk } from './env';

export async function mintSgt(fee: KeyPairSigner, group: Address, owner: Address): Promise<{ mint: Address; account: Address }> {
  const mint = await generateKeyPairSigner();
  const pointer = { __kind: 'GroupMemberPointer' as const, authority: fee.address, memberAddress: mint.address };
  const member = { __kind: 'TokenGroupMember' as const, mint: mint.address, group, memberNumber: 1n };
  const space = getMintSize22([pointer]);
  const rent = await rpc.getMinimumBalanceForRentExemption(BigInt(getMintSize22([pointer, member]))).send();
  const createAta = await getCreateAta22({ payer: fee, owner, mint: mint.address, tokenProgram: TOKEN_2022_PROGRAM_ADDRESS });
  const account = createAta.accounts[1].address;
  await sendOk(
    fee,
    [
      getCreateAccountInstruction({ payer: fee, newAccount: mint, lamports: rent, space, programAddress: TOKEN_2022_PROGRAM_ADDRESS }),
      getInitializeGroupMemberPointerInstruction({ mint: mint.address, authority: fee.address, memberAddress: mint.address }),
      getInitializeMint2Instruction22({ mint: mint.address, decimals: 0, mintAuthority: fee.address }),
      getInitializeTokenGroupMemberInstruction({ member: mint.address, memberMint: mint.address, memberMintAuthority: fee, group, groupUpdateAuthority: fee }),
      createAta,
      getMintToInstruction22({ mint: mint.address, token: account, mintAuthority: fee, amount: 1n }),
    ],
    `mint Seeker Genesis Token ${mint.address} to ${owner}`,
  );
  return { mint: mint.address, account };
}
