// Settings page (settings.html), 2026-10-29 (Gemini added): every provider's badge in Settings → Accounts → Manage
// accounts shows its logo (an <svg>), never the fallback letters, and the page has no script errors (a missing comma in
// LOGOS once broke the whole page). The provider list comes from the app's own Providers.java, so a new provider
// without a LOGOS entry fails here. Run: node settings-provider-logos.mjs (or npm run check). Exit code 0 = pass.
// Optional: SCREENSHOT=/path.png saves the Manage accounts screen.
import { launch, pageUrl } from './common.mjs';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import path from 'path';

const here = path.dirname(fileURLToPath(import.meta.url));
const java = readFileSync(path.resolve(here, '../../app/src/main/java/org/llmcentral/app/provider/Providers.java'), 'utf8');
// new ProviderSpec("id", "Name", "https://…", new String[]{…}, "BADGE", 0xFFRRGGBB
const providers = [...java.matchAll(/new ProviderSpec\(\s*(?:DUCK_ID|"([^"]+)"),\s*"([^"]+)",[\s\S]*?"([^"]+)",\s*0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})/g)]
  .map(m => ({ id: m[1] || 'duck', name: m[2], badge: m[3], color: '#' + m[4], canAdd: true,
               accounts: [{ id: m[1] || 'duck', name: m[2], visible: true }] }));

const b = await launch();
const p = await b.newPage({ viewport: { width: 412, height: 915 } });
const errors = []; p.on('pageerror', e => errors.push(String(e)));
await p.addInitScript(list => {
  const noop = () => {};
  const accounts = { providers: list, defaults: {}, order: list.map(x => x.id) };
  window.AndroidSettings = new Proxy({ getSettingsJson: () => JSON.stringify({ max_live_tabs: 3 }) }, { get: (t, k) => t[k] || noop });
  window.AndroidAccounts = new Proxy({ getAccountsJson: () => JSON.stringify(accounts) }, { get: (t, k) => t[k] || (() => '{}') });
  window.AndroidAppearance = new Proxy({}, { get: () => () => '{}' });
  window.AndroidStorage = new Proxy({}, { get: () => noop });
}, providers);
await p.goto(pageUrl('settings.html'));
await p.waitForTimeout(400);
await p.click('#manageRow'); // Settings → Accounts → Manage accounts
await p.waitForTimeout(400);
const badges = await p.evaluate(() => [...document.querySelectorAll('#manageView .badge, #manageView [class*="badge"]')]
  .map(s => ({ svg: !!s.querySelector('svg path[d]'), text: s.textContent.trim() })));
if (process.env.SCREENSHOT) await p.screenshot({ path: process.env.SCREENSHOT });
await b.close();
const withLogo = badges.filter(x => x.svg).length;
console.log(JSON.stringify({ providers: providers.map(x => x.id), badges: badges.length, withLogo, letters: badges.filter(x => !x.svg).map(x => x.text), errors }));
const ok = providers.length >= 6 && providers.some(x => x.id === 'gemini') && badges.length >= providers.length
  && withLogo === badges.length && errors.length === 0;
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
