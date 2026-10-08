import { describe, expect, it } from 'vitest';
import { AccountRole, address, generateKeyPairSigner, getAddressEncoder, getProgramDerivedAddress } from '@solana/kit';
import * as ed from '@noble/ed25519';
import { ED25519_PROGRAM, LIGHTHOUSE_PROGRAM, buildPayOfferInstructions, receiptAddress, signMenu } from '../src/pay';
import { explainError } from '../src/errors';
import { TALLY_PROGRAM_ADDRESS } from '../src/generated';
import { buildHeader, computeFloors, type Item } from '../src/menu';

const items: Item[] = computeFloors([
  { id: new Uint8Array(16).fill(1), category: 3, price: 4_500_000n, floor: 0n },
  { id: new Uint8Array(16).fill(2), category: 3, price: 5_900_000n, floor: 0n },
  { id: new Uint8Array(16).fill(3), category: 0, price: 3_000_000n, floor: 0n },
]);

async function setup() {
  const merchant = address('8ZyR1aZBgJTBqdM9fAwnP5vzDMsKkZ9DvWEPF2WX5G3G');
  const secret = ed.utils.randomSecretKey();
  const menu = await signMenu({ merchant, issuedSlot: 100n, expiresSlot: 200n, menuNonce: 7n, items }, secret);
  const agent = await generateKeyPairSigner();
  const owner = await generateKeyPairSigner();
  const capsule = { address: address('9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin'), owner: owner.address, vault: address('Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB'), mint: address('EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v') };
  return { merchant, menu, agent, owner, capsule, payout: address('So11111111111111111111111111111111111111112') };
}

describe('pay_offer transaction builder', () => {
  it('emits [compute budget] · ed25519 · pay_offer · lighthouse in order', async () => {
    const s = await setup();
    const ixs = await buildPayOfferInstructions({ menu: s.menu, index: 0, capsule: s.capsule, agent: s.agent, payout: s.payout, computeUnitLimit: 60_000, lighthouseVaultAfter: 15_500_000n });
    const ids = ixs.map((i) => i.programAddress);
    expect(ids[0]).toBe('ComputeBudget111111111111111111111111111111');
    expect(ids.slice(-3)).toEqual([ED25519_PROGRAM, TALLY_PROGRAM_ADDRESS, LIGHTHOUSE_PROGRAM]);
  });

  it('carries the signed 144-byte header inside the precompile data', async () => {
    const s = await setup();
    const [ed25519] = await buildPayOfferInstructions({ menu: s.menu, index: 0, capsule: s.capsule, agent: s.agent, payout: s.payout });
    const header = buildHeader({ programId: new Uint8Array(getAddressEncoder().encode(TALLY_PROGRAM_ADDRESS)), merchantPda: new Uint8Array(getAddressEncoder().encode(s.merchant)), menuRoot: s.menu.root, issuedSlot: 100n, expiresSlot: 200n, menuNonce: 7n, itemCount: 3 });
    expect(Buffer.from(ed25519.data!.slice(112)).equals(Buffer.from(header))).toBe(true);
    expect(await ed.verifyAsync(ed25519.data!.slice(48, 112), header, ed25519.data!.slice(16, 48))).toBe(true);
  });

  it('derives the receipt PDA from (capsule, merchant, nonce, item)', async () => {
    const s = await setup();
    const enc = getAddressEncoder();
    const nonce = new Uint8Array(8);
    new DataView(nonce.buffer).setBigUint64(0, 7n, true);
    const [expected] = await getProgramDerivedAddress({ programAddress: TALLY_PROGRAM_ADDRESS, seeds: [new TextEncoder().encode('receipt'), enc.encode(s.capsule.address), enc.encode(s.merchant), nonce, items[1].id] });
    expect(await receiptAddress(s.capsule.address, s.merchant, 7n, items[1].id)).toBe(expected);
  });

  it('marks the owner as signer only when a co-sign is supplied', async () => {
    const s = await setup();
    const plain = await buildPayOfferInstructions({ menu: s.menu, index: 1, capsule: s.capsule, agent: s.agent, payout: s.payout });
    const cosigned = await buildPayOfferInstructions({ menu: s.menu, index: 1, capsule: s.capsule, agent: s.agent, payout: s.payout, ownerSigner: s.owner });
    const ownerRole = (ixs: typeof plain) => ixs[ixs.length - 1].accounts!.find((a) => a.address === s.owner.address)!.role;
    expect(ownerRole(plain)).toBe(AccountRole.READONLY);
    expect(ownerRole(cosigned)).toBe(AccountRole.READONLY_SIGNER);
  });
});

describe('error sentences', () => {
  it('explains known codes in plain language', () => {
    expect(explainError(6013)).toBe("That's pricier than the cheapest match, so your approval is needed.");
    expect(explainError(6009)).toBe("That item isn't on the menu the merchant signed.");
  });
  it('never crashes on unknown codes', () => {
    expect(explainError(9999)).toBe('Transaction failed (code 9999).');
  });
});
