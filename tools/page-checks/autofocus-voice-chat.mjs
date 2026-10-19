// Injected script assets/js/auto_focus.js, R4-23: if voice chat starts while the script waits for the page, the run
// stops and releases its "in progress" guard, so the next app open still starts a new chat.
// Run: node autofocus-voice-chat.mjs   (or all checks: npm run check). Exit code 0 = pass.
import { launch, assetPath } from './common.mjs';
import { readFileSync } from 'fs';
const SCRIPT = readFileSync(assetPath('js/auto_focus.js'), 'utf8');
const b = await launch();
const p = await b.newPage();
await p.setContent('<div id="app"></div><div id="app-loader"></div>');   // page not ready yet: the script waits
await p.evaluate(() => { window.__msgs = []; window.AppBridge = { postMessage: m => window.__msgs.push(m) }; });
await p.evaluate(SCRIPT);
await p.evaluate(() => { window.isVoiceChatActive = true; });          // voice chat starts while it waits
await p.waitForTimeout(900);
const stopped = await p.evaluate(() => window.__llmcAutoFocusRunning === false && window.__msgs.length === 0);
await p.evaluate(() => { window.isVoiceChatActive = false; document.getElementById('app').innerHTML = '<textarea></textarea>'; });
await p.evaluate(SCRIPT);                                               // next app open: must run
await p.waitForTimeout(1600);
const ran = await p.evaluate(() => window.__msgs.filter(m => m === 'shortcut:').length === 2);
await b.close();
console.log({ stopped, ran }, stopped && ran ? 'PASS' : 'FAIL'); process.exit(stopped && ran ? 0 : 1);
