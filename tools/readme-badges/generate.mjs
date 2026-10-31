// Development-time tool (not part of the app): writes the README's badges into artwork/readme/.
//
//   cd tools/readme-badges && npm install && npm run generate
//
// The design copies the badges of the Flow app (github.com/Flow-Tube/Flow) exactly: same shapes, sizes, text
// positions and the same Material 3 tone recipe; only the colours are ours (owner, 2026-10-30). Nothing of their files
// is copied: the shapes are re-drawn here from measurements (see the numbers below and CHANGES.md 2026-10-30).
//   * "Get it on …" pills (get-*.svg): 60 tall, fully round; the store's logo on Material 3's 9-lobed "cookie"
//     shape; "Get it on" small, the store's name big.
//   * Info chips (chip-*.svg): two joined segments, 32 tall: round outer ends, 5-unit inner corners, 3 apart.
// Each comes as -light.svg and -dark.svg; the README picks one with <picture> + prefers-color-scheme.
//
// COLOURS: every colour is BRAND's hue (our banner's teal) at a Material tone (lightness) and chroma (colourfulness).
// To change them: (1) BRAND = another hex re-colours everything; (2) TONES = lighter/darker or more/less colourful
// per part; (3) OVERRIDE = a fixed hex for one part. Then run the generator and look at the README in light and dark.
//
// Text: Google Sans (OFL), turned into outlines, because GitHub shows README SVGs as plain images that can't load fonts.
// Version and minimum Android come from app/build.gradle; the APK size from the last release build (or APK_KB=…).

import { readFileSync, writeFileSync, mkdirSync, existsSync, statSync } from 'fs';
import { fileURLToPath } from 'url';
import path from 'path';
import opentype from 'opentype.js';
import { argbFromHex, hexFromArgb, Hct } from '@material/material-color-utilities';
import { siGithub, siFdroid, siObtainium, siAndroid } from 'simple-icons';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../..');
const OUT = path.join(ROOT, 'artwork/readme');

// ---------------------------------------------------------------- inputs
const BRAND = '#019971'; // the banner's teal (artwork/banner.svg)

const gradle = readFileSync(path.join(ROOT, 'app/build.gradle'), 'utf8');
const versionName = (gradle.match(/versionName\s*=\s*"([^"]+)"/) || [])[1];
const minSdk = +(gradle.match(/minSdk\s*=\s*(\d+)/) || [])[1];
if (!versionName || !minSdk) throw new Error('versionName/minSdk not found in app/build.gradle');
// API level → Android version (only the ones this app could plausibly use).
const ANDROID = { 21: '5.0', 23: '6.0', 24: '7.0', 25: '7.1', 26: '8.0', 28: '9', 29: '10', 30: '11', 31: '12', 33: '13' };
if (!ANDROID[minSdk]) throw new Error('Add minSdk ' + minSdk + ' to the ANDROID table');

// APK size: APK_KB=… wins; else the last release build, rounded up to 10 KB ("~270 KB": signing adds a few KB).
const apk = path.join(ROOT, 'app/build/outputs/apk/release/app-release-unsigned.apk');
const apkKb = process.env.APK_KB ? +process.env.APK_KB
    : existsSync(apk) ? Math.ceil(statSync(apk).size / 1024 / 10) * 10 : null;
if (!apkKb) throw new Error('No release APK found: run ./gradlew assembleRelease first, or set APK_KB=270');

// Material icons (Apache 2.0, google/material-design-icons, 24 × 24): balance, local_offer (a tag), download.
const MI = {
    balance: 'M13,7.83c0.85-0.3,1.53-0.98,1.83-1.83H18l-3,7c0,1.66,1.57,3,3.5,3s3.5-1.34,3.5-3l-3-7h2V4h-6.17 C14.42,2.83,13.31,2,12,2S9.58,2.83,9.17,4L3,4v2h2l-3,7c0,1.66,1.57,3,3.5,3S9,14.66,9,13L6,6h3.17c0.3,0.85,0.98,1.53,1.83,1.83 V19H2v2h20v-2h-9V7.83z M20.37,13h-3.74l1.87-4.36L20.37,13z M7.37,13H3.63L5.5,8.64L7.37,13z M12,6c-0.55,0-1-0.45-1-1 c0-0.55,0.45-1,1-1s1,0.45,1,1C13,5.55,12.55,6,12,6z',
    tag: 'm21.41 11.58-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z',
    download: 'M5,20h14v-2H5V20z M19,9h-4V3H9v6H5l7,7L19,9z',
};

// The badges. Links live in README.md (an SVG shown as an image can't hold a link).
const PILLS = [
    { file: 'get-github', small: 'Get it on', big: 'GitHub', icon: siGithub.path },
    { file: 'get-fdroid', small: 'Get it on', big: 'F-Droid', icon: siFdroid.path },
    { file: 'get-obtainium', small: 'Get it on', big: 'Obtainium', icon: siObtainium.path },
];
const CHIPS = [
    { file: 'chip-android', label: 'Android', value: ANDROID[minSdk] + '+', icon: siAndroid.path },
    { file: 'chip-release', label: 'Release', value: 'v' + versionName, icon: MI.tag },
    { file: 'chip-apk', label: 'APK', value: '~' + apkKb + ' KB', icon: MI.download },
    { file: 'chip-license', label: 'License', value: 'GPL-3.0', icon: MI.balance },
];

// ---------------------------------------------------------------- colours
// Each part = [tone, chroma] on BRAND's hue. Tone: 0 = black … 100 = white. Chroma: how colourful (0 = grey; a
// value higher than BRAND's hue allows at that tone is clipped to the most colourful one possible). These pairs are
// Flow's own (measured on their badges with Material's HCT colour space), so only the hue is ours.
const TONES = {
    light: {
        pill: [92, 10], cookie: [49, 108], cookieIcon: [99, 3], small: [30, 18], big: [10, 14],
        labelBg: [39, 93], label: [100, 0], valueBg: [90, 13], value: [10, 14],
    },
    dark: {
        pill: [17, 14], cookie: [60, 82], cookieIcon: [7, 44], small: [80, 18], big: [90, 13],
        labelBg: [80, 29], label: [20, 65], valueBg: [22, 14], value: [90, 13],
    },
};
// Fixed colours that win over TONES, e.g. { light: { cookie: '#019971' } }. Empty = everything from BRAND's hue.
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

// ---------------------------------------------------------------- text as outlines
// Google Sans (OFL, the font Material 3 / Flow's badges use), weights 500 and 700, as WOFF (opentype.js 1.x reads it).
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

// ---------------------------------------------------------------- shapes
/**
 * Material 3's 9-lobed "cookie": a circle whose radius waves 9 times, r(θ) = R + A·cos(9θ), one lobe pointing right.
 * Fitted to Flow's badge: centre (30, 30), R = 20.48, A = 1.57 (the formula matches their outline within 0.17 units).
 * Drawn as a smooth closed curve (Catmull-Rom through 90 points, written as cubic Béziers).
 */
function cookie(cx, cy, R, A, fill) {
    const n = 90, pts = [];
    for (let i = 0; i < n; i++) {
        const t = (i / n) * 2 * Math.PI, r = R + A * Math.cos(9 * t);
        pts.push([cx + r * Math.cos(t), cy + r * Math.sin(t)]);
    }
    const p = (i) => pts[(i + n) % n];
    let d = `M${r2(p(0)[0])} ${r2(p(0)[1])}`;
    for (let i = 0; i < n; i++) {
        const [p0, p1, p2, p3] = [p(i - 1), p(i), p(i + 1), p(i + 2)];
        d += `C${r2(p1[0] + (p2[0] - p0[0]) / 6)} ${r2(p1[1] + (p2[1] - p0[1]) / 6)} `
            + `${r2(p2[0] - (p3[0] - p1[0]) / 6)} ${r2(p2[1] - (p3[1] - p1[1]) / 6)} ${r2(p2[0])} ${r2(p2[1])}`;
    }
    return `<path fill="${fill}" d="${d}Z"/>`;
}
const r2 = (n) => Math.round(n * 100) / 100;
/** Rectangle with separate left/right corner radii (a joined-group segment). */
function segment(x, w, h, left, right, fill) {
    return `<path fill="${fill}" d="M${r1(x + left)} 0H${r1(x + w - right)}A${right} ${right} 0 0 1 ${r1(x + w)} ${right}`
        + `V${h - right}A${right} ${right} 0 0 1 ${r1(x + w - right)} ${h}H${r1(x + left)}A${left} ${left} 0 0 1 ${r1(x)} ${h - left}`
        + `V${left}A${left} ${left} 0 0 1 ${r1(x + left)} 0Z"/>`;
}
const svg = (w, h, label, body) =>
    `<svg xmlns="http://www.w3.org/2000/svg" width="${r1(w)}" height="${h}" viewBox="0 0 ${r1(w)} ${h}" role="img" `
    + `aria-label="${label}"><title>${label}</title>${body}</svg>\n`;

// ---------------------------------------------------------------- badges
// Every number below is measured on Flow's badges (get-github, split/android; Chromium getBBox), in SVG units.
const PILL_SMALL = sizeForHeight(MEDIUM, 'Get it on', 9.8);   // "Get it on": ink 9.8 tall, baseline 25.7
const PILL_BIG = sizeForHeight(BOLD, 'GitHub', 16.6);         // store name: ink 16.6 tall, baseline 46.4
const CHIP_TEXT = sizeForHeight(MEDIUM, 'Android', 10.6);     // chip texts: baseline 21.2

/** "Get it on …": 60 tall, round ends; cookie at (30, 30); 22-unit logo at (19, 19); text from x 64.7; 26.7 after. */
function pill(p, c) {
    const h = 60, textX = 64.7;
    const textW = Math.max(width(MEDIUM, p.small, PILL_SMALL), width(BOLD, p.big, PILL_BIG));
    const w = textX + textW + 26.7;
    return svg(w, h, p.small + ' ' + p.big,
        `<rect width="${r1(w)}" height="${h}" rx="${h / 2}" fill="${c.pill}"/>`
        + cookie(30, 30, 20.48, 1.57, c.cookie)
        + icon(p.icon, 19, 19, 22, c.cookieIcon)
        + text(MEDIUM, p.small, PILL_SMALL, textX, 25.7, c.small)
        + text(BOLD, p.big, PILL_BIG, textX, 46.4, c.big));
}

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
const written = [];
for (const [mode, c] of Object.entries(MODES)) {
    for (const p of PILLS) {
        writeFileSync(path.join(OUT, `${p.file}-${mode}.svg`), pill(p, c));
        written.push(`${p.file}-${mode}.svg`);
    }
    for (const ch of CHIPS) {
        writeFileSync(path.join(OUT, `${ch.file}-${mode}.svg`), chip(ch, c));
        written.push(`${ch.file}-${mode}.svg`);
    }
}
console.log('colours', JSON.stringify(MODES));
console.log('values', { versionName, minSdk, android: ANDROID[minSdk], apkKb });
console.log('wrote', written.length, 'files to artwork/readme/');
