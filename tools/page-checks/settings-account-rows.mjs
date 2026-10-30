// Settings → Accounts → Manage accounts, one row per account (owner, 2026-10-30): no "In the menu" / "Hidden from the
// menu" text any more; an eye button left of ⋮ shows and changes whether the account is in the account menu (a slashed
// eye = hidden); the "Open" chip sits under the name of the account on screen; other accounts get no second line.
// The eye tells the app once (setAccountVisible), never opens the account, and keeps the keyboard focus on the eye.
// Run: node settings-account-rows.mjs (or npm run check). Exit code 0 = pass. SCREENSHOT=/path.png saves the screen.
import { launch, pageUrl } from './common.mjs';

const b = await launch();
const p = await b.newPage({ viewport: { width: 412, height: 915 } });
const errors = []; p.on('pageerror', e => errors.push(String(e)));
await p.addInitScript(() => {
  const noop = () => {};
  const data = {
    multiAccount: true, current: 'acct_2', defaults: {},
    providers: [
      { id: 'duck', name: 'duck.ai', badge: 'D', color: '#DE5833', canAdd: false, accounts: [{ id: 'duck', name: 'duck.ai', visible: true }] },
      { id: 'claude', name: 'Claude', badge: 'C', color: '#D97757', canAdd: true, accounts: [
        { id: 'claude', name: 'Claude', visible: true }, { id: 'acct_2', name: 'Work', visible: false }] },
      { id: 'chatgpt', name: 'ChatGPT', badge: 'G', color: '#10A37F', canAdd: true, accounts: [
        { id: 'chatgpt', name: 'ChatGPT', visible: true }] }]
  };
  const order = () => data.providers.flatMap(p => p.accounts).filter(a => a.visible).map(a => a.id);
  window.__calls = [];
  const json = () => JSON.stringify(Object.assign({}, data, { order: order() }));
  window.AndroidSettings = new Proxy({ getSettingsJson: () => JSON.stringify({ max_live_tabs: 3 }) }, { get: (t, k) => t[k] || noop });
  window.AndroidAccounts = new Proxy({
    getAccountsJson: json,
    setAccountVisible: (id, visible) => {
      window.__calls.push(['setAccountVisible', id, visible]);
      for (const p of data.providers) for (const a of p.accounts) if (a.id === id) a.visible = visible;
      return json();
    },
    openAccount: id => { window.__calls.push(['openAccount', id]); }
  }, { get: (t, k) => t[k] || (() => '{}') });
  window.AndroidAppearance = new Proxy({}, { get: () => () => '{}' });
  window.AndroidStorage = new Proxy({}, { get: () => noop });
});
await p.goto(pageUrl('settings.html'));
await p.waitForTimeout(300);
await p.click('#manageRow');
await p.waitForTimeout(300);
const rows = () => p.evaluate(() => [...document.querySelectorAll('#accountGroups .row.clickable')].map(r => {
  const btns = [...r.querySelectorAll(':scope > .icon-btn, :scope > .row-actions > .icon-btn')];
  const eye = r.querySelector('.eye-btn');
  return {
    title: r.querySelector('.title').textContent,
    sub: r.querySelector('.text .sub') ? r.querySelector('.text .sub').textContent : null,
    chipUnderName: !!r.querySelector('.text .sub .chip'), chipInTitle: !!r.querySelector('.title .chip'),
    text: r.textContent,
    eyeBeforeMore: btns.length === 2 && btns[0] === eye && btns[1].getAttribute('aria-label').startsWith('Options'),
    pressed: eye && eye.getAttribute('aria-pressed'), label: eye && eye.getAttribute('aria-label'),
    path: eye && eye.querySelector('path').getAttribute('d').slice(0, 12)
  };
}));
const before = await rows();
if (process.env.SCREENSHOT) await p.screenshot({ path: process.env.SCREENSHOT });
// Show "Work" again with its eye (keyboard: focus + Enter, like a TalkBack double-tap on a focused button).
await p.focus('.eye-btn[data-id="acct_2"]');
await p.keyboard.press('Enter');
await p.waitForTimeout(200);
const after = await rows();
const focused = await p.evaluate(() => document.activeElement && document.activeElement.dataset.id);
const calls = await p.evaluate(() => window.__calls);
await b.close();
console.log(JSON.stringify({ before, after, focused, calls, errors }, null, 1));
const byTitle = (list, t) => list.find(r => r.title === t) || {};
const ok = before.length === 4 && errors.length === 0
  && before.every(r => !/In the menu|Hidden from the menu/.test(r.text) && r.eyeBeforeMore && !r.chipInTitle)
  && byTitle(before, 'Work').pressed === 'false' && byTitle(before, 'Claude').pressed === 'true'
  && byTitle(before, 'Work').path !== byTitle(before, 'Claude').path           // slashed eye vs open eye
  && byTitle(before, 'Work').chipUnderName && byTitle(before, 'Work').sub === 'Open'
  && byTitle(before, 'Claude').sub === null && byTitle(before, 'ChatGPT').sub === null // no empty second line
  && byTitle(before, 'duck.ai').sub === 'No login needed'
  && byTitle(before, 'Work').label === 'Show Work in the menu'
  && JSON.stringify(calls) === JSON.stringify([['setAccountVisible', 'acct_2', true]])
  && byTitle(after, 'Work').pressed === 'true' && byTitle(after, 'Work').path === byTitle(before, 'Claude').path
  && focused === 'acct_2';
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
