// prefill_prompt.js (web/PromptFiller: shared text / "Ask AI" typed into the chat box), 2026-10-29 (Gemini):
// the prompt arrives exactly once and keeps its line breaks in three kinds of message box:
//  - a Quill editor (Gemini: div.ql-editor). Stand-in for what Quill does with a "paste" event (measured on
//    gemini.google.com): the cursor moves into Quill's hidden clipboard element for one tick, then Quill takes what
//    landed there and flattens it into one line. Text inserted during that tick is lost into it.
//  - a ProseMirror-like editor (ChatGPT, Claude) that handles the paste itself: it must still get the paste.
//  - a <textarea> (DeepSeek, Z.ai).
// Run: node prefill-editors.mjs (or npm run check). Exit code 0 = pass.
import { launch, assetPath } from './common.mjs';
import { readFileSync } from 'fs';

// eval below runs the app's own prefill_prompt.js in a throw-away test page, exactly as WebView's evaluateJavascript
// does in the app; the stand-in editors' innerHTML only ever receives escaped text. Development-only, never shipped.
const fill = readFileSync(assetPath('js/prefill_prompt.js'), 'utf8').trim();
const PROMPT = 'Hello\nline 2 <b>x</b>';
const PAGES = {
  quill: `<div class="ql-editor" contenteditable="true" style="min-height:40px"><p><br></p></div>
    <div class="ql-clipboard" contenteditable="true" tabindex="-1" style="position:absolute;left:-9999px"></div>
    <script>
      const ed = document.querySelector('.ql-editor'), clip = document.querySelector('.ql-clipboard');
      window.pastes = 0;
      ed.addEventListener('paste', () => { // Quill 1.x clipboard.onCapturePaste, simplified: no preventDefault
        window.pastes++; clip.focus();
        setTimeout(() => { const t = clip.innerText.replace(/\\n/g, ''); clip.innerHTML = '';
          if (t) { ed.innerHTML = '<p>' + t.replace(/</g, '&lt;') + '</p>'; } ed.focus(); }, 1);
      }, true);
    </script>`,
  prosemirror: `<div class="ProseMirror" contenteditable="true" style="min-height:40px"></div>
    <script>
      const ed = document.querySelector('.ProseMirror'); window.pastes = 0;
      ed.addEventListener('paste', e => { window.pastes++; e.preventDefault();
        for (const line of e.clipboardData.getData('text/plain').split('\\n')) { const p = document.createElement('p'); p.textContent = line; ed.append(p); } });
    </script>`,
  textarea: `<textarea id="prompt-textarea" style="height:60px"></textarea><script>window.pastes = 0;</script>`,
};

const b = await launch();
const results = {};
for (const [name, html] of Object.entries(PAGES)) {
  const p = await b.newPage({ viewport: { width: 412, height: 915 } });
  const errors = []; p.on('pageerror', e => errors.push(String(e)));
  await p.setContent('<!doctype html><body>' + html + '</body>');
  const answers = [];
  for (let i = 0; i < 4; i++) {         // PromptFiller polls every 0.5 s until "filled" or "busy"
    const a = await p.evaluate(code => eval(code), fill + '(' + JSON.stringify(PROMPT) + ')');
    answers.push(a);
    if (a === 'filled' || a === 'busy') break;
    await p.waitForTimeout(500);
  }
  await p.waitForTimeout(300);
  const out = await p.evaluate(() => {
    const box = document.querySelector('textarea, .ql-editor, .ProseMirror');
    return { text: box.tagName === 'TEXTAREA' ? box.value : box.innerText, html: box.innerHTML, pastes: window.pastes };
  });
  const lines = out.text.split('\n').map(s => s.trim()).filter(Boolean);
  results[name] = { answers: answers.join(','), lines, pastes: out.pastes, errors,
    once: (out.text.match(/Hello/g) || []).length === 1,
    lineBreak: lines.length === 2 && lines[0] === 'Hello' && lines[1] === 'line 2 <b>x</b>',
    noHtml: !/<b>/.test(out.html) };
  await p.close();
}
await b.close();
console.log(JSON.stringify(results, null, 1));
const r = results;
const ok = Object.values(r).every(x => x.once && x.lineBreak && x.noHtml && x.errors.length === 0)
  && r.prosemirror.pastes === 1   // ChatGPT/Claude keep getting the paste
  && r.quill.pastes === 0;        // a Quill editor is never sent the paste
console.log(ok ? 'PASS' : 'FAIL'); process.exit(ok ? 0 : 1);
