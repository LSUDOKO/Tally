#!/usr/bin/env -S npx tsx
// Tally MCP server over stdio (Claude Code, Claude Desktop). Tools live in tools.ts.
// Usage: npx tsx mcp/src/index.ts   ·   env TALLY_RPC_URL (default: Solana devnet)
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { createTallyServer } from './tools.js';

await createTallyServer().connect(new StdioServerTransport());
