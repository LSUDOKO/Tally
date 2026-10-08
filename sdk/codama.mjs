// Generates a @solana/kit client for the Tally program from the Anchor IDL.
import { createFromRoot } from 'codama';
import { rootNodeFromAnchor } from '@codama/nodes-from-anchor';
import { renderVisitor } from '@codama/renderers-js';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const idl = JSON.parse(readFileSync(new URL('../idl/tally.json', import.meta.url), 'utf8'));
const sdkRoot = fileURLToPath(new URL('.', import.meta.url));
await createFromRoot(rootNodeFromAnchor(idl)).accept(
  renderVisitor(sdkRoot, { generatedFolder: 'src/generated', syncPackageJson: false, formatCode: true }),
);
console.log('generated client in src/generated');
