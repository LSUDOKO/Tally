# 19 — Sources

## Research papers

| Paper | Link | Read |
|---|---|---|
| Signing the Transaction but Not the Decision: Whisper Attacks and a Binding Defense for AP2 | https://www.alphaxiv.org/abs/2609.11757 | Full text |
| APort Vault: Benchmarking AI Agent Payment Authorization with the Open Agent Passport | https://www.alphaxiv.org/abs/2609.22076 | Full text |
| Proof-Gated Signing: Solver-Checked Transaction Guards that Hold Under State Drift | https://www.alphaxiv.org/abs/2610.00354 | Full text |
| Blockchain Transaction Simulation Phishing | https://www.alphaxiv.org/abs/2607.28747 | Full text |
| Toward Practical Decentralized Proof-of-Location via Physical Witnessing Zones | https://www.alphaxiv.org/abs/2608.04957 | Full text |
| CAVEAT: Towards Robust Computer-Use Agents in Incentive-Misaligned Environments | https://www.alphaxiv.org/abs/2609.27273 | Abstract |
| Whom Do AI Agents Work For? Role Assignment Induces Sponsorship Bias | https://www.alphaxiv.org/abs/2609.17989 | Abstract |
| Who Is Your Agent Serving? Provider-Side Indirect Prompt Injection | https://www.alphaxiv.org/abs/2610.05266 | Abstract |
| Market Signal Injection: Adversarial Context Manipulation of LLM Pricing Agents | https://www.alphaxiv.org/abs/2609.18357 | Abstract |
| GNSS Spoofing in Mobile Devices: A Survey | https://www.alphaxiv.org/abs/2610.01294 | Abstract |
| Can CaMeLs Talk? Securing Multi-Agent Systems Against Indirect Prompt Injection | https://www.alphaxiv.org/abs/2610.05640 | Abstract |

## Projects and documentation

| Name | Link |
|---|---|
| Cloaked Agent | https://www.remoteopenclaw.com/mcp/CloakedAgent/cloaked |
| Praxis | https://hackquest.io/projects/Praxis |
| AgentGuard Protocol | https://github.com/davidweb3-ctrl/agentguard-protocol |
| Squads spending limits API | https://developers.squads.so/grid/v1/api-reference/endpoint/spending-limits/post.md |
| wormhole-x402 | https://socket.dev/npm/package/wormhole-x402 |
| AP2 explained | https://eco.com/support/en/articles/15192002-ap2-protocol-explained-google-s-agentic-commerce-standard-2026 |
| Mastercard Verifiable Intent | https://www.mastercard.com/global/en/news-and-trends/stories/2026/verifiable-intent.html |
| Lighthouse assertions guide (QuickNode) | https://www.quicknode.com/guides/solana-development/tooling/web3-2/lighthouse |
| Ed25519 verification in Anchor (RareSkills) | https://rareskills.io/post/solana-signature-verification |
| Ed25519/Secp256k1 introspection example | https://github.com/GuidoDipietro/solana-ed25519-secp256k1-sig-verification |
| Coinspect: transaction simulation challenges | https://www.coinspect.com/blog/transaction-simulation-challenges/ |
| Solana Mobile Monolith winners | https://solanamobile.com/blog/solana-mobile-monolith-hackathon-winners-announced |
| CLOCK IN hackathon (Radiants) | https://solanamobile.radiant.nexus/ |

| Lighthouse (Jac0xb) | https://github.com/Jac0xb/lighthouse |
| DrillX (ORE PoW) | https://github.com/regolith-labs/drillx |
| ORE | https://github.com/regolith-labs/ore |
| Mobile Wallet Adapter (Kotlin clientlib) | https://github.com/solana-mobile/mobile-wallet-adapter |
| Solana Mobile docs (Seed Vault, SGT, SIWS) | https://docs.solanamobile.com/ |
| Android Host Card Emulation | https://developer.android.com/develop/connectivity/nfc/hce |
| Android Keystore (Ed25519 / KeyMint) | https://developer.android.com/privacy-and-security/keystore |
| Helius priority fee API | https://docs.helius.dev/ |

## Verification notes (October 2026)

- APort Vault: 140 → 0, 105 → 0 matched, 0.38% session bound, 7/14 forged-receipt — **verified in the paper**. "74.6%" — **not found**; removed.
- Proof-Gated Signing: simulation-only missed 50/50 drift scenarios; 57.9% overall; PGS 0/50 drift with attacker gain — **verified**.

## Still to read before submission

- Solana Mobile developer docs and Mobile Wallet Adapter docs (current Kotlin API names).
- Solana dApp Store submission guide.
- Current SKR token mint address and decimals.
- SGT mint authority / group address and Token-2022 extension layout.
- Whether Solana's BLAKE3 syscall is active on the target cluster.
- Whether current ORE still uses DrillX PoW; DrillX on-chain verification CU cost.
- Lighthouse program ID and assertion instruction layout for the pinned version.
- Android Keystore Ed25519 support on Seeker (KeyMint version).
- Official CLOCK IN ORE prize eligibility text.
- Anchor version notes for the version you pin.

*Research gathered October 2026 with alphaXiv and web search. Firecrawl credits ran out midway, so the competitor scan is good but not exhaustive.*
