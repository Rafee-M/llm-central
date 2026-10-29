// Settings page (settings.html), 2026-10-29 owner decision: "Allow third-party cookies" (Data & tools) is off when the
// app sends no value or false, on when it sends true; one tap calls AndroidSettings.setThirdPartyCookies once with the
// new value; the switch is named by its title for screen readers.
// Run: node settings-third-party-cookies.mjs   (or all checks: npm run check). Exit code 0 = pass.
// Optional: SCREENSHOT=/path.png saves the Data & tools section.
import { launch, pageUrl } from './common.mjs';
const b = await launch();
async function state(value, tap) {
  const p = await b.newPage({ viewport: { width: 412, height: 915 } });
  const errors = []; p.on('pageerror', e => errors.push(String(e)));
  await p.addInitScript(v => {
    const s = { max_live_tabs: 3, block_trackers: true }; if (v !== undefined) s.third_party_cookies = v;
    const noop = () => {};
    window.__calls = [];
    window.AndroidSettings = new Proxy({ getSettingsJson: () => JSON.stringify(s),
      setThirdPartyCookies: on => window.__calls.push(on) }, { get: (t, k) => t[k] || noop });
    window.AndroidAccounts = new Proxy({ getAccountsJson: () => JSON.stringify({ accounts: [], providers: [], defaults: {}, order: [] }) }, { get: (t, k) => t[k] || (() => '{}') });
    window.AndroidAppearance = new Proxy({}, { get: () => () => '{}' });
    window.AndroidStorage = new Proxy({}, { get: noop => () => {} });
  }, value);
  await p.goto(pageUrl('settings.html'));
  await p.waitForTimeout(400);
  const sw = p.getByRole('switch', { name: 'Allow third-party cookies' });
  const named = await sw.count() === 1;
  const before = named ? await sw.isChecked() : null;
  if (tap && named) {
    await sw.scrollIntoViewIfNeeded();
    await p.locator('label:has(#third_party_cookies)').click();
    if (process.env.SCREENSHOT) await p.locator('#data').screenshot({ path: process.env.SCREENSHOT });
  }
  const calls = await p.evaluate(() => window.__calls);
  await p.close();
  return { named, on: before, calls, errors };
}
const missing = await state(undefined, true), off = await state(false), on = await state(true);
await b.close();
console.log(JSON.stringify({ missing, off, on }));
const ok = missing.named && missing.on === false && JSON.stringify(missing.calls) === '[true]'
  && off.on === false && on.on === true && off.calls.length === 0 && on.calls.length === 0
  && [missing, off, on].every(x => x.errors.length === 0);
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
