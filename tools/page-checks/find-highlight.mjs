// Injected script assets/js/find_highlight.js (top bar → find in page, ui/FindBar): while the app's find bar is open,
// Chromium's find matches are coloured with the app's accent through the CSS ::search-text pseudo-element (WebView 144+).
// The style sheet must work on a page whose Content Security Policy forbids inline styles (a <style> tag would be
// blocked there), must not pile up when added again, must leave the page's own style sheets alone, and must go away
// when the bar closes.
// Run: node find-highlight.mjs   (or all checks: npm run check). Exit code 0 = pass.
import { launch, assetPath } from './common.mjs';
import { readFileSync } from 'fs';
const SCRIPT = readFileSync(assetPath('js/find_highlight.js'), 'utf8');
const CSS = '::search-text{background-color:#AABBCC;color:#112233}::search-text:current{background-color:#445566;color:#FFFFFF}';
const b = await launch();
const p = await b.newPage();
const consoleLines = [];
p.on('console', m => consoleLines.push(m.text()));
p.on('pageerror', e => consoleLines.push('pageerror: ' + e.message));
// A strict CSP like a hardened chat site: no inline styles, no scripts of its own.
await p.route('https://chat.example/', r => r.fulfill({ status: 200, contentType: 'text/html',
    headers: { 'content-security-policy': "default-src 'none'; style-src 'none'; script-src 'none'" },
    body: '<p>find me, find me again</p>' }));
await p.goto('https://chat.example/');
// The page's own constructed style sheet must survive.
await p.evaluate(() => { const own = new CSSStyleSheet(); own.replaceSync('p{margin:1px}'); document.adoptedStyleSheets = [own]; });
const run = arg => p.evaluate(SCRIPT + '(' + JSON.stringify(arg) + ')');
const state = () => p.evaluate(() => document.adoptedStyleSheets.map(s => Array.from(s.cssRules).map(r => r.cssText).join(' ')));
const supported = await p.evaluate(() => CSS.supports('selector(::search-text)'));
const first = await run(CSS);
const second = await run(CSS);                 // the bar searches again: still one sheet
const applied = await state();
const removed = await run(null);
const after = await state();
const removedAgain = await run(null);          // closing twice is harmless
await b.close();
const ours = applied.filter(t => t.includes('::search-text'));
console.log({ supported, first, second, applied, removed, after, removedAgain, consoleLines });
const ok = supported && first === 'applied' && second === 'applied' && applied.length === 2 && ours.length === 1
    && ours[0].includes('#445566') === false /* cssText normalises colours to rgb() */ && ours[0].includes('rgb(68, 85, 102)')
    && removed === 'removed' && after.length === 1 && after[0].includes('margin') && removedAgain === 'removed'
    && consoleLines.length === 0;
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
