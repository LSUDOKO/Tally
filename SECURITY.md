# Security policy

## Status

Tally runs on **Solana devnet only** and has **not been audited**. Do not use it with mainnet funds. The current threat model, what is blocked on-chain and what is out of scope, is in [docs/11-threat-model.md](docs/11-threat-model.md) and the README's [Security, honestly](README.md#security-honestly).

## Supported versions

| Version | Supported |
|---|---|
| 1.2.x (latest release, `main`) | Yes |
| < 1.2 | No, please update |

The deployed devnet program is `3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk`.

## Reporting a vulnerability

**Please do not open a public issue.** Report privately through GitHub: [open a private vulnerability report](https://github.com/LSUDOKO/Tally/security/advisories/new).

Include what you can of:

- the component (program, SDK, Android app, Tally cloud / MCP server, Telegram bot);
- the steps or a test that reproduces it, ideally a LiteSVM test or a devnet transaction signature;
- the impact you expect (funds moved outside a capsule's rules, a check bypassed, a denial of service).

You can expect an acknowledgement within 3 days and an assessment within 7. Fixes land with a regression test in [`tests/litesvm`](tests/litesvm) and a credit in the changelog, unless you prefer to stay anonymous.

## Most valuable reports

Anything that lets an agent move value that `pay_offer` should refuse:

- paying an item that is not under the merchant's signed Merkle root, or at a different price;
- paying any account other than the merchant's registered payout;
- getting past the Ed25519 introspection, the freshness window, the Cheapest-Match rule, the caps or the receipt nullifier;
- draining a vault beyond the exact-delta post-condition;
- bypassing SKR bond slashing, disputes or the bond unlock delay.

## Out of scope

- Devnet tokens have no value; "stealing" mock USDC or SKR is only interesting if it shows a real bypass.
- Rate limits and cold starts of the free-tier hosted demo.
- A compromised phone OS or wallet, physical relay of a fresh QR code, and the other limits listed in the threat model.

Never send a recovery phrase or private key, including in a report.
