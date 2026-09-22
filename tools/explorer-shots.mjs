// Renders every screen of ui/typewright-explorer.html to PNG, so a build agent without a
// touch device can compare its Compose output against the source of truth.
// Usage: node tools/explorer-shots.mjs [outDir]   (defaults to build/explorer-shots)
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

// Resolve playwright from the project or the global install (NODE_PATH is ignored by ESM).
const require = createRequire(import.meta.url);
const { chromium } = require(require.resolve('playwright', { paths: [process.cwd(), ...(process.env.NODE_PATH ?? '').split(':').filter(Boolean)] }));

const out = resolve(process.argv[2] ?? 'build/explorer-shots');
mkdirSync(out, { recursive: true });
const url = 'file://' + resolve('ui/typewright-explorer.html');
const screens = ['home', 'capture', 'trace', 'economy', 'draw', 'space', 'learn', 'check', 'ship', 'workbook', 'desktop'];
const textures = ['paper', 'vellum', 'blueprint', 'dark', 'light'];

const browser = await chromium.launch({ executablePath: process.env.CHROMIUM ?? undefined });
for (const [label, viewport] of [['wide', { width: 1440, height: 1000 }]]) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: 1 });
  await page.goto(url);
  // The explorer's `.screen{display:flex}` outranks the UA `[hidden]` rule; the chat artifact host
  // supplies this override, a plain browser does not. Inject it rather than edit the explorer.
  await page.addStyleTag({ content: '[hidden]{display:none!important}' });
  await page.waitForTimeout(800);
  for (const s of screens) {
    await page.click(`#nav [data-s="${s}"]`);
    await page.waitForTimeout(900);
    await page.screenshot({ path: `${out}/${s}-${label}.png` });
    // Notes for the screen, as text, beside the image.
    const notes = await page.evaluate(() => document.querySelector('#notes')?.innerText ?? '');
    const fs = await import('node:fs');
    fs.writeFileSync(`${out}/${s}-notes.txt`, notes);
  }
  await page.click('#nav [data-s="home"]');
  for (const t of textures) {
    await page.click(`.texrow [data-t="${t}"]`);
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${out}/texture-${t}.png` });
  }
  await page.close();
}
await browser.close();
console.log('wrote', out);
