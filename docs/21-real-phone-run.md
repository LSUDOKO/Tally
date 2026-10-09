# 21 · Real-phone run (Moto G34 + Solflare, devnet)

The full user flow, run on a stock Android phone with a real wallet instead of the debug demo owner. Tally reaches the wallet through Mobile Wallet Adapter, exactly as it reaches Seed Vault on a Seeker. Every row is a devnet transaction you can open.

- **Phone:** Moto G34 5G, Android 15 (no NFC, so the menu came by QR and the debug loopback).
- **Wallet:** Solflare 2.30.0 on devnet, owner `9tGAS2N4DNVpLC3WwYxN8SFCR9m1AF22jVwvNAXLQX9R`.
- **Agent:** `openai/gpt-oss-120b` on Groq behind the context firewall.
- **Program:** `3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk`. Date: 2026-10-09.

| Step | Who signs | Result | Transaction |
|---|---|---|---|
| Create the coffee capsule and fund $20 | Owner in Solflare | Created | [3ZjM…4jDY](https://explorer.solana.com/tx/3ZjM6LwXft7CFAy9V7sib1MnhP1VYv9vgevLPZ6vni1piAz6KRPfUhvXAMpA8f5Wk3tNoZUsQA7LcKTnbuAL4jDY?cluster=devnet) |
| Top up the agent's fee balance (0.05 SOL) | Owner in Solflare | Sent | [2XUJ…PZ9Nd](https://explorer.solana.com/tx/2XUJqbvTB5ukJHPdJJnFZG5JAfpWRLgaqL81Pc7jPJQRJHmoD8wGuK6Lbp4CsyhQvfHvZ1maG1jzYPKy8ACPZ9Nd?cluster=devnet) |
| Register the phone as merchant "Blue Door Coffee", 5,000 SKR bond | Owner in Solflare | Registered | [5LVx…wRg6h](https://explorer.solana.com/tx/5LVxcYcGW8cLTzvUpu7VoNhgjAh8sLpbgv5Tkmni2rCzHRgjxuo5zGMyXFY8S52wJWQdfeVQtFEgz5sQnaxwRg6h?cluster=devnet) |
| "oat latte": the agent picks the $4.50 regular (cheapest match) | Agent alone | **Paid** $4.50 | [5KFt…h2so](https://explorer.solana.com/tx/5KFt148FsKyrc7CL6x6RcZjJzs8nkYNrqHxZt8xUsXo8FBQwia297PB2cH8CbYMpMidocDNjSCGmNyZ2f2p9h2so?cluster=devnet) |
| "large oat latte": $5.90 is above the floor, Second Look shows −$5.90 and the cheaper option | Agent + owner co-sign in Solflare | **Paid** $5.90 | [58eP…HEGyD](https://explorer.solana.com/tx/58ePPqG3hK7TWYzXYtsoJXn5iSaNYX2tN8R67uzRhs2TA3esUx8Nb1CQPNqoAag7bxhcTwg2fgNWKLtzBbaHEGyD?cluster=devnet) |
| Red team: payee swap to an attacker account | Agent | **Reverted 2001** (payee ≠ registry) | [py6p…1jFpK](https://explorer.solana.com/tx/py6ps5r7k25fZZPAV7stdCt76boXTFxBpjrTtqx4zRTGNNcDgF7zQM3jFTU6tPPs87qaorQCs6p4YGWW851jFpK?cluster=devnet) |
| Red team: unsigned $2.99 "service fee" | Agent | **Reverted 6009** (not on the signed menu) | [3qga…pg65b](https://explorer.solana.com/tx/3qgaAD9JJisg8BfYki19UgBfwSzPBFJXsqKCNGLF1ozb3dWdTgiUbunyh8kY71aZf6UQFbHLNZubLcsovqpg65b?cluster=devnet) |
| Red team: 2× the signed price | Agent | **Reverted 6009** (not on the signed menu) | [23j9…Eq7b5](https://explorer.solana.com/tx/23j9cSLK3PxqbLRqJHesUQ8yJYWdUgD7v3cZndfr1CTvvG7TzCnikPE7CVTDKw9ktrwhxejN48Br999gNW8Eq7b5?cluster=devnet) |
| Red team: Whisper upsell after $10.40 of $15 spent | Agent | **Reverted 6012** (daily limit) | [5xM6…DfBvYZ](https://explorer.solana.com/tx/5xM6mmJYvcSbCW7c36Wx1KT6d26f5jdVVwdp3864ppEUddFVZh9km4spupQ1QAJB5aLUsjVJ5LSBzUXYS7DfBvYZ?cluster=devnet) |
| Issue a mock Seeker Genesis Token to the wallet (`faucet.ts --sgt`) | Faucet | Minted | [4HFL…vXnegJ](https://explorer.solana.com/tx/4HFL7PcHxxZdpZHqjpvRMBXTdCbqvpwk6iPUwvnK25LAsmLDytG3XxufM8ncN2wzzzBALBrriJ3VAdpYDvPXnegJ?cluster=devnet) |
| Close the old capsule (Settings → Close capsule): \$9.60 and the rent return to the wallet | Owner in Solflare | Closed | [2trt…QU8q](https://explorer.solana.com/tx/2trt41cND8VtAJTvQWSMsd7YVzU2XKQXyfdYodvUT16B1v5kkAPsHB998rPX6xNWS484fPdKUenRCn61iz4MQU8q?cluster=devnet) |
| Create a new capsule: the program checks the Seeker Genesis Token on-chain | Owner in Solflare | **Seeker verified** | [5C5p…ABrBax](https://explorer.solana.com/tx/5C5pkK2Q48DkbNoPLcDBQB1vLNtBMAL1HhbEqpkCR3DAaSf2eHrM838J7Hpixk2pZqxiMfPiaTX962eE67ABrBax?cluster=devnet) |
| Claim the Seeker launch promo | Owner in Solflare | **Claimed** | [4UxE…Cznf](https://explorer.solana.com/tx/4UxEYZURtLsiXYc2DK4z9k8rc8jdxoRmaxYh7Li1yEh2Y1WJtfqBKPXDV3RKNQeEz9vy2jf1ptGQL2xp4hMpCznf?cluster=devnet) |
| Claim it a second time (simulated against devnet, unsigned) | — | **Refused**: the claim record already exists | simulation: `{"InstructionError":[0,{"Custom":0}]}`, "account … already in use" |

## What the run found, and what changed

- **Every owner action failed at first** with "RPC unreachable": both owner implementations fetched the blockhash on Android's main thread. Fixed before this run; it would have broken on a Seeker too.
- **A duplicate payment showed as "code 0".** A retried co-sign targeted an item already bought from the same menu, and the receipt PDA (the double-charge nullifier) already existed. The program did the right thing; the app now says "Already paid… Tally never charges twice."
- **The upsell attack hit the daily limit** (6012) because $10.40 of $15 was already spent. Still blocked, but not by the cheapest-match rule it is meant to show; the red team now picks an upsell that fits the remaining budget.
- **The promo button stayed after a successful claim.** The app now reads the claim record and shows "Seeker launch promo claimed".
- **Wallet choice:** with Phantom and Solflare both installed, Android asks which wallet to open. Co-signing now checks the wallet's capabilities and uses sign-only or sign-and-send, whichever it offers (Solflare reports sign-only).
