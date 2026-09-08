/**
 * Browser-safe render bridge for the PhpStorm ALPS preview (JCEF).
 *
 * Deliberately imports only `parser/alps-parser` and `generator/dot-generator`
 * from @alps-asd/app-state-diagram, and calls @viz-js/viz's `instance()`
 * directly. `generator/svg-generator.ts` is NOT imported: it pulls in
 * Node's `child_process` at module scope and is unsafe to bundle for a
 * browser/JCEF target.
 */
import { parseAlpsAuto } from '@alps-asd/app-state-diagram/parser/alps-parser.js';
import { generateDot, type LabelMode } from '@alps-asd/app-state-diagram/generator/dot-generator.js';
import { instance, type Viz } from '@viz-js/viz';

export interface RenderResult {
  svg?: string;
  error?: string;
}

export interface AsdBridge {
  render(text: string, labelMode?: LabelMode): Promise<RenderResult>;
}

let viz: Viz | null = null;

async function render(text: string, labelMode: LabelMode = 'id'): Promise<RenderResult> {
  try {
    const doc = parseAlpsAuto(text);
    const dot = generateDot(doc, labelMode);
    if (!viz) {
      viz = await instance();
    }
    const svg = viz.renderString(dot, { format: 'svg' });
    return { svg };
  } catch (err) {
    return { error: err instanceof Error ? err.message : String(err) };
  }
}

declare global {
  // eslint-disable-next-line no-var -- ambient global assignment requires `var`
  var asd: AsdBridge;
}

globalThis.asd = { render };
