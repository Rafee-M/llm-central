// Injected script assets/js/auto_focus.js ("Auto-focus keyboard", duck.ai), R4-23: the app injects it up to three
// times for one page load; only one run may press "new chat" and open the keyboard (2 presses + 2 keyboard requests),
// and a later run (the app opened again) must still work.
// Run: node autofocus-once-per-load.mjs   (or all checks: npm run check). Exit code 0 = pass.
import { launch, assetPath } from './common.mjs';
import { readFileSync } from 'fs';
const SCRIPT = readFileSync(assetPath('js/auto_focus.js'), 'utf8');
const b = await launch();
const p = await b.newPage();
await p.setContent('<div id="app"><textarea></textarea></div>');
await p.evaluate(() => { window.__msgs = []; window.AppBridge = { postMessage: m => window.__msgs.push(m) }; });
const count = type => p.evaluate(t => window.__msgs.filter(m => m === t).length, type);
for (let i = 0; i < 3; i++) await p.evaluate(SCRIPT);   // same page load: onPageFinished + progress 100 + …
await p.waitForTimeout(1600);
const firstShortcut = await count('shortcut:'), firstKeyboard = await count('keyboard:');
await p.evaluate(SCRIPT);                                    // later: the user opens the app again (MAIN intent)
await p.waitForTimeout(1600);
const secondShortcut = await count('shortcut:') - firstShortcut;
await b.close();
console.log({ firstShortcut, firstKeyboard, secondShortcut });
// One run = 2 shortcut presses (now + 400 ms) and 2 keyboard requests (600 / 1200 ms).
const ok = firstShortcut === 2 && firstKeyboard === 2 && secondShortcut === 2;
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
