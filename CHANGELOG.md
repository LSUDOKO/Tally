# Changelog

All notable changes to Tally. Dates are UTC. Tally runs on Solana devnet only; nothing here is audited or meant for mainnet funds.

## Unreleased

### Added
- **Tally Points, streaks and levels**: points only from settled on-chain receipts (no self-dealing, disputes or red-team runs), a streak multiplier up to 1.5×, 2× for Seeker capsules; levels give the mascot auras, a crown and a halo, with a level-up celebration.
- **My usual**: one-tap reorder that stops on any change (price, item, counter) and explains it.
- **Multi-item cart**: reviewed as a whole against per-purchase, daily and vault limits; each item settles as its own checked transaction.
- **"Was this you?" notifications** after every agent payment, with a Freeze action; a Freeze button on the home-screen widget.
- **Weekly summary**, **shareable verifiable receipts**, a **low-balance warning** and a **read-only offline view**.
- **Tally MCP server** (`mcp/`): verified menus, rules, offers, purchase proposals (QR and `tally://` link) and payment lookups for any MCP client; it cannot pay or change anything. The app accepts proposals by QR or link.
- **Brand redesign**: cobalt glass UI, Playfair Display and Plus Jakarta Sans, the animated Tally mascot, launcher icon, splash and widget.


### Added
- **Groq-hosted agent** (`openai/gpt-oss-120b`) in place of the Anthropic SDK. The key is entered in Settings and kept in app-private storage, never built into the APK. Calls are capped at 30 a day and 1,024 tokens each; past the allowance the deterministic rule agent decides.
- **Real-wallet co-signing for any Mobile Wallet Adapter wallet.** Tally checks the wallet's capabilities and uses sign-only or sign-and-send, whichever it offers. Verified with Solflare on a Moto G34 ([docs/21](docs/21-real-phone-run.md)).
- **Close capsule** in Settings: the vault's USDC and the account rent go back to the owner.
- **One-phone demo** (debug builds): the phone signs a fresh menu as the merchant and pays it as the customer, re-signing automatically when a menu expires.
- **`faucet.ts --sgt`** issues a mock Seeker Genesis Token on devnet, so Seeker-only features can be tried on any phone.
- **Software NFC tap test**: a real signed menu crosses the exact APDU exchange and still verifies.
- **Recovery after a reinstall or new phone**: "Make this phone the agent" (set_agent) and, in Merchant mode, "Use this phone's menu key" (rotate_signing_key).
- **Seeker promo state**: the home screen reads the claim record and shows "claimed"; a second claim is explained.
- **Signed-menu QR in the SDK** (Base45 + CBOR, byte-compatible with the app) and **`scripts/counter.ts`**, a laptop counter showing the demo café's live QR.
- **Judge path**: a five-minute README walkthrough and an issue form for requesting devnet test funds.

### Fixed
- Owner-signed actions failed on every phone: the blockhash was fetched on Android's main thread.
- Screens refreshed before transactions confirmed, showing stale balances.
- Leaving a screen could cancel a payment mid-flight, so it was sent but never logged.
- A menu that expired during Second Look reverted the co-signed payment; it is now rebuilt from a fresh menu, or the user is asked to rescan.
- An NFC tap spanning the terminal's 20-second re-sign could splice two menus; each tap now reads from a snapshot taken at SELECT.
- The NFC reader looped forever on a terminal that answered with empty chunks, and trusted any announced length (now capped at 16 KB).
- A duplicate payment showed as "code 0"; it now explains Tally's double-charge protection.
- Today's spend came from the phone's local log; it now comes from the chain, reset with the program's 216,000-slot cycle.
- Red-team attacks were sometimes stopped by the wrong check (category, budget, proof of work, Lighthouse); each now meets the check it targets.
- Funding the capsule counted as spending; rotating the agent key stranded its SOL.
- The signed release APK crashed at launch and could not build ML Kit's scanner: R8 full mode dropped reflectively-called constructors (WorkManager's Room database, ML Kit registrars). Verified fixed by scanning and paying with the release build on a phone.

## Phase 4 · 2026-10-08 · Devnet
- Program deployed to devnet; cluster initialised (USDC and SKR test mints, mock Seeker Genesis Token collection); demo world seeded.
- `attack-demo.ts` replays the honest purchase and the attacks with explorer links ([docs/DEVNET.md](docs/DEVNET.md)).
- Opt-in live test (`TALLY_LIVE=1`) pays, and is attacked, on devnet through the Kotlin client.
- RPC calls retry with backoff on rate limits.

## Phase 3 · Android app
- One APK for both roles: Seeker user mode and merchant terminal.
- Scan (CameraX + ML Kit) or tap (NFC HCE) a signed menu; context-firewalled agent; Second Look sheet showing gross outflow; red-team mode; receipts with disputes; home-screen widget; voice requests.
- Kotlin core library, byte-equal to the Rust crate and the `@solana/kit` client.

## Phase 2 · SDK, scripts and CI
- TypeScript SDK generated with Codama on `@solana/kit`; cross-language fixtures.
- Devnet/localnet scripts: deploy, initialise, seed, faucet, attack replay.
- GitHub Actions for the program, SDK and Android builds.

## Phase 1 · Program
- Anchor program enforcing ten `pay_offer` invariants: signed-menu membership, hardened Ed25519 introspection, freshness window, Cheapest-Match rule, bond tier, receipt nullifier, exact-delta transfer, payee from the registry, caps and categories, and an optional exact Lighthouse assertion.
- SKR merchant bonds with fraud-proof slashing, arbiter disputes, Seeker Genesis Token check, proof-of-work anti-flood gate.
- LiteSVM threat-model suite asserting each attack's exact revert code.
