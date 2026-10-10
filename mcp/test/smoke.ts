// Live smoke test of the MCP server over stdio, as an MCP client would use it.
// Needs a running counter (scripts/counter.ts) and devnet. Usage: npx tsx test/smoke.ts <owner> [counter_url]
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

const [owner, counter = 'http://localhost:8787'] = process.argv.slice(2);
if (!owner) throw new Error('usage: smoke.ts <owner> [counter_url]');
const client = new Client({ name: 'tally-smoke', version: '1.0.0' });
await client.connect(new StdioClientTransport({ command: 'npx', args: ['tsx', 'src/index.ts'], env: { ...process.env } as Record<string, string> }));
const tools = await client.listTools();
console.log('tools:', tools.tools.map((t) => t.name).join(', '));
const text = (r: Awaited<ReturnType<typeof client.callTool>>) => (r.content as { type: string; text?: string }[]).filter((c) => c.type === 'text').map((c) => c.text).join('\n');
console.log('\n# rules\n' + text(await client.callTool({ name: 'tally_get_rules', arguments: { owner } })));
const offer = await client.callTool({ name: 'tally_find_offer', arguments: { counter_url: counter, request: 'large oat latte', owner } });
console.log('\n# find_offer\n' + text(offer).split('\n').slice(0, 14).join('\n'));
const best = JSON.parse(text(offer)).best_match;
const proposal = await client.callTool({ name: 'tally_propose_purchase', arguments: { counter_url: counter, item_id: best.item_id } });
const kinds = (proposal.content as { type: string }[]).map((c) => c.type);
console.log('\n# propose\n' + text(proposal).slice(0, 220) + '…\ncontent types: ' + kinds.join(', '));
console.log('\n# check_payment\n' + text(await client.callTool({ name: 'tally_check_payment', arguments: { signature: '58ePPqG3hK7TWYzXYtsoJXn5iSaNYX2tN8R67uzRhs2TA3esUx8Nb1CQPNqoAag7bxhcTwg2fgNWKLtzBbaHEGyD' } })));
await client.close();
