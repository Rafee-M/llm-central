// Development/CI tool (not part of the app): writes ONLY the README's "Downloads" chip into artwork/readme/.
//
//   cd tools/readme-badges && npm install && npm run generate:downloads
//
// It is a companion of generate.mjs and draws the chip with the very same code (shapes, sizes, text positions,
// Material 3 tone recipe, Google Sans outlines), so chip-downloads-{light,dark}.svg are indistinguishable in style
// from chip-android / chip-release / chip-apk / chip-license. It needs no Gradle file and no built APK, so it can
// run in CI on a schedule.
//
// WHERE THE NUMBER COMES FROM: GitHub counts, per release asset, how many times its download URL was requested
// ("download_count" in the Releases API). This script sums that field over the matching assets of all releases.
//   DOWNLOADS=1234        use this number instead of asking GitHub (for testing)
//   GITHUB_REPOSITORY     owner/name (set automatically in GitHub Actions; default below)
//   GH_TOKEN / GITHUB_TOKEN  optional; raises the API rate limit from 60 to 1000+ requests per hour
//   ASSET_SUFFIX=.apk     only count assets whose name ends with this ("" = count every asset)
//   LATEST_ONLY=1         count only the latest release instead of all releases
// If GitHub can't be reached the script exits with an error and leaves the existing SVGs untouched.

import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { fileURLToPath } from 'url';
import path from 'path';
import opentype from 'opentype.js';
import { argbFromHex, hexFromArgb, Hct } from '@material/material-color-utilities';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../..');
const OUT = path.join(ROOT, 'artwork/readme');

// ---------------------------------------------------------------- inputs (keep in sync with generate.mjs)
const BRAND = '#019971'; // the banner's teal (artwork/banner.svg)

const REPO = process.env.GITHUB_REPOSITORY || 'Rafee-M/llm-central';
const ASSET_SUFFIX = process.env.ASSET_SUFFIX ?? '.apk';
const LATEST_ONLY = process.env.LATEST_ONLY === '1';

// Material icon (Apache 2.0, google/material-design-icons, 24 × 24): cloud_download.
const ICON_CLOUD_DOWNLOAD = 'M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z';

const CHIP = { file: 'chip-downloads', label: 'Downloads', icon: ICON_CLOUD_DOWNLOAD };

// ---------------------------------------------------------------- the number
async function countDownloads() {
    if (process.env.DOWNLOADS) return Math.max(0, Math.floor(+process.env.DOWNLOADS));
    const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'readme-badges' };
    const token = process.env.GH_TOKEN || process.env.GITHUB_TOKEN;
    if (token) headers.Authorization = 'Bearer ' + token;
    const get = async (url) => {
        const res = await fetch(url, { headers });
        if (!res.ok) throw new Error(`${url} → ${res.status} ${res.statusText}`);
        return res.json();
    };
    const sum = (rel) => rel.assets
        .filter((a) => a.name.endsWith(ASSET_SUFFIX))
        .reduce((n, a) => n + a.download_count, 0);
    if (LATEST_ONLY) return sum(await get(`https://api.github.com/repos/${REPO}/releases/latest`));
    let total = 0;
    for (let page = 1; ; page++) {
        const releases = await get(`https://api.github.com/repos/${REPO}/releases?per_page=100&page=${page}`);
        total += releases.reduce((n, rel) => n + sum(rel), 0);
        if (releases.length < 100) break; // last page
    }
    return total;
}
/** 0…999 as is; 1.2k, 12k, 123k; 1.2M … (one decimal below 10, none above; a trailing ".0" is dropped). */
const compact = (n) => n < 1000 ? String(n)
    : n < 999500 ? (n / 1e3).toFixed(n < 1e4 ? 1 : 0).replace(/\.0$/, '') + 'k'
    : (n / 1e6).toFixed(1).replace(/\.0$/, '') + 'M';

let downloads;
try {
    downloads = await countDownloads();
} catch (e) {
    console.error('Could not read the download count; nothing was written.\n ', e.message);
    process.exit(1);
}

// ---------------------------------------------------------------- colours (same recipe as generate.mjs)
// Each part = [tone, chroma] on BRAND's hue; only the four parts a chip uses are listed here.
const TONES = {
    light: { labelBg: [39, 93], label: [100, 0], valueBg: [90, 13], value: [10, 14] },
    dark:  { labelBg: [80, 29], label: [20, 65], valueBg: [22, 14], value: [90, 13] },
};
// Fixed colours that win over TONES, e.g. { light: { labelBg: '#019971' } }. Keep identical to generate.mjs.
const OVERRIDE = { light: {}, dark: {} };

const HUE = Hct.fromInt(argbFromHex(BRAND)).hue;
function colours(mode) {
    const out = {};
    for (const [part, [tone, chroma]] of Object.entries(TONES[mode])) {
        out[part] = OVERRIDE[mode][part] || hexFromArgb(Hct.from(HUE, chroma, tone).toInt()).toUpperCase();
    }
    return out;
}
const MODES = { light: colours('light'), dark: colours('dark') };

// ---------------------------------------------------------------- text as outlines (same as generate.mjs)
const fontFile = (w) => path.join(HERE, 'node_modules/@fontsource/google-sans/files/google-sans-latin-' + w + '-normal.woff');
const toArrayBuffer = (b) => b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength);
const MEDIUM = opentype.parse(toArrayBuffer(readFileSync(fontFile(500))));
const BOLD = opentype.parse(toArrayBuffer(readFileSync(fontFile(700))));
const width = (font, text, size) => font.getAdvanceWidth(text, size);
/** Text as one <path>; (x, baseline) like SVG <text>. */
function text(font, str, size, x, baseline, fill) {
    const d = font.getPath(str, x, baseline, size).toPathData(2);
    return `<path fill="${fill}" d="${d}"/>`;
}
/** The font size at which `sample` is exactly `height` tall (ink, as measured on Flow's badges). */
function sizeForHeight(font, sample, height) {
    const box = font.getPath(sample, 0, 0, 100).getBoundingBox();
    return height / ((box.y2 - box.y1) / 100);
}

const r1 = (n) => Math.round(n * 10) / 10;
/** A 24 × 24 icon scaled to `size`, top-left at (x, y). */
const icon = (d, x, y, size, fill) =>
    `<path fill="${fill}" transform="translate(${r1(x)} ${r1(y)}) scale(${size / 24})" d="${d}"/>`;

// ---------------------------------------------------------------- shapes (same as generate.mjs)
/** Rectangle with separate left/right corner radii (a joined-group segment). */
function segment(x, w, h, left, right, fill) {
    return `<path fill="${fill}" d="M${r1(x + left)} 0H${r1(x + w - right)}A${right} ${right} 0 0 1 ${r1(x + w)} ${right}`
        + `V${h - right}A${right} ${right} 0 0 1 ${r1(x + w - right)} ${h}H${r1(x + left)}A${left} ${left} 0 0 1 ${r1(x)} ${h - left}`
        + `V${left}A${left} ${left} 0 0 1 ${r1(x + left)} 0Z"/>`;
}
const svg = (w, h, label, body) =>
    `<svg xmlns="http://www.w3.org/2000/svg" width="${r1(w)}" height="${h}" viewBox="0 0 ${r1(w)} ${h}" role="img" `
    + `aria-label="${label}"><title>${label}</title>${body}</svg>\n`;

// ---------------------------------------------------------------- the chip (same numbers as generate.mjs)
// Measured on Flow's badges, in SVG units. The size is derived from the word "Android" exactly as in generate.mjs,
// so every chip gets the identical font size whatever its own text is.
const CHIP_TEXT = sizeForHeight(MEDIUM, 'Android', 10.6);     // chip texts: baseline 21.2

/** Info chip [icon label][value]: 32 tall; outer radius 16, inner 5, 3 apart; 18-unit icon at x 10; label at 34.5. */
function chip(ch, c) {
    const h = 32, iconSize = 18, inner = 5, split = 3, baseline = 21.2;
    const leftW = 34.5 + width(MEDIUM, ch.label, CHIP_TEXT) + 13;
    const rightW = 12.4 + width(BOLD, ch.value, CHIP_TEXT) + 14.7;
    const w = leftW + split + rightW;
    return svg(w, h, ch.label + ': ' + ch.value,
        segment(0, leftW, h, h / 2, inner, c.labelBg)
        + icon(ch.icon, 10, (h - iconSize) / 2, iconSize, c.label)
        + text(MEDIUM, ch.label, CHIP_TEXT, 34.5, baseline, c.label)
        + segment(leftW + split, rightW, h, inner, h / 2, c.valueBg)
        + text(BOLD, ch.value, CHIP_TEXT, leftW + split + 12.4, baseline, c.value));
}

mkdirSync(OUT, { recursive: true });
const value = compact(downloads);
const written = [];
for (const [mode, c] of Object.entries(MODES)) {
    const file = `${CHIP.file}-${mode}.svg`;
    writeFileSync(path.join(OUT, file), chip({ ...CHIP, value }, c));
    written.push(file);
}
console.log('downloads', { repo: REPO, total: downloads, shown: value, assets: ASSET_SUFFIX || 'all', latestOnly: LATEST_ONLY });
console.log('wrote', written.join(', '), 'to artwork/readme/');
