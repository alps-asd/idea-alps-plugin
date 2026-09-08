import { build } from 'esbuild';
import { cpSync, mkdirSync } from 'node:fs';

const outDir = new URL('../src/main/resources/web/', import.meta.url);
mkdirSync(outDir, { recursive: true });

await build({
  entryPoints: ['src/main.ts'],
  bundle: true,
  format: 'iife',
  platform: 'browser',
  target: 'es2020',
  minify: true,
  sourcemap: false,
  outfile: new URL('asd.bundle.js', outDir).pathname,
  logLevel: 'info',
});

cpSync('src/index.html', new URL('index.html', outDir).pathname);
