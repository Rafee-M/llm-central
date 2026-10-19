// Saved chats window (chats_viewer.html), R4-7 + R4-34 (VERIFICATION.md):
// - a markdown link in a saved answer becomes a link only for http(s) addresses (a "javascript:" link would run with the
//   window's bridge, which can read every saved chat);
// - the Download buttons work for a file named with an apostrophe ("John's notes.pdf");
// - each saved file keeps its own name and real type (PDF stays .pdf/application/pdf, PNG stays .png/image/png);
// - tapping a picture opens the full view.
// Run: node viewer-links-and-files.mjs   (or all checks: npm run check). Exit code 0 = pass.
import { launch, pageUrl } from './common.mjs';
const dump = { indexedDB: { savedAIChatData: { chats: [ {
  id: 'c1', title: 'Test chat', lastEdit: Date.now(),
  messages: [
    { role: 'user', content: 'see file', files: [ { url: 'data:application/pdf;base64,AAAA', name: "John's notes.pdf", type: 'file' } ] },
    { role: 'assistant', content: 'Click [evil](javascript:window.__pwned=1) or [ok](https://example.com) now',
      images: [ { url: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', id: 'pic1' } ] }
  ] } ] } } };
const browser = await launch();
const page = await browser.newPage({ viewport: { width: 412, height: 915 } });
const errors = [];
page.on('pageerror', e => errors.push(String(e)));
await page.addInitScript(json => {
  window.__blobs = [];
  window.AndroidChatsViewer = {
    getChatsJson: () => json,
    processBlob: (d, m, cd, u) => window.__blobs.push({ cd: cd, m: m }),
    copyToClipboard: () => {}, dismissViewer: () => {}
  };
  window.AndroidTheme = { css: () => '' };
}, JSON.stringify(dump));
await page.goto(pageUrl('chats_viewer.html'));
await page.waitForTimeout(800);
await page.locator('text=Test chat').first().click();
await page.waitForTimeout(500);
const hrefs = await page.$$eval('a[href]', as => as.map(a => a.getAttribute('href')));
const hasJsLink = hrefs.some(h => /^\s*javascript:/i.test(h));
const hasOkLink = hrefs.includes('https://example.com');
await page.locator('.image-download-btn').nth(0).click();   // the attached PDF
await page.locator('.image-download-btn').nth(1).click();   // the PNG picture card (name hint = its id)
await page.waitForTimeout(200);
const blobs = await page.evaluate(() => window.__blobs);
await page.locator('.chat-generated-img').first().click();
await page.waitForTimeout(200);
const lightbox = await page.evaluate(() => !!document.querySelector('#lightboxModal.active'));
await page.locator('#lightboxDownloadBtn').click();           // the picture from the full view
await page.waitForTimeout(200);
const all = await page.evaluate(() => window.__blobs);
const result = { hasJsLink, hasOkLink, all, lightbox, errors };
console.log(JSON.stringify(result, null, 1));
await browser.close();
// R4-34: each file keeps its own name and real type; pictures get the extension of their real type.
const want = [
  ['filename="John\'s notes.pdf"', 'application/pdf'],
  ['filename="pic1.png"', 'image/png'],
  ['filename="ai_generated_image.png"', 'image/png'],
];
const namesOk = all.length === 3 && want.every((w, i) => all[i].cd.endsWith(w[0]) && all[i].m === w[1]);
const ok = !hasJsLink && hasOkLink && namesOk && lightbox && errors.length === 0;
console.log(ok ? 'PASS' : 'FAIL');
process.exit(ok ? 0 : 1);
