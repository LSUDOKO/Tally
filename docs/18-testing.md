# 18 — Testing

> **As built:** 155 Rust tests pass (`cargo test --workspace`). That's 34 `tally-core` unit and golden-vector tests, 2 program unit tests and 119 LiteSVM integration tests across `merchant`, `capsule`, `introspection`, `menu`, `policy`, `settlement`, `lighthouse`, `slashing`, `dispute`, `sgt`, `pow` and `compute`. The row → test mapping is in [`tests/litesvm/THREAT_SUITE.md`](../tests/litesvm/THREAT_SUITE.md). The planned table below is kept for reference.

## Program tests (one per threat-model row, plus happy paths)

| Test | Setup | Expect |
|---|---|---|
| happy_path_cheapest | Price == floor, agent signs only, Live window | Success; receipt; vault −P; payout +P |
| cosign_required | Price > floor, owner not signing | `OwnerCosignRequired` |
| cosign_ok | Price > floor, owner signs | Success, `cosigned = true` |
| payee_swap | Attacker token account as `payout` | `ConstraintHasOne` |
| price_inflation | Leaf price changed, same proof | `NotOnMenu` |
| unshown_item | Item not in the tree | `NotOnMenu` |
| node_as_leaf | Pass an internal node as a leaf | `NotOnMenu` |
| proof_too_long | 17+ siblings | `NotOnMenu` |
| forged_menu | Header signed by an unregistered key | `WrongSigner` |
| wrong_message | Valid signature over a different header | `WrongMessage` |
| wrong_merchant_pda | Header signed for merchant X, paid via merchant Y | `WrongMessage` |
| missing_ed25519 | No Ed25519 instruction | `MissingSig` |
| ed25519_not_adjacent | ComputeBudget between Ed25519 and `pay_offer` | `MissingSig` |
| ed25519_offset_attack | `message_instruction_index` points at another instruction | `BadSigIx` |
| ed25519_offset_into_header | `msg_off` < 16 | `BadSigIx` |
| ed25519_oob | `msg_off + msg_len` > data len | `BadSigIx` |
| ed25519_two_sigs | `num_signatures = 2` | `BadSigIx` |
| ed25519_with_accounts | Precompile instruction with accounts attached | `BadSigIx` |
| stale_menu | Warp past `expires_slot` | `Stale` |
| not_yet_valid | Slot < `issued_slot` | `Stale` |
| window_151_no_cosign | Window 151, agent only | `OwnerCosignRequired` (or `WindowTooLong` if the capsule disallows extended windows) |
| window_too_long | Window 9,001 | `WindowTooLong` |
| replay | Pay the same `(menu_nonce, item)` twice | Second fails (account in use) |
| wrong_agent | Signed by another key | Constraint failure |
| frozen | Freeze, then pay | `Frozen` |
| expired_capsule | Slot ≥ `expiry_slot` | `Expired` |
| category_blocked | Category bit off | `Category` |
| category_255 / category_256 | Edge of bitmap | OK / `Category` |
| over_per_tx | Price > cap | `OverPerTx` |
| over_daily | Several buys exceed the daily cap | `OverDaily` |
| cycle_reset | Warp 216,000 slots | Spending resets |
| low_bond | Bond < capsule minimum, no owner signature | `OwnerCosignRequired` |
| wrong_mint | Payout in another mint | `WrongMint` |
| fee_mint_postcondition | Token-2022 mint with transfer fee as payout path | `PostCondition` |
| lighthouse_missing | `REQUIRE_LIGHTHOUSE` set, no terminal Lighthouse instruction | `LighthouseMissing` |
| lighthouse_ok | Terminal Lighthouse assertion on the vault | Success |
| floor_fraud_proof | Signed menu with `price_a < floor_b`, same category | Slash; `Slashed` event |
| floor_fraud_false | Consistent menu submitted as fraud | Rejected, no slash |
| bond_unlock_blocked | Withdraw before the delay, or with an open dispute | Error |
| pow_required | Merchant difficulty 8, no PoW | `PowRequired` |
| pow_invalid / pow_reused | Wrong digest; solution from another capsule or menu | `PowInvalid` |
| pow_ok | Valid DrillX solution | Success |
| sgt_ok / sgt_wrong_mint / sgt_wrong_owner | `create_capsule` with an SGT account | Flag set / errors |
| sgt_double_claim | `claim_promo` twice with the same SGT | Second fails |

Use `solana-program-test`, `bankrun` or LiteSVM with slot warping. **Log CU for every passing `pay_offer`.** Target < 45k without PoW.

## Cross-language vectors

`fixtures/menu-v1.json` must give identical leaves, roots, 144-byte headers and proofs in:

- Rust (program tests)
- TypeScript (scripts/SDK)
- Kotlin (app unit tests)

It also includes an HCE APDU transcript and one fraud menu.

## App tests

| Area | Tests |
|---|---|
| Menu decode/verify | Valid, bad signature, root mismatch, expired, wrong domain, wrong `hash_alg`, floor contradiction |
| Context firewall | Names with newlines, URLs or instructions → dictionary tokens only; descriptions never in the prompt; output schema rejects extras and unknown IDs |
| Tx builder | Order is `[CB] · Ed25519 · pay_offer · Lighthouse`; correct accounts; correct header bytes |
| Second Look | Gross outflow lists every decreasing account; extra instruction → red banner, Approve disabled |
| HCE | SELECT, length, chunking at 240 B, payload swap during rotation |
| Keystore | Ed25519 available → hardware key; unavailable → fallback path |
| Error mapping | Each Tally error → one plain sentence |

## Device checklist before recording

- [ ] Fresh install from the release APK works on a physical Seeker
- [ ] MWA connect, SIWS and Seed Vault approval on the demo device
- [ ] Scan works in the demo lighting; NFC tap works phone to phone
- [ ] All red-team attacks give the expected result twice in a row
- [ ] PoW solve time is acceptable at the demo difficulty
- [ ] Explorer links open the right transactions
- [ ] Airplane-mode behaviour is graceful
- [ ] Battery saver doesn't kill Merchant mode rotation or HCE
