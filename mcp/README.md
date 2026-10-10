# Tally MCP server

Lets any MCP client (Claude Code, Claude Desktop, other agents) shop **within the owner's Tally rules**:

| Tool | What it does |
|---|---|
| `tally_read_menu` | Reads a counter's live menu and verifies its signature against the merchant's registered key on Solana |
| `tally_get_rules` | Reads the owner's capsule: allowed categories, per-purchase and daily limits, what's left today, frozen or expired |
| `tally_find_offer` | Judges every item (`autonomous`, `needs_owner_approval`, `refused`, with reasons) and returns the cheapest match for a request |
| `tally_propose_purchase` | Returns a QR code (scan with Tally) and a `tally://checkout` link that opens the purchase in the app |
| `tally_check_payment` | Settled, pending or refused (with the plain-language reason) for a transaction signature |

**What it cannot do, by design:** pay, withdraw, change rules, or see any key. A proposal only pre-fills the app's checkout; the owner taps to pay, pricier picks still go through Second Look, and the chain re-checks the item, price, payee and limits.

## Run

```bash
pnpm install
# a counter to shop at (or a phone in Merchant mode publishing via scripts/counter.ts)
TALLY_CLUSTER=devnet pnpm -C scripts exec tsx counter.ts 8787
```

Claude Code:

```bash
claude mcp add tally -- npx tsx /path/to/Tally/mcp/src/index.ts
```

Claude Desktop (`claude_desktop_config.json`):

```json
{ "mcpServers": { "tally": { "command": "npx", "args": ["tsx", "/path/to/Tally/mcp/src/index.ts"] } } }
```

Then ask: *"At http://localhost:8787, get me an oat latte within my Tally rules. My wallet is 9tGA…QX9R."* The agent reads the verified menu, checks your rules, and shows a QR to scan with Tally.

Environment: `TALLY_RPC_URL` (default Solana devnet).

## Test

```bash
pnpm -C mcp test                                   # policy unit tests
npx tsx mcp/test/smoke.ts <owner> http://localhost:8787   # every tool, live over stdio
```
