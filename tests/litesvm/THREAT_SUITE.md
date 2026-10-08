# Threat-model test suite

Every on-chain row of [`docs/11-threat-model.md`](../../docs/11-threat-model.md) maps to a LiteSVM test that loads the compiled program, sends the attack transaction, and asserts the **exact** revert code.

Run:

```bash
cargo build-sbf --manifest-path programs/tally/Cargo.toml
cargo test -p tally-litesvm
```

## Attack matrix → tests

| # | Attack (docs/11) | Result | Test(s) | Asserted error |
|---|---|---|---|---|
| 1 | Payee swap | Blocked | `policy::payee_swap` | `ConstraintHasOne` (2001) |
| 2 | Price tampering | Blocked | `menu::price_inflation`, `menu::floor_tampering` | `NotOnMenu` |
| 3 | Phantom add-on | Blocked | `menu::unshown_item`, `menu::proof_too_long` | `NotOnMenu` |
| 4 | Menu forgery | Blocked | `introspection::forged_menu`, `introspection::rotated_key_rejects_old_menu`, `introspection::header_bound_to_merchant_pda` | `WrongSigner` / `WrongMessage` |
| 5 | Duplicate settlement | Blocked | `settlement::replay_same_offer_reverts` | receipt `init` collision |
| 6 | Stale-catalog replay | Blocked | `menu::stale_menu`, `menu::not_yet_valid`, `menu::window_*` | `Stale` / `WindowTooLong` |
| 7 | "Pre-authorised" injection | Blocked | covered by design: no instruction reads conversational state; every pay test asserts on signatures and rules only | — |
| 8 | Ed25519 offset hijack | Blocked | `introspection::ed25519_cross_ix_exploit_shape_rejected` (**mutation-verified**: with the index pin removed the exploit *succeeds*), `ed25519_two_sigs`, `ed25519_with_accounts`, `ed25519_offset_into_header_reverts`, `ed25519_not_adjacent`, `missing_ed25519` | `BadSigIx` / `MissingSig` |
| 9 | Hidden outflow / fee drain | Blocked | `lighthouse::*` (required terminal assertion, wrong amount reverts whole tx); exact deltas in `tally_core::post` unit tests (`fee_mint_shorts_payee`, `extra_drain_from_vault`) | `PostCondition` / `LighthouseMissing` |
| 10 | Floor fraud (contradiction) | Blocked + slashed | `slashing::floor_fraud_proof_slashes_bond`, `honest_menu_is_not_a_fraud`, `different_categories_are_not_a_fraud`, `fraud_signed_by_someone_else_is_rejected`, `same_evidence_cannot_slash_twice`, `a_second_fraudulent_menu_slashes_again` | `NotAFraud` / `WrongSigner` |
| 11 | Settlement flood | Rate-limited | `pow::pow_required`, `pow_invalid_digest`, `pow_reused_across_capsules_is_invalid`, `pow_ok_and_compute_reported` | `PowRequired` / `PowInvalid` |
| 12 | Whisper upsell | Surfaced (co-sign) | `policy::cosign_required_above_floor`, `cosign_satisfies_above_floor`, `impostor_owner_signature_does_not_count`, `settlement::cosigned_receipt_records_flag` | `OwnerCosignRequired` |
| 14 | Under-bonded merchant | Surfaced (co-sign) | `policy::low_bond_requires_cosign` | `OwnerCosignRequired` |
| 15 | Agent key extraction | Bounded | `policy::over_per_tx`, `policy::category_*`, `settlement::over_daily`, `cycle_reset`, `introspection::frozen`, `expired_capsule`, `wrong_agent`, `capsule::set_agent_rotates` | caps, `Frozen`, `Expired` |
| 16 | In-policy drain | Bounded | `settlement::over_daily`, `boundaries_spent_equals_daily_cap` | `OverDaily` |

Rows 13 (simulation-diff phishing) and 17–20 (relay, floor omission, rubber-stamping, OS compromise) are app-side or out of scope, and have no on-chain test.

## Hardening from the phase 1 review

| Exploit found in review | Test |
|---|---|
| Merchant self-slashes to escape disputes or the unlock delay | `slashing::merchant_cannot_report_itself`, `floor_fraud_proof_slashes_bond` (10% bounty) |
| Key rotation erases fraud evidence | `slashing::rotation_does_not_escape_fraud_proof`, `retired_key_evidence_expires_after_unlock_delay`, `key_records_track_rotation` |
| Bond unlock never re-locks; merchant trades while unlocking | `dispute::withdraw_relocks_bond`, `unlock_request_deactivates_merchant`, `reactivating_cancels_unlock` |
| PoW fails whenever the slot advances | `pow::pow_survives_slot_advance`, `pow_slot_too_old`, `pow_slot_not_in_sysvar`, `pow_wrong_hash_for_named_slot` |
| A trivially-true Lighthouse assertion satisfies the flag | `lighthouse::trivially_true_lighthouse_assertion_is_rejected` |
| Cross-merchant receipt pre-blocking | `settlement::receipts_are_namespaced_by_merchant` |
| `agent == owner` silently disables co-sign | `capsule::agent_cannot_be_owner_at_creation`, `agent_cannot_be_rotated_to_owner` |
| Compensation pool locked forever | `slashing::arbiter_compensates_users_from_pool`, `only_arbiter_spends_pool` |

## Also covered

- **Disputes and bonds:** `dispute::*`. Arbiter-only resolution, slash capped at bond, no double resolution, unlock delay, unlock blocked by open disputes.
- **SGT gate:** `sgt::*`. Real Token-2022 group and member mints; wrong group, wrong owner, zero amount, classic-SPL impostor, one promo claim per device.
- **Merkle edge shapes:** `settlement::single_item_menu_pays`, `odd_menu_last_leaf_pays`, and the `tally-core` merkle tests for sizes 1–17.
- **Boundaries:** price == floor (autonomous), price == per-tx cap, spent == daily cap, slot == expires_slot, window == 150, cycle rollover at exactly `CYCLE_SLOTS`.

## Compute (measured)

| Configuration | CU |
|---|---|
| `pay_offer`, 8-item menu | ~20.4k |
| `pay_offer`, 4,096-item menu (proof depth 12) | ~22.8k |
| `pay_offer` + required Lighthouse + PoW gate | ~22.3k |
| DrillX verification spike (rejected) | ~468k |

`compute::*` fails the build if `pay_offer` regresses past 45,000 CU.
