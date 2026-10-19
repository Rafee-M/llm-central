// Saved chats window (chats_viewer.html), R4-34: savedFile() picks the name and type a downloaded file is saved with.
// Edge cases: a type with parameters, an upper-case extension, a Word file, an unknown type, a non-data: address and a
// quote in the name. Every name must end with an extension (the app's duck.ai naming adds ".md" otherwise).
// Run: node viewer-file-names.mjs   (or all checks: npm run check). Exit code 0 = pass.
import { launch, pageUrl } from './common.mjs';
const b = await launch();
const p = await b.newPage();
await p.addInitScript(() => { window.AndroidTheme = { css: () => '' }; });
await p.goto(pageUrl('chats_viewer.html'));
const cases = [
  ['data:image/jpeg;base64,AA', 'img_1', 'img_1.jpeg', 'image/jpeg'],
  ['data:image/png;base64,AA', null, 'ai_generated_image.png', 'image/png'],
  ['data:application/pdf;base64,AA', 'Report.PDF', 'Report.PDF', 'application/pdf'],
  ['data:text/plain;charset=utf-8;base64,AA', 'notes', 'notes.txt', 'text/plain'],
  ['data:application/vnd.openxmlformats-officedocument.wordprocessingml.document;base64,AA', 'cv.docx', 'cv.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
  ['data:application/x-thing;base64,AA', 'blob', 'blob.jpeg', 'application/x-thing'],
  ['https://x/y.png', 'a', 'a.jpeg', 'image/jpeg'],
  ['data:image/heic;base64,AA', 'photo', 'photo.heic', 'image/heic'],
  ['data:image/png;base64,AA', 'say "hi"', 'say _hi_.png', 'image/png'],
];
let fails = 0;
for (const [u, h, n, t] of cases) {
  const r = await p.evaluate(([u, h]) => savedFile(u, h), [u, h]);
  const ok = r.name === n && r.type === t;
  if (!ok) fails++;
  console.log(ok ? 'ok  ' : 'FAIL', JSON.stringify(h), '->', r.name, r.type);
}
await b.close();
console.log(fails ? 'FAIL' : 'PASS'); process.exit(fails ? 1 : 0);
