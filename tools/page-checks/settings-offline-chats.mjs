// Settings page (settings.html), 2026-10-18 owner decision: "Offline chats (duck.ai)" is off when the app sends no
// value or false (and "Saved chats" is greyed out), on when it sends true.
// Run: node settings-offline-chats.mjs   (or all checks: npm run check). Exit code 0 = pass.
import { launch, pageUrl } from './common.mjs';
const b = await launch();
async function state(value) {
  const p = await b.newPage({ viewport: { width: 412, height: 915 } });
  const errors = []; p.on('pageerror', e => errors.push(String(e)));
  await p.addInitScript(v => {
    const s = { max_live_tabs: 3, block_trackers: true }; if (v !== undefined) s.offline_chats = v;
    const noop = () => {};
    window.AndroidSettings = new Proxy({ getSettingsJson: () => JSON.stringify(s) }, { get: (t, k) => t[k] || noop });
    window.AndroidAccounts = new Proxy({ getAccountsJson: () => JSON.stringify({ accounts: [], providers: [], defaults: {}, order: [] }) }, { get: (t, k) => t[k] || (() => '{}') });
    window.AndroidAppearance = new Proxy({}, { get: () => () => '{}' });
    window.AndroidStorage = new Proxy({}, { get: () => noop });
  }, value);
  await p.goto(pageUrl('settings.html'));
  await p.waitForTimeout(400);
  const r = await p.evaluate(() => ({ on: document.getElementById('offline_chats').checked,
    savedChatsDisabled: document.getElementById('savedChatsRow').disabled }));
  await p.close();
  return { ...r, errors };
}
const missing = await state(undefined), off = await state(false), on = await state(true);
await b.close();
console.log(JSON.stringify({ missing, off, on }));
const ok = !missing.on && missing.savedChatsDisabled && !off.on && on.on && !on.savedChatsDisabled
  && [missing, off, on].every(x => x.errors.length === 0);
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
