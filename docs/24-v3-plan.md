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

## Next, no key needed

1. **Solana Pay for merchants.** The merchant terminal also shows a standard [Solana Pay](https://docs.solanapay.com) transfer-request QR, so any Solana wallet can pay a Tally counter even without the app. Tally's own signed-menu QR stays the protected path.
2. **Spending by category.** The weekly card gains a category split (coffee / food / transit) from the receipts already on the phone.
3. **Scheduled allowance.** "Reset my daily cap to $15 every weekday, $5 on weekends." Stored on the phone, applied with one wallet signature when it changes.
4. **Counter directory.** A list of bonded merchants read from the on-chain registry, with bond size and slash history, so a user can see who is trustworthy before they ever scan.
5. **Share a capsule's rules.** A read-only link (`/c/<capsule>`) on Tally cloud showing a capsule's rules and receipts, for a parent giving a child an agent allowance.

## Needs something from the maintainer

| Integration | Why | What's needed | Where it goes |
| --- | --- | --- | --- |
| **Helius** | Faster devnet RPC and **webhooks**, so "Was this you?" can arrive even when the app is closed (the cloud gets the event and pushes to Telegram). | A free Helius API key | Render env `HELIUS_API_KEY`; app Settings → RPC URL |
| **Telegram alerts** | The bot messages you when your agent pays (needs Helius webhooks above, or a polling worker). | Same key; the bot token is already set | `mcp/src/bot.ts` |
| **Dialect** | In-wallet notifications for Solflare/Phantom users instead of Telegram. | Dialect dapp registration | Cloud |
| **Google Places** | Show the counter's real name, address and photo next to the merchant address. | Places API key | App (Checkout, Receipts) |
| **Mainnet** | USDC on mainnet, a real SKR bond. | A funded deployer key, an audit | `programs/` deploy |
| **Play Store / Solana dApp Store** | Distribution. | Developer accounts | `docs/13-hackathon-requirements.md` |

## Explicitly not planned

- Letting any third party (bot, MCP client, cloud) sign a payment. Every path still ends in the app with the owner's key, and the program re-checks the signed menu.
- Buying or transferring Tally Points.
- Storing keys anywhere but the phone (and Render's env for the demo café's menu key).
