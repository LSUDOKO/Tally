// Demo helper: build an agent proposal for an item on a counter's live menu, the same thing the
// Tally MCP server's tally_propose_purchase returns. Prints the tally:// link (open it on the phone,
// e.g. `adb shell am start -a android.intent.action.VIEW -d "<link>"`) and the QR text.
// Usage: pnpm -C scripts exec tsx propose.ts "<item words>" [counter_url=http://localhost:8787]
import { fromQr, toProposalLink, toProposalQr } from '@tally/sdk';

async function main() {
  const [want, counter = 'http://localhost:8787'] = process.argv.slice(2);
  if (!want) throw new Error('usage: propose.ts "<item words>" [counter_url]');
  const body = (await (await fetch(new URL('/menu.json', counter))).json()) as { qr: string };
  const payload = fromQr(body.qr);
  const words = want.toLowerCase().split(/\s+/);
  const i = payload.names.findIndex((n) => words.every((w) => n.toLowerCase().includes(w)));
  if (i < 0) throw new Error(`no item matching "${want}" (menu: ${payload.names.join(', ')})`);
  console.log(`item: ${payload.names[i]}`);
  console.log(`link: ${toProposalLink(payload, payload.menu.items[i].id)}`);
  console.log(`qr:   ${toProposalQr(payload, payload.menu.items[i].id).slice(0, 60)}…`);
}

main().catch((e) => {
  console.error(String(e));
  process.exit(1);
});
