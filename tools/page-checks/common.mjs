// Shared helpers for the page checks (tools/page-checks). Development-time only: nothing here is part of the app.
//
// The app's HTML pages (settings.html, chats_viewer.html…) and injected scripts (assets/js/*.js) normally run inside
// Android's WebView and talk to Java through "bridges" (window.AndroidSettings, window.AndroidChatsViewer…). These
// checks open them in desktop Chromium instead, with small JavaScript stand-ins for those bridges, and look at what the
// page shows and what it sends to the app. Chromium and WebView share the same engine, so this is a good check of the
// page's own logic; it is NOT a check of the Android side (that needs the phone).
import { chromium } from 'playwright';
import { fileURLToPath, pathToFileURL } from 'url';
import path from 'path';

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const ASSETS = path.resolve(HERE, '../../app/src/main/assets');

/** file:// address of a page in app/src/main/assets (e.g. pageUrl('settings.html')). */
export function pageUrl(name) {
    return pathToFileURL(path.join(ASSETS, name)).href;
}

/** Path of a file in app/src/main/assets (e.g. assetPath('js/auto_focus.js')). */
export function assetPath(name) {
    return path.join(ASSETS, name);
}

/** Starts Chromium: the one in CHROMIUM_PATH if set (Claude's sandbox), else Playwright's own download. */
export function launch() {
    return chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
}
