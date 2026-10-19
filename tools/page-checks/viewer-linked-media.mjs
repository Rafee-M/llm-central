// Saved chats window (chats_viewer.html), R4-17 regression check: pictures saved outside a chat (duck.ai's blobMap and
// page pictures, "domImages") still reach their chats: one linked by id, one added to a "draw…" chat.
// Run: node viewer-linked-media.mjs   (or all checks: npm run check). Exit code 0 = pass.
import { launch, pageUrl } from './common.mjs';
const PIC_A = 'data:image/png;base64,' + 'B'.repeat(5000);
const PIC_B = 'data:image/jpeg;base64,' + 'C'.repeat(5000);
const dump = {
  blobMap: { 'blob:https://duck.ai/1': PIC_B },
  domImages: [ { src: 'blob:https://duck.ai/2', alt: 'imgA', dataUrl: PIC_A } ],
  indexedDB: { savedAIChatData: { chats: [
    { id: 'c1', title: 'By id', lastEdit: Date.now(), messages: [ { role: 'user', content: 'see', content2: 0 },
      { role: 'assistant', content: { text: 'pic', images: [ { id: 'imgA' } ] } } ] },
    { id: 'c2', title: 'Draw a cat', lastEdit: Date.now() - 1000, messages: [ { role: 'user', content: 'draw a cat' },
      { role: 'assistant', content: 'done' } ] } ] } } };
const b = await launch();
const p = await b.newPage({ viewport: { width: 412, height: 915 } });
const errors = []; p.on('pageerror', e => errors.push(String(e)));
await p.addInitScript(json => { window.AndroidChatsViewer = { getChatsJson: () => json, processBlob(){}, copyToClipboard(){}, dismissViewer(){} };
  window.AndroidTheme = { css: () => '' }; }, JSON.stringify(dump));
await p.goto(pageUrl('chats_viewer.html'));
await p.waitForTimeout(800);
const srcs = [];
for (const title of ['By id', 'Draw a cat']) {
  await p.locator(`text=${title}`).first().click();
  await p.waitForTimeout(300);
  srcs.push(await p.evaluate(() => [...document.querySelectorAll('.chat-generated-img')].map(i => i.src.slice(0, 30) + '|' + i.src.length)));
  await p.evaluate(() => hideDetails());
  await p.waitForTimeout(200);
}
await b.close();
console.log(JSON.stringify({ srcs, errors }));
const ok = srcs[0].some(s => s.endsWith('|' + PIC_A.length)) && srcs[1].length === 1 && errors.length === 0;
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
