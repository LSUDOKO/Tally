// Plain-language sentences for every Tally error code (shown to users by the app and scripts).
const SENTENCES: Record<number, [string, string]> = {
  6000: ['Frozen', 'This capsule is frozen. Unfreeze it to let your agent pay.'],
  6001: ['Expired', 'This capsule has expired. Create a new one to keep paying.'],
  6002: ['MerchantInactive', 'This merchant is not accepting agent payments right now.'],
  6003: ['MissingSig', "The merchant's menu signature is missing from the transaction."],
  6004: ['BadSigIx', 'The menu signature was malformed or tampered with.'],
  6005: ['WrongSigner', "That menu wasn't signed by the merchant's registered key."],
  6006: ['WrongMessage', "The menu details don't match what the merchant signed."],
  6007: ['Stale', 'That menu has expired. Scan the counter again for a fresh one.'],
  6008: ['WindowTooLong', 'That menu is valid for too long to be trusted automatically.'],
  6009: ['NotOnMenu', "That item isn't on the menu the merchant signed."],
  6010: ['Category', "Your rules don't allow this category."],
  6011: ['OverPerTx', "That's above your per-purchase limit."],
  6012: ['OverDaily', "That would go over today's limit."],
  6013: ['OwnerCosignRequired', "That's pricier than the cheapest match, so your approval is needed."],
  6014: ['WrongMint', "The merchant's payout account uses the wrong token."],
  6015: ['PostCondition', 'The balance check failed, so nothing was paid.'],
  6016: ['LighthouseMissing', 'The required balance assertion was missing or wrong.'],
  6017: ['PowRequired', 'The merchant is busy and needs a quick proof of work. Try again.'],
  6018: ['PowInvalid', 'The proof of work was invalid or too old. Try again.'],
  6019: ['Overflow', 'An amount overflowed. Nothing was paid.'],
  6020: ['Unauthorized', "You're not allowed to do that."],
  6021: ['BondLocked', 'The bond is still locked.'],
  6022: ['DisputeOpen', 'The merchant has an open dispute.'],
  6023: ['NotAFraud', "Those items don't prove a pricing contradiction."],
  6024: ['InvalidSgt', "This device's Seeker Genesis Token couldn't be verified."],
  6025: ['InvalidRules', "Those capsule rules aren't valid."],
  6026: ['WrongDisputeState', 'That dispute is already resolved.'],
  6027: ['SelfReport', 'A merchant cannot report itself.'],
  6028: ['EvidenceExpired', 'That evidence was signed with a key retired too long ago.'],
  2001: ['ConstraintHasOne', "The payee or account doesn't match the merchant's registry."],
};

export function errorName(code: number): string | undefined {
  return SENTENCES[code]?.[0];
}

export function explainError(code: number): string {
  return SENTENCES[code]?.[1] ?? `Transaction failed (code ${code}).`;
}
