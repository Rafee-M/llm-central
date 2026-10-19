// Saved chats window (chats_viewer.html), R4-17: the hidden debug panel's text is made only when the panel is opened
// (tap the "Saved Chats" title), with long picture data shortened; "Copy Debug JSON" copies that same text; pictures
// are still shown and saved at full size. Uses a backup with a 300 KB picture and counts how much JSON text the page
// builds while the panel is hidden (must be none).
// Run: node viewer-debug-panel.mjs   (or all checks: npm run check). Exit code 0 = pass.
import { launch, pageUrl } from './common.mjs';
const BIG = 'data:image/png;base64,' + 'A'.repeat(300000);
const dump = { indexedDB: { savedAIChatData: { chats: [ { id: 'c1', title: 'Test chat', lastEdit: Date.now(),
  messages: [ { role: 'user', content: 'hi' },
              { role: 'assistant', content: 'here', images: [ { url: BIG, id: 'pic1' } ] } ] } ] } } };
const b = await launch();
const p = await b.newPage({ viewport: { width: 412, height: 915 } });
const errors = []; p.on('pageerror', e => errors.push(String(e)));
await p.addInitScript(json => {
  window.__maxStringify = 0; window.__copied = null; window.__blobs = [];
  const orig = JSON.stringify;
  JSON.stringify = function () { const r = orig.apply(JSON, arguments); if (typeof r === 'string') window.__maxStringify = Math.max(window.__maxStringify, r.length); return r; };
  window.AndroidChatsViewer = { getChatsJson: () => json, processBlob: (d, m, cd) => window.__blobs.push(d.length),
    copyToClipboard: t => { window.__copied = t; }, dismissViewer: () => {} };
  window.AndroidTheme = { css: () => '' };
}, JSON.stringify(dump));
await p.goto(pageUrl('chats_viewer.html'));
await p.waitForTimeout(800);
const whileHidden = await p.evaluate(() => ({ maxStringify: window.__maxStringify, panelText: document.getElementById('debugOutput').textContent.length }));
await p.locator('h1:has-text("Saved Chats")').click();          // open the debug panel
await p.waitForTimeout(200);
const panel = await p.evaluate(() => document.getElementById('debugOutput').textContent);
await p.locator('text=Copy Debug JSON').click();
const copied = await p.evaluate(() => window.__copied);
await p.locator('h1:has-text("Saved Chats")').click();          // close it again
await p.locator('text=Test chat').first().click();
await p.waitForTimeout(400);
const imgSrcLen = await p.evaluate(() => (document.querySelector('.chat-generated-img') || {}).src?.length || 0);
await p.locator('.image-download-btn').first().click();
const saved = await p.evaluate(() => window.__blobs);
await b.close();
const r = { whileHidden, panelLen: panel.length, panelHasStructure: panel.includes('"savedAIChatData"') && panel.includes('characters)'),
  copiedSame: copied === panel, imgSrcLen, saved, errors };
console.log(JSON.stringify(r));
const ok = whileHidden.panelText === 0 && whileHidden.maxStringify < 20000 && r.panelHasStructure && panel.length < 20000
  && r.copiedSame && imgSrcLen === BIG.length && saved.length === 1 && saved[0] === BIG.length && errors.length === 0;
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
