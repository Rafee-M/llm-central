// Development-time tool: draws the GENERATED icon files the way launchers show them, into one picture
// (tools/icon/preview.png), so a new icon can be checked before it reaches a phone. Not part of the app.
// Run after the generator: cd tools/icon && npm run preview   (the "App icon" workflow runs it too)
//
// What it reads (made by generate.mjs; the preview never looks at artwork/icon.svg, so it shows what Android gets):
//   res/drawable/ic_launcher_background.xml (flat or a linear gradient; older outputs: res/values/…, a colour),
//   res/drawable/ic_launcher_foreground.xml (fillAlpha = a translucent part, e.g. a shadow),
//   res/drawable/ic_launcher_monochrome.xml, res/mipmap-mdpi/ic_launcher.png, fastlane …/images/icon.png
//
// How Android draws an adaptive icon (AOSP AdaptiveIconDrawable: MASK_SIZE 100, EXTRA_INSET_PERCENTAGE 1/4,
// SAFEZONE_SCALE 66/72): the launcher's shape is a path in a 100 x 100 box covering the icon; the 108 dp layers
// are drawn 1.5 x that box, centred, so only their middle 72 dp is inside it.
// Launcher shapes below are AOSP's own: the default config_icon_mask (core/res config.xml, main) and the
// IconShape{Squircle,Teardrop,RoundedRect}Overlay masks (frameworks/base android10-release), plus a circle (Pixel).
// Themed icons (Android 13+): Launcher3 ThemedIconDrawable.getColors tints the monochrome layer with one colour:
// light = icon system_neutral2_700 on system_accent1_100, dark = icon system_accent1_100 on system_neutral1_800.
// Real phones take those colours from the wallpaper; the preview uses AOSP's default values (colors.xml, android13).

import { chromium } from 'playwright';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..');
const RES = join(ROOT, 'app', 'src', 'main', 'res');
const OUT = join(HERE, 'preview.png');

const SHAPES = [
    ['Circle (Pixel)', 'M50,0A50,50 0 1 1 50,100A50,50 0 1 1 50,0Z'],
    ['AOSP default', 'M50,0L92,0C96.42,0 100,4.58 100 8L100,92C100, 96.42 96.42 100 92 100L8 100C4.58, 100 0 96.42 0 92L0 8 C 0 4.42 4.42 0 8 0L50 0Z'],
    ['Squircle', 'M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z'],
    ['Rounded square', 'M50,0L88,0 C94.4,0 100,5.4 100 12 L100,88 C100,94.6 94.6 100 88 100 L12,100 C5.4,100 0,94.6 0,88 L0 12 C0 5.4 5.4 0 12 0 L50,0 Z'],
    ['Teardrop', 'M50,0 C77.6,0 100,22.4 100,50 L100,88 C100,94.6 94.6,100 88,100 L50,100 C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0 Z'],
    ['Square', 'M0,0H100V100H0Z'],
];
const THEMED = { light: { bg: '#C1E8FF', fg: '#40484D' }, dark: { bg: '#2E3133', fg: '#C1E8FF' } };

// ---------- read the generated vector drawables (they hold only flat <path> elements) ----------

function attrs(text) {
    return Object.fromEntries([...text.matchAll(/android:(\w+)="([^"]*)"/g)].map(m => [m[1], m[2]]));
}

function readVector(file) {
    const xml = readFileSync(file, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
    const tags = [...xml.matchAll(/<([a-z-]+)[\s>]/g)].map(m => m[1]).filter(t => t !== 'vector' && t !== 'path');
    if (tags.length) throw new Error(`${file}: preview.mjs reads only <path> elements, found <${tags[0]}>`);
    const v = attrs(xml.match(/<vector[^>]*>/)[0]);
    if (v.viewportWidth !== '108' || v.viewportHeight !== '108') throw new Error(`${file}: expected a 108 x 108 viewport`);
    return [...xml.matchAll(/<path([^>]*)\/>/g)].map(m => attrs(m[1]));
}

// One drawable path as SVG. tint = one colour for everything (themed icon), keeping each path's alpha shape.
function svgPath(p, tint) {
    const fill = p.fillColor ? (tint || argbToCss(p.fillColor)) : 'none';
    const stroke = p.strokeColor ? (tint || argbToCss(p.strokeColor)) : 'none';
    return `<path d="${p.pathData}" fill="${fill}" fill-opacity="${p.fillAlpha || 1}" fill-rule="${p.fillType === 'evenOdd' ? 'evenodd' : 'nonzero'}"`
        + ` stroke="${stroke}" stroke-width="${p.strokeWidth || 0}" stroke-linejoin="${(p.strokeLineJoin || 'miter')}"`
        + ` stroke-linecap="${p.strokeLineCap || 'butt'}"/>`;
}

// Android colours are #RGB, #ARGB, #RRGGBB or #AARRGGBB; CSS wants the alpha last.
function argbToCss(c) {
    const h = c.slice(1);
    if (h.length === 8) return `#${h.slice(2)}${h.slice(0, 2)}`;
    if (h.length === 4) return `#${h.slice(1)}${h[0]}`;
    return c;
}

// The background layer: drawable/ic_launcher_background.xml (one path over the whole 108 dp layer, a flat colour or
// an aapt linear gradient), or, from generators before 2026-10-22, only the colour in values/. A gradient becomes
// one SVG <linearGradient> in the coordinates icon() draws the background in (the 100-unit mask box, layer at 1.5 x).
let background, backgroundDefs = '';
const bgDrawable = join(RES, 'drawable', 'ic_launcher_background.xml');
if (existsSync(bgDrawable)) {
    const xml = readFileSync(bgDrawable, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
    const g = /<gradient([^>]*)>([\s\S]*?)<\/gradient>/.exec(xml);
    if (g) {
        const a = attrs(g[1]), toBox = v => -25 + Number(v) * 150 / 108;
        if (a.type !== 'linear') throw new Error(`${bgDrawable}: preview.mjs draws only linear gradients`);
        const stops = [...g[2].matchAll(/<item([^>]*)\/>/g)].map(m => attrs(m[1]))
            .map(st => `<stop offset="${st.offset}" stop-color="${argbToCss(st.color)}"/>`).join('');
        backgroundDefs = `<svg width="0" height="0" style="position:absolute"><defs><linearGradient id="bg" gradientUnits="userSpaceOnUse"`
            + ` x1="${toBox(a.startX)}" y1="${toBox(a.startY)}" x2="${toBox(a.endX)}" y2="${toBox(a.endY)}">${stops}</linearGradient></defs></svg>`;
        background = 'url(#bg)';
    } else {
        background = argbToCss(attrs(xml.match(/<path[^>]*>/)[0]).fillColor);
    }
} else {
    background = argbToCss(readFileSync(join(RES, 'values', 'ic_launcher_background.xml'), 'utf8')
        .match(/<color name="ic_launcher_background"[^>]*>([^<]+)</)[1].trim());
}
const foreground = readVector(join(RES, 'drawable', 'ic_launcher_foreground.xml'));
const monochrome = readVector(join(RES, 'drawable', 'ic_launcher_monochrome.xml'));
const pngData = f => 'data:image/png;base64,' + readFileSync(f).toString('base64');

// ---------- one launcher icon: mask over the 100-unit box, layers 1.5 x larger and centred ----------

let ids = 0;
function icon(px, shape, layers, bg, guides = false) {
    const id = `m${ids++}`;
    const layer = `<g transform="translate(-25 -25) scale(${150 / 108})">${layers}</g>`;
    const guide = guides
        ? `<path d="${shape}" fill="none" stroke="#E53935" stroke-width="0.8"/>`
          + `<circle cx="50" cy="50" r="${50 * 66 / 72}" fill="none" stroke="#FFC107" stroke-width="0.8" stroke-dasharray="2 1.5"/>`
        : '';
    // With guides, the whole 108 dp layer is drawn faintly so you can see what every launcher cuts away.
    const outside = guides ? `<g opacity="0.25"><rect x="-25" y="-25" width="150" height="150" fill="${bg}"/>${layer}</g>` : '';
    return `<svg width="${px}" height="${px}" viewBox="${guides ? '-25 -25 150 150' : '0 0 100 100'}" style="overflow:visible">
        <defs><clipPath id="${id}"><path d="${shape}"/></clipPath></defs>${outside}
        <g clip-path="url(#${id})"><rect x="-25" y="-25" width="150" height="150" fill="${bg}"/>${layer}</g>${guide}</svg>`;
}

const fg = foreground.map(p => svgPath(p)).join('');
const tile = (label, inner, dark = false) =>
    `<figure class="${dark ? 'dark' : ''}">${inner}<figcaption>${label}</figcaption></figure>`;

const html = `<!doctype html><meta charset="utf-8"><style>
  body { margin: 0; padding: 20px 24px; font: 14px/1.4 system-ui, sans-serif; color: #1b1b1f; background: #fff; width: 940px; }
  h2 { font-size: 15px; margin: 18px 0 8px; } p { margin: 0 0 8px; color: #555; font-size: 12px; }
  .row { display: flex; flex-wrap: wrap; gap: 14px; align-items: flex-end; }
  figure { margin: 0; padding: 12px; border-radius: 12px; background: #eef1f4; text-align: center; }
  figure.dark { background: #121316; color: #e3e2e6; }
  figcaption { font-size: 11px; margin-top: 6px; }
</style>
${backgroundDefs}
<h2>Adaptive icon (Android 8+) in launcher shapes</h2>
<div class="row">${SHAPES.map(([n, d]) => tile(n, icon(112, d, fg, background))).join('')}</div>
<h2>Themed icon (Android 13+, monochrome layer)</h2>
<p>Sample colours (AOSP defaults); a real phone takes them from the wallpaper. Every part is one colour: only the shapes count.</p>
<div class="row">
  ${tile('Light, circle', icon(112, SHAPES[0][1], monochrome.map(p => svgPath(p, THEMED.light.fg)).join(''), THEMED.light.bg))}
  ${tile('Light, squircle', icon(112, SHAPES[2][1], monochrome.map(p => svgPath(p, THEMED.light.fg)).join(''), THEMED.light.bg))}
  ${tile('Dark, circle', icon(112, SHAPES[0][1], monochrome.map(p => svgPath(p, THEMED.dark.fg)).join(''), THEMED.dark.bg), true)}
  ${tile('Dark, squircle', icon(112, SHAPES[2][1], monochrome.map(p => svgPath(p, THEMED.dark.fg)).join(''), THEMED.dark.bg), true)}
</div>
<h2>Fit check</h2>
<p>Faint = parts of the 108 dp layer no launcher shows. Red = the shape. Yellow dashes = the 66 dp safe zone: key content belongs inside it.</p>
<div class="row">
  ${tile('Circle + guides', icon(150, SHAPES[0][1], fg, background, true))}
  ${tile('Squircle + guides', icon(150, SHAPES[2][1], fg, background, true))}
  ${tile('Themed + guides', icon(150, SHAPES[0][1], monochrome.map(p => svgPath(p, THEMED.light.fg)).join(''), THEMED.light.bg, true))}
</div>
<h2>Small sizes and the other files</h2>
<div class="row">
  ${tile('48 px (home screen, mdpi)', icon(48, SHAPES[0][1], fg, background))}
  ${tile('24 px (lists, notifications)', icon(24, SHAPES[0][1], fg, background))}
  ${tile('Android 7 PNG (mipmap-mdpi)', `<img src="${pngData(join(RES, 'mipmap-mdpi', 'ic_launcher.png'))}" width="48" height="48">`)}
  ${tile('F-Droid store icon (512 px)', `<img src="${pngData(join(ROOT, 'fastlane', 'metadata', 'android', 'en-US', 'images', 'icon.png'))}" width="112" height="112">`)}
  ${tile('On a dark home screen', icon(64, SHAPES[2][1], fg, background), true)}
</div>`;

async function main() {
    const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
    try {
        const page = await browser.newPage({ viewport: { width: 988, height: 400 }, deviceScaleFactor: 2 });
        const errors = [];
        page.on('pageerror', e => errors.push(e.message));
        await page.setContent(html);
        if (errors.length) throw new Error('preview page error: ' + errors[0]);
        await page.screenshot({ path: OUT, fullPage: true });
        console.log(`preview written: ${OUT} (${foreground.length} foreground + ${monochrome.length} monochrome paths, background ${background === 'url(#bg)' ? 'gradient' : background})`);
    } finally {
        await browser.close();
    }
}

main().catch(e => { console.error(e.message); process.exit(1); });
