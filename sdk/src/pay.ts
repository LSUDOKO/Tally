// Assembles pay_offer transactions in the canonical order the program requires (docs/06):
//   [ComputeBudget…] · Ed25519 verify(header) · pay_offer · [Lighthouse AssertTokenAccount]
// ComputeBudget must precede Ed25519 because the program reads the instruction at index cur-1.
import {
  AccountRole,
  address,
  getAddressEncoder,
  getProgramDerivedAddress,
  type Address,
  type Instruction,
  type TransactionSigner,
} from '@solana/kit';
import { getSetComputeUnitLimitInstruction, getSetComputeUnitPriceInstruction } from '@solana-program/compute-budget';
import * as ed from '@noble/ed25519';
import { TALLY_PROGRAM_ADDRESS, getPayOfferInstruction } from './generated';
import { MerkleTree, buildHeader, ed25519IxData, leafHash, u64le, type Item } from './menu';

export const ED25519_PROGRAM = address('Ed25519SigVerify111111111111111111111111111');
export const LIGHTHOUSE_PROGRAM = address('L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95');

const enc = getAddressEncoder();
const bytes = (a: Address) => new Uint8Array(enc.encode(a));

export interface MenuFields {
  merchant: Address;
  issuedSlot: bigint;
  expiresSlot: bigint;
  menuNonce: bigint;
  items: Item[];
}

/** A menu plus the merchant's Ed25519 signature over its 144-byte header. */
export interface SignedMenu extends MenuFields {
  root: Uint8Array;
  header: Uint8Array;
  signerPubkey: Uint8Array;
  signature: Uint8Array;
}

export function menuHeader(m: MenuFields, root: Uint8Array, programId: Address = TALLY_PROGRAM_ADDRESS): Uint8Array {
  return buildHeader({
    programId: bytes(programId),
    merchantPda: bytes(m.merchant),
    menuRoot: root,
    issuedSlot: m.issuedSlot,
    expiresSlot: m.expiresSlot,
    menuNonce: m.menuNonce,
    itemCount: m.items.length,
  });
}

/** Merchant side: build the Merkle root and sign the header with a 32-byte Ed25519 secret key. */
export async function signMenu(m: MenuFields, secretKey: Uint8Array): Promise<SignedMenu> {
  const root = new MerkleTree(m.items.map(leafHash)).root();
  const header = menuHeader(m, root);
  return {
    ...m,
    root,
    header,
    signerPubkey: await ed.getPublicKeyAsync(secretKey),
    signature: await ed.signAsync(header, secretKey),
  };
}

export async function receiptAddress(capsule: Address, merchant: Address, menuNonce: bigint, itemId: Uint8Array): Promise<Address> {
  const [pda] = await getProgramDerivedAddress({
    programAddress: TALLY_PROGRAM_ADDRESS,
    seeds: [new TextEncoder().encode('receipt'), bytes(capsule), bytes(merchant), u64le(menuNonce), itemId],
  });
  return pda;
}

/** Lighthouse `AssertTokenAccount { Amount == amount }` (discriminator 9, Amount variant 2, Equal). */
export function lighthouseAssertAmount(tokenAccount: Address, amount: bigint): Instruction {
  const data = new Uint8Array(12);
  data.set([9, 0, 2]);
  data.set(u64le(amount), 3);
  data[11] = 0;
  return { programAddress: LIGHTHOUSE_PROGRAM, accounts: [{ address: tokenAccount, role: AccountRole.READONLY }], data };
}

export interface PayOfferParams {
  menu: SignedMenu;
  /** Index of the chosen item in `menu.items`. */
  index: number;
  capsule: { address: Address; owner: Address; vault: Address; mint: Address };
  agent: TransactionSigner;
  /** The merchant's registered payout account (from the Merchant registry, never the QR). */
  payout: Address;
  /** Supply when the purchase needs the owner's co-signature (above floor, low bond, extended window). */
  ownerSigner?: TransactionSigner;
  pow?: { nonce: Uint8Array; slot: bigint };
  /** When set, append the exact Lighthouse assertion `vault == lighthouseVaultAfter`. */
  lighthouseVaultAfter?: bigint;
  computeUnitLimit?: number;
  priorityMicroLamports?: bigint;
}

export async function buildPayOfferInstructions(p: PayOfferParams): Promise<Instruction[]> {
  const item = p.menu.items[p.index];
  if (!item) throw new Error(`no item at index ${p.index}`);
  const tree = new MerkleTree(p.menu.items.map(leafHash));
  const out: Instruction[] = [];
  if (p.computeUnitLimit) out.push(getSetComputeUnitLimitInstruction({ units: p.computeUnitLimit }));
  if (p.priorityMicroLamports) out.push(getSetComputeUnitPriceInstruction({ microLamports: p.priorityMicroLamports }));
  out.push({ programAddress: ED25519_PROGRAM, accounts: [], data: ed25519IxData(p.menu.signerPubkey, p.menu.signature, p.menu.header) });

  const pay = getPayOfferInstruction({
    capsule: p.capsule.address,
    agent: p.agent,
    owner: p.capsule.owner,
    merchant: p.menu.merchant,
    mint: p.capsule.mint,
    vault: p.capsule.vault,
    payout: p.payout,
    receipt: await receiptAddress(p.capsule.address, p.menu.merchant, p.menu.menuNonce, item.id),
    // The signed root, never a recomputed one: a tampered item must fail Merkle inclusion.
    menuRoot: p.menu.root,
    issuedSlot: p.menu.issuedSlot,
    expiresSlot: p.menu.expiresSlot,
    menuNonce: p.menu.menuNonce,
    // The signed count (header bytes 136..140), never items.length: appended items must not re-sign.
    itemCount: new DataView(p.menu.header.buffer, p.menu.header.byteOffset).getUint32(136, true),
    itemId: item.id,
    category: item.category,
    price: item.price,
    categoryFloor: item.floor,
    proof: tree.proof(p.index),
    pow: p.pow ? { nonce: p.pow.nonce, slot: p.pow.slot } : null,
  });
  if (p.ownerSigner) {
    const ownerSigner = p.ownerSigner;
    out.push({
      ...pay,
      accounts: pay.accounts.map((a) =>
        a.address === p.capsule.owner ? { address: a.address, role: AccountRole.READONLY_SIGNER, signer: ownerSigner } : a,
      ),
    } as Instruction);
  } else {
    out.push(pay);
  }
  if (p.lighthouseVaultAfter !== undefined) out.push(lighthouseAssertAmount(p.capsule.vault, p.lighthouseVaultAfter));
  return out;
}
