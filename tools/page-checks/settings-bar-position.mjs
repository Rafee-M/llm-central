// Settings page (settings.html), owner 2026-10-31: Appearance's "Hide status bar" row became two switches.
// "Top bar at the bottom" is off when the app sends no value; "Reverse account order" is ON when the app sends no
// value (owner: on by default). One tap calls AndroidAppearance.setBarAtBottom / setReverseAccounts once with the new
// value, and the page shows what the app answers. Both switches are named by their titles for screen readers, and
// no "Hide status bar" switch is left in Settings (it stays in the account menu).
// Run: node settings-bar-position.mjs   (or all checks: npm run check). Exit code 0 = pass.
// Optional: SCREENSHOT=/path.png saves the Appearance section.
import { launch, pageUrl } from './common.mjs';
const b = await launch();
async function state(appearance, tap) {
  const p = await b.newPage({ viewport: { width: 412, height: 915 } });
  const errors = []; p.on('pageerror', e => errors.push(String(e)));
  await p.addInitScript(a => {
    const noop = () => {};
    window.__calls = [];
    const current = Object.assign({ mode: 'system', palette: 'duck', palettes: [] }, a);
    const answer = () => JSON.stringify(current);
    window.AndroidSettings = new Proxy({ getSettingsJson: () => JSON.stringify({ max_live_tabs: 3 }) },
      { get: (t, k) => t[k] || noop });
    window.AndroidAccounts = new Proxy({ getAccountsJson: () => JSON.stringify({ accounts: [], providers: [], defaults: {}, order: [] }) },
      { get: (t, k) => t[k] || (() => '{}') });
    window.AndroidAppearance = new Proxy({
      getAppearanceJson: answer,
      setBarAtBottom: on => { window.__calls.push(['bar', on]); current.barAtBottom = on; return answer(); },
      setReverseAccounts: on => { window.__calls.push(['reverse', on]); current.reverseAccounts = on; return answer(); },
    }, { get: (t, k) => t[k] || (() => '{}') });
    window.AndroidStorage = new Proxy({}, { get: () => () => {} });
  }, appearance);
  await p.goto(pageUrl('settings.html'));
  await p.waitForTimeout(400);
  const bar = p.getByRole('switch', { name: 'Top bar at the bottom' });
  const rev = p.getByRole('switch', { name: 'Reverse account order' });
  const named = await bar.count() === 1 && await rev.count() === 1;
  const hideLeft = await p.getByRole('switch', { name: 'Hide status bar' }).count();
  const description = named ? await rev.getAttribute('aria-describedby').then(id => p.locator('#' + id).textContent()) : null;
  const before = named ? [await bar.isChecked(), await rev.isChecked()] : null;
  let after = null;
  if (tap && named) {
    await bar.scrollIntoViewIfNeeded();
    await p.locator('label:has(#bar_at_bottom)').click();
    await p.locator('label:has(#reverse_accounts)').click();
    after = [await bar.isChecked(), await rev.isChecked()];
    if (process.env.SCREENSHOT) await p.locator('#appearance').screenshot({ path: process.env.SCREENSHOT });
  }
  const calls = await p.evaluate(() => window.__calls);
  await p.close();
  return { named, hideLeft, description, before, after, calls, errors };
}
const missing = await state({}, true);
const set = await state({ barAtBottom: true, reverseAccounts: false });
await b.close();
console.log(JSON.stringify({ missing, set }));
const ok = missing.named && missing.hideLeft === 0
  && missing.description === 'Applies when the bar is at the bottom'
  && JSON.stringify(missing.before) === '[false,true]'
  && JSON.stringify(missing.calls) === '[["bar",true],["reverse",false]]'
  && JSON.stringify(missing.after) === '[true,false]'
  && JSON.stringify(set.before) === '[true,false]' && set.calls.length === 0
  && [missing, set].every(x => x.errors.length === 0);
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
