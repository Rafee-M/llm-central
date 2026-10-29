// Provider probe (summary.md §5.4): opens a chat site the way an LLM Central tab would — phone size, WebView user agent,
// the app's tracker list applied with web/TrackerBlocker's rules (empty 403) — and reports what matters when adding or
// checking a provider:
//   - cost: load time, requests, transfer (known sizes), JavaScript heap, DOM nodes, main-thread time (median of runs);
//   - main-frame navigations, frames, and every host the page talks to (to choose `inAppDomains`, check sign-in hosts,
//     and review telemetry for assets/trackers.txt);
//   - which requests the tracker list blocked, and page errors (did blocking break anything?);
//   - our injected scripts on the real page: blob_capture.js (errors, time) and prefill_prompt.js (answer, result).
// Development only: needs the live site (many refuse automated browsers), is not part of `npm run check` or CI, and
// sends no prompt (it only types into the box). Usage, in Claude's sandbox:
//   cd tools/provider-probe && ln -sfn /opt/node-tools/node_modules node_modules &&
//   CHROMIUM_PATH=/opt/pw-browsers/chromium node probe.mjs https://gemini.google.com/app [runs=3] [--no-block]; rm node_modules
import { chromium } from 'playwright';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import path from 'path';

const ASSETS = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../app/src/main/assets');
const url = process.argv[2];
const runs = Math.max(1, parseInt(process.argv[3], 10) || 1);
const block = !process.argv.includes('--no-block');
if (!url) { console.error('usage: node probe.mjs <url> [runs] [--no-block]'); process.exit(2); }
// A typical Android WebView user agent (the "; wv" mark and "Version/4.0" are what sites see from the app).
const UA = 'Mozilla/5.0 (Linux; Android 14; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.0.0 Mobile Safari/537.36';

/** Same rules as web/TrackerBlocker.shouldBlock: ?query names, host + parent domains, host/path prefixes; pages never. */
function trackerRule(text) {
  const hosts = new Set(), paths = {}, names = new Set();
  for (let line of text.split('\n')) {
    line = line.trim();
    if (!line || line.startsWith('#')) continue;
    if (line.startsWith('?')) names.add(line.slice(1));
    else if (line.includes('/')) { const i = line.indexOf('/'); (paths[line.slice(0, i)] ||= []).push(line.slice(i)); }
    else hosts.add(line);
  }
  return (address, mainFrame) => {
    if (mainFrame) return false;
    const u = new URL(address);
    if (u.protocol !== 'https:' && u.protocol !== 'http:') return false;
    for (const part of u.search.slice(1).split('&')) if (part && names.has(part.split('=')[0])) return true;
    let h = u.hostname.toLowerCase();
    for (;;) {
      if (hosts.has(h)) return true;
      for (const prefix of paths[h] || []) if (u.pathname.startsWith(prefix)) return true;
      const dot = h.indexOf('.');
      if (dot < 0) return false;
      h = h.slice(dot + 1);
    }
  };
}

const blocked = block ? trackerRule(readFileSync(path.join(ASSETS, 'trackers.txt'), 'utf8')) : () => false;
const blobCapture = readFileSync(path.join(ASSETS, 'js/blob_capture.js'), 'utf8');
const prefill = readFileSync(path.join(ASSETS, 'js/prefill_prompt.js'), 'utf8').trim();
const proxy = process.env.HTTPS_PROXY ? { server: process.env.HTTPS_PROXY } : undefined;
const browser = await chromium.launch({ ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}), proxy });

const results = [];
let detail;
for (let run = 0; run < runs; run++) {
  // A fresh context per run = a first visit (empty cache), like a newly loaded account.
  const context = await browser.newContext({ userAgent: UA, viewport: { width: 412, height: 915 }, isMobile: true,
    hasTouch: true, ignoreHTTPSErrors: !!proxy });
  const page = await context.newPage();
  const hosts = {}, blockedHits = {}, navigations = [], errors = [];
  let requests = 0, bytes = 0;
  await page.route('**/*', route => {
    const request = route.request();
    const mainFrame = request.isNavigationRequest() && request.frame() === page.mainFrame();
    const u = new URL(request.url());
    if (blocked(request.url(), mainFrame)) {
      const key = u.host + u.pathname.split('/').slice(0, 3).join('/');
      blockedHits[key] = (blockedHits[key] || 0) + 1;
      return route.fulfill({ status: 403, body: '', contentType: 'text/plain' });
    }
    requests++;
    hosts[u.host] = (hosts[u.host] || 0) + 1;
    if (mainFrame) navigations.push(request.url().slice(0, 120));
    return route.continue();
  });
  page.on('response', r => { bytes += +(r.headers()['content-length'] || 0); });
  page.on('pageerror', e => errors.push(String(e).slice(0, 160)));
  const cdp = await context.newCDPSession(page);
  await cdp.send('Performance.enable');
  const started = Date.now();
  await page.goto(url, { waitUntil: 'load', timeout: 60000 });
  const loadMs = Date.now() - started;
  await page.waitForTimeout(5000); // let the app finish its start-up work
  const m = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map(x => [x.name, x.value]));
  // The app's scripts, run the way MainActivity runs them (evaluateJavascript) on a non-duck page. eval here only
  // runs the app's own asset files inside this throw-away browser; nothing from the website is evaluated.
  const blobCaptureMs = await page.evaluate(src => { const t = performance.now(); eval(src); return +(performance.now() - t).toFixed(2); }, blobCapture);
  const prefillAnswers = [];
  for (let i = 0; i < 6; i++) { // web/PromptFiller polls every 0.5 s until "filled" or "busy"
    const answer = await page.evaluate(code => eval(code), prefill + '(' + JSON.stringify('Probe line 1\nProbe line 2') + ')');
    prefillAnswers.push(answer);
    if (answer === 'filled' || answer === 'busy') break;
    await page.waitForTimeout(500);
  }
  await page.waitForTimeout(500);
  const box = await page.evaluate(() => {
    const boxes = [...document.querySelectorAll('textarea, [contenteditable="true"], [contenteditable=""]')]
      .filter(e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; });
    const b = boxes[boxes.length - 1];
    return b ? { tag: b.tagName, id: b.id, class: String(b.className).slice(0, 80),
      text: (b.tagName === 'TEXTAREA' ? b.value : b.innerText).split('\n').filter(Boolean) } : null;
  });
  results.push({ loadMs, requests, MB: +(bytes / 1048576).toFixed(1), heapMB: +(m.JSHeapUsedSize / 1048576).toFixed(1),
    nodes: m.Nodes, mainThreadS: +m.TaskDuration.toFixed(2), blobCaptureMs, errors: errors.length });
  if (!detail) {
    detail = { navigations, frames: page.frames().map(f => f.url().slice(0, 100)).filter(f => f && f !== 'about:blank'),
      hosts: Object.entries(hosts).sort((a, b) => b[1] - a[1]), blocked: blockedHits, errors, prefillAnswers, box };
  }
  await context.close();
}
await browser.close();

const median = key => { const v = results.map(r => r[key]).sort((a, b) => a - b); return v[Math.floor(v.length / 2)]; };
console.log(JSON.stringify({ url, runs, trackerList: block,
  median: Object.fromEntries(Object.keys(results[0]).map(k => [k, median(k)])), runsDetail: results, firstRun: detail }, null, 1));
