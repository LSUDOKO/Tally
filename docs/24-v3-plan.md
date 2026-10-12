# Tally v3: what makes it a product

v1 proved the mechanism (signed menus, on-chain checks). v2 made it something people would keep on their phone (points, usual, cart, "was this you?", a cloud, Telegram, MCP). v3 is about the things a real wallet user expects from a serious app, and about letting other systems plug in.

Each item says what it needs. "No key" items ship without anything from the maintainer; the others need an account or key that only the maintainer can create.

## Shipped in this pass (no key)

| Feature | What it does | Where |
| --- | --- | --- |
| **App lock** | The phone's fingerprint, face or PIN on every return to the front and before every payment the owner taps (one prompt per cart). Framework `BiometricPrompt`, nothing to configure. | Settings → Security |
| **Fee balance in dollars** | USD per SOL from Jupiter's public price feed (CoinGecko fallback), shown beside the agent's SOL. | Settings → Agent |
| **Export receipts** | All receipts as CSV (date, item, counter, USDC, outcome, flags, explorer link) through the share sheet. | Receipts → Export CSV |
| **Brand kit** | Mark, wordmark, icon, palette, type and light rules, generated from the theme. | `docs/brand/` |
| **Wallet sign-in** | Lists the Mobile Wallet Adapter wallets on the phone (Solflare, Phantom, Backpack, Seed Vault) with their own icons and signs in with Solana; Play Store links when there are none. The session is kept, so later approvals don't ask to sign in again. | Home → Sign in with wallet |
| **Sign out / switch wallet** | Revokes Tally's token in the wallet and sets that account's receipts and rewards aside on the phone until it signs in again. The capsule stays on chain. | Settings → account card |
| **Solana Pay for merchants** | A standard [Solana Pay](https://docs.solanapay.com) transfer-request QR beside the signed menu, so any Solana wallet can pay a Tally counter. The signed-menu QR stays the protected path. | Merchant mode |
| **Spending by category** | The weekly card splits spend into coffee / food / transit from the receipts on the phone. | Rewards |
| **Scheduled allowance** | One daily cap on weekdays, another at weekends. Home asks for one wallet approval when the day type changes; the program enforces whatever is signed. | Settings → Allowance schedule |
| **Counter directory** | Every registered counter read from the program's accounts, with bond size and slash history, before you ever scan. | Scan |
| **Telegram payment alerts (Helius)** | `/wallet` gives the chat its own Helius webhook on the capsule's vault: every purchase, top-up and withdrawal arrives as a message naming the counter, with a pointer to freeze in the app, even with Tally closed. The cloud also reads chain through Helius RPC. | Telegram bot, Tally cloud (`HELIUS_API_KEY`) |
| **Counter places (Google Places)** | The counter's Maps name, address, rating and photo at checkout and in the directory. The key stays on the cloud; a name match is labelled as one, and `PLACE_PINS` marks a confirmed place. | Checkout, Scan (`GOOGLE_PLACES_KEY`) |
| **Telegram payment alerts (Helius)** | `/wallet` gives the chat its own Helius webhook on the capsule's vault: every purchase, top-up and withdrawal arrives as a message naming the counter, with a pointer to freeze in the app, even with Tally closed. The cloud also reads chain through Helius RPC. | Telegram bot, Tally cloud (`HELIUS_API_KEY`) |
| **Counter places (Google Places)** | The counter's Maps name, address, rating and photo at checkout and in the directory. The key stays on the cloud; a name match is labelled as one, and `PLACE_PINS` marks a confirmed place. | Checkout, Scan (`GOOGLE_PLACES_KEY`) |
| **Share a capsule's rules** | A read-only page (`/c/<owner>`) on Tally cloud with a capsule's rules, plus the bot's `/share`. | Tally cloud, Telegram |

## Needs something from the maintainer

| Integration | Why | What's needed | Where it goes |
| --- | --- | --- | --- |
| **Dialect** | In-wallet notifications for Solflare/Phantom users instead of Telegram. | Dialect dapp registration | Cloud |
| **Mainnet** | USDC on mainnet, a real SKR bond. | A funded deployer key, an audit | `programs/` deploy |
| **Play Store / Solana dApp Store** | Distribution. | Developer accounts | `docs/13-hackathon-requirements.md` |

## Explicitly not planned

- Letting any third party (bot, MCP client, cloud) sign a payment. Every path still ends in the app with the owner's key, and the program re-checks the signed menu.
- Buying or transferring Tally Points.
- Storing keys anywhere but the phone (and Render's env for the demo café's menu key).
