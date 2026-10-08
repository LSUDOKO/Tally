# 08 — Android app (Seeker)

**Stack:** Kotlin, Jetpack Compose (Material 3), Solana Mobile Stack: **Mobile Wallet Adapter 2.0** (`com.solanamobile:mobile-wallet-adapter-clientlib-ktx`, pinned at **2.0.7** in the plan; check for the latest 2.0.x), **Seed Vault** through MWA, CameraX + ML Kit barcode scanning, Android NFC **reader mode** plus a **HostApduService** (HCE), Android Keystore / KeyMint, WorkManager, Glance (widget). Min SDK 28, with hardware Ed25519 agent keys used on 33+. Target the latest stable SDK. No WebView wrappers anywhere.

## Two signing domains

| | Owner | Agent |
|---|---|---|
| Key | Master wallet key | Ephemeral Ed25519 |
| Where | **Seed Vault** secure environment; never enters the app process | **Android Keystore**: hardware-backed Ed25519 when KeyMint supports it (Android 13+), otherwise an AES-GCM-wrapped software key whose wrapping key lives in Keystore (StrongBox if present) |
| Reached via | MWA 2.0 `transact { authorize / signAndSendTransactions / signTransactions }` | In-process signer |
| Signs | `create_capsule`, `update_rules`, `fund`/`withdraw`, `freeze`, `set_agent`, cheapest-match **co-signs** | `pay_offer` only, for one capsule |
| User sees | Biometric + Seed Vault confirmation | Nothing (autonomous) |
| If compromised | (assumed secure) | Loss bounded by caps, categories, expiry and freeze |

## Screens

The whitepaper's **seven core surfaces** are marked ★. The rest support them.

| # | Screen | What the user does | Mobile-native feature |
|---|---|---|---|
| 1 | Onboarding | Connect wallet, **Sign-in-with-Solana**, SGT check, 3 cards: "proposes / signs / checks" | MWA `authorize` + SIWS → Seed Vault |
| 2 ★ | **Home dashboard** | Capsule balances, rolling 24 h use bar, **Attacks Blocked This Week** | **Glance widget** with the same three numbers |
| 3 ★ | **Provisioning** | Type "coffee, max $6, $15/day, cheapest by default", review parsed rules as chips, approve | Seed Vault **biometric**; MWA `signAndSendTransactions` |
| 4 ★ | **Scan (Camera / NFC)** | Point at the QR or tap the merchant phone; see merchant, bond and Live/Extended badge | CameraX + ML Kit; NFC reader mode; **haptic tick** on a verified offer |
| 5 ★ | **Agent reasoning** | Say or type "oat latte". Watch a live trace: request → candidate leaves → chosen leaf → which invariants it will meet | Voice input; streaming trace |
| 6 ★ | **Second Look sheet** | See gross outflow, payee and bond, item, **cheaper alternatives skipped**, and the instruction list | Bottom sheet; high-contrast amount; Seed Vault co-sign |
| 7 ★ | **Receipts & forensics** | Paid and blocked attempts, error decoded, explorer links, "Report merchant" | Notifications for blocked attempts |
| 8 | Red-team mode | One tap runs the attack suite against your own capsule | Live revert list with explorer links |
| 9 ★ | **Merchant terminal mode** | Build catalog → register + bond SKR → rotating QR + NFC broadcast; toggle PoW congestion mode | Same APK; keep-screen-on; **HCE** |
| 10 | Settings | Rotate agent key, freeze capsule, RPC (Helius / custom), network, `REQUIRE_LIGHTHOUSE` toggle | Biometric gate on sensitive actions |

## Key flows

### Onboarding: SIWS + SGT

```kotlin
// MWA 2.0: authorize with a Sign-in-with-Solana payload (check current API names in the MWA docs).
val result = walletAdapter.signIn(activityResultSender,
    SignInWithSolana.Payload(domain = "tally.app", statement = "Sign in to Tally"))
val owner = result.authResult.accounts.first().publicKey
// Verify the SIWS signature locally, then look up SGT ownership for `owner`
// (Token-2022 accounts whose mint is a member of the SGT group; see 20-sgt-and-ore.md).
```

Holding an SGT unlocks Seeker-only features (merchant promos, higher default caps) and lets the user pass the SGT account into `create_capsule` for an on-chain check.

### Create capsule (owner)

```kotlin
walletAdapter.transact(activityResultSender) { auth ->
    // create_capsule(id, rules, agentPubkey, sgtAccount?) + fund(amount)
    signAndSendTransactions(arrayOf(createCapsuleTx.serialize()))
}
```

- The parser turns natural language into `{categories, per_tx_cap, daily_cap, expiry, min_bond, flags}`. The user sees and edits these as chips **before** Seed Vault opens. Nothing gets signed that the user didn't see as structured rules.
- After success the app stores `capsule` and `capsule_id` locally and reads everything else from the chain.

### Purchase (agent path)

1. Scan or tap → verify locally ([07](07-menu-and-qr-spec.md), steps 1–7).
2. The **context firewall** builds the agent payload. The agent returns `item_id`.
3. The tx builder emits exactly: `[ComputeBudget…] · Ed25519(header, sig, key) · pay_offer · Lighthouse assert`, with fee payer = agent key and a priority fee from the Helius `getPriorityFeeEstimate`.
4. If the merchant has PoW on, a coroutine computes a DrillX solution on a background dispatcher. A progress ring shows while it runs.
5. `simulateTransaction`: compute **gross outflow** per account from pre and post token balances, and map any Tally error to a plain sentence.
6. If the price is above the floor, the bond is below the minimum, or the window is extended → open the **Second Look sheet** → MWA `signTransactions` (Seed Vault adds the owner signature) → submit.
7. Otherwise submit directly → haptic success → receipt.

### Second Look sheet

- **You pay (gross): $5.90 USDC** in large high-contrast type, taken from simulation. Every account that decreases is listed, not just the net change.
- **To:** Blue Door Coffee · bond 5,000 SKR · 0 slashed · payout `9xQe…` (from the registry)
- **Item:** Oat latte (large) · Coffee
- **Cheaper match skipped:** Oat latte (regular) · **$4.50**. Button: *[Pick cheaper]*.
- **Transaction contains:** Ed25519 verify ✓ · Tally pay_offer ✓ · Lighthouse assert ✓ · *nothing else*
- Buttons: **Approve with Seed Vault** / **Pick cheaper** / **Cancel**

Any unexpected instruction shows a red banner and **disables Approve**.

**Note.** Tally can't draw on the Seed Vault's own signing screen. This sheet **always** comes before the Seed Vault prompt, and it's where the gross outflow is shown.

### Merchant terminal mode

- A signing key is generated in Keystore. `register_merchant` with the SKR bond is signed by the merchant's wallet through MWA.
- Catalog editor → floors computed → Merkle tree → header signed **every ~20 s** → QR redrawn and the HCE service's buffer swapped atomically.
- A **PoW congestion** switch sets `pow_difficulty`. The app suggests turning it on automatically when `pay_offer` attempts per minute go above a threshold.
- Keep-screen-on, plus a battery-saver exemption prompt.

### HCE service (merchant side)

```kotlin
class TallyHceService : HostApduService() {
    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray = when {
        apdu.isSelect(TALLY_AID) -> byteArrayOf(0x01) + SW_OK
        apdu.ins == 0xCA.toByte() -> MenuBuffer.current().size.toU16() + SW_OK
        apdu.ins == 0xB0.toByte() -> MenuBuffer.current().chunk(apdu.offset(), apdu.le()) + SW_OK
        else -> SW_INS_NOT_SUPPORTED
    }
    override fun onDeactivated(reason: Int) {}
}
```

## Agent key storage

- **Preferred:** `KeyPairGenerator.getInstance("Ed25519", "AndroidKeyStore")` (API 33+) with `PURPOSE_SIGN` and `setUserAuthenticationRequired(false)`, so the agent can act unattended. Check `KeyInfo.securityLevel` and show the user whether the key is TEE- or StrongBox-backed. *(Check the exact algorithm name and KeyMint support on Seeker.)*
- **Fallback:** generate the Ed25519 key in-app, encrypt it with an AES-GCM Keystore key, and decrypt it only for the moment of signing.
- It's never exported. Rotate with `set_agent`.
- Stated limit: on a rooted device the key can be **used**, and in fallback mode **extracted**. That's why the capsule bounds exist.

## Mobile polish checklist (UX is 25% of the score)

- [ ] Scan or tap to verified offer in **< 1 s**; tap to paid in **< 3 s** on devnet
- [ ] Haptic tick on a verified offer, success buzz on pay, double buzz on revert
- [ ] Dark mode, large type, TalkBack labels; gross outflow readable at arm's length
- [ ] Every Tally error mapped to one plain sentence ("That item isn't on the menu the café signed.")
- [ ] Offline: scanning and verification work; purchases queue until online *only if the window is still valid*
- [ ] Explorer link on every receipt and every blocked attempt
- [ ] Glance widget: balance, 24 h use, attacks blocked this week

## Libraries (check current versions before use)

| Need | Library |
|---|---|
| Wallet / Seed Vault | `mobile-wallet-adapter-clientlib-ktx` 2.0.x |
| Solana RPC + tx | Solana Mobile `web3-solana` / `rpc-core`, or Sol4k |
| Priority fees | Helius `getPriorityFeeEstimate` |
| Ed25519 | KeyMint (API 33+) or a vetted library (e.g. BouncyCastle) for the fallback |
| QR | CameraX + ML Kit Barcode Scanning |
| NFC | Android `NfcAdapter` reader mode, `HostApduService` |
| CBOR | `kotlinx-serialization-cbor` |
| Merkle / SHA-256 | `java.security.MessageDigest` |
| DrillX | `drillx` Rust crate via JNI (uniffi or a hand-written JNI binding) |
| Widget | Jetpack Glance |
