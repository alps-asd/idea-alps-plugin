import { readFileSync } from 'node:fs';

// asd.bundle.js is a browser IIFE; it only needs `globalThis` (used directly,
// no DOM APIs) to expose window.asd. Evaluate it in this Node process.
const code = readFileSync(new URL('../src/main/resources/web/asd.bundle.js', import.meta.url), 'utf-8');
(0, eval)(code);

// Committed fixture, not the sibling app-state-diagram checkout: this must
// run standalone in CI, where only this repo (and a freshly built sibling
// dist/) is present.
const sample = readFileSync(new URL('./fixtures/alps.json', import.meta.url), 'utf-8');

const result = await globalThis.asd.render(sample, 'id');
if (result.error) {
  console.error('RENDER ERROR:', result.error);
  process.exit(1);
}
if (!result.svg || !result.svg.includes('<svg')) {
  console.error('NO SVG IN OUTPUT:', result);
  process.exit(1);
}

const expectedIds = ['Blog', 'BlogPosting', 'About'];
const missingIds = expectedIds.filter((id) => !result.svg.includes(id));
if (missingIds.length > 0) {
  console.error('MISSING EXPECTED NODE IDS IN SVG:', missingIds);
  process.exit(1);
}

console.log('OK: svg length =', result.svg.length);
console.log('contains node ids:', expectedIds.join(', '));

// Broken JSON must surface as an error, not throw.
const broken = await globalThis.asd.render('{not json', 'id');
if (typeof broken.error !== 'string') {
  console.error('EXPECTED AN ERROR FOR BROKEN INPUT, GOT:', broken);
  process.exit(1);
}
console.log('broken input handled: true');
