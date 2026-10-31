// Settings page (settings.html), owner 2026-10-31: Appearance → Text size slider runs 50–150 % (100 % in the middle)
// and has a Reset button at the end of its line (the slider is shorter for it). Reset is greyed out at 100 %; a tap
// shows 100 % and calls AndroidSettings.setTextZoom(100) once. A pinch may still have set more than 150 % (up to 300):
// the number then shows the real size and Reset works. Slider, number and button stay on one line on a 360 px phone.
// Run: node settings-text-size.mjs   (or all checks: npm run check). Exit code 0 = pass.
// Optional: SCREENSHOT=/path.png saves the Text size row.
import { launch, pageUrl } from './common.mjs';
const b = await launch();
async function state(zoom, width, tap) {
  const p = await b.newPage({ viewport: { width, height: 915 } });
  const errors = []; p.on('pageerror', e => errors.push(String(e)));
  await p.addInitScript(z => {
    const noop = () => {};
    window.__calls = [];
    window.AndroidSettings = new Proxy({ getSettingsJson: () => JSON.stringify({ max_live_tabs: 3, text_zoom: z }),
      setTextZoom: v => window.__calls.push(v) }, { get: (t, k) => t[k] || noop });
    window.AndroidAccounts = new Proxy({ getAccountsJson: () => JSON.stringify({ accounts: [], providers: [], defaults: {}, order: [] }) },
      { get: (t, k) => t[k] || (() => '{}') });
    window.AndroidAppearance = new Proxy({}, { get: () => () => '{}' });
    window.AndroidStorage = new Proxy({}, { get: () => () => {} });
  }, zoom);
  await p.goto(pageUrl('settings.html'));
  await p.waitForTimeout(400);
  const slider = p.locator('#text_zoom'), reset = p.getByRole('button', { name: 'Reset text size' });
  await slider.scrollIntoViewIfNeeded();
  const range = await slider.evaluate(s => [s.min, s.max, s.step, s.value].join('/'));
  const label = await p.locator('#text_zoom_value').textContent();
  const disabled = await reset.isDisabled();
  // One line: the three share a vertical centre; the button ends inside the row; the slider is the shrinking part.
  const boxes = await p.evaluate(() => ['#text_zoom', '#text_zoom_value', '#text_zoom_reset']
    .map(id => document.querySelector(id).getBoundingClientRect()).map(r => ({ l: r.left, r: r.right, c: (r.top + r.bottom) / 2, w: r.width })));
  const row = await p.evaluate(() => document.querySelector('#text_zoom').closest('.row').getBoundingClientRect().right);
  const oneLine = Math.abs(boxes[0].c - boxes[2].c) < 2 && Math.abs(boxes[1].c - boxes[2].c) < 2
    && boxes[0].r <= boxes[1].l && boxes[1].r <= boxes[2].l && boxes[2].r <= row && boxes[0].w > 100;
  let after = null;
  if (tap) {
    await reset.click();
    after = { label: await p.locator('#text_zoom_value').textContent(), value: await slider.inputValue(),
      disabled: await reset.isDisabled() };
    if (process.env.SCREENSHOT) await p.locator('#text_zoom').locator('xpath=ancestor::div[contains(@class,"row")]').first().screenshot({ path: process.env.SCREENSHOT });
  }
  const calls = await p.evaluate(() => window.__calls);
  await p.close();
  return { range, label, disabled, oneLine, sliderWidth: Math.round(boxes[0].w), after, calls, errors };
}
const normal = await state(100, 360);
const bigger = await state(130, 360, true);
const pinched = await state(220, 412, true);
const narrow = await state(150, 320);
await b.close();
console.log(JSON.stringify({ normal, bigger, pinched, narrow }));
const reset = x => x.after && x.after.label === '100 %' && x.after.value === '100' && x.after.disabled
  && JSON.stringify(x.calls) === '[100]';
const ok = normal.range === '50/150/10/100' && normal.disabled && normal.calls.length === 0
  && bigger.label === '130 %' && !bigger.disabled && reset(bigger)
  && pinched.label === '220 %' && !pinched.disabled && pinched.range.endsWith('/150') && reset(pinched)
  && [normal, bigger, pinched, narrow].every(x => x.oneLine && x.errors.length === 0);
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
