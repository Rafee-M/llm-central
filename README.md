<img src="artwork/icon.svg" width="96" height="96" alt="LLM Central app icon: a speech bubble with five pixel sprites on green">

# LLM Central

> **Status: early development.** duck.ai, ChatGPT, Claude, DeepSeek and Z.ai work as switchable tabs, with
> several isolated accounts per provider and Material You theming.

**LLM Central** is a lightweight, privacy-focused Android app that puts several AI chat websites in one
place: duck.ai, ChatGPT, Claude, DeepSeek, Z.ai and more, with several isolated accounts per provider and
instant switching between them, while keeping the APK tiny (~250 KB).

It is a fork of [duckAssist](https://github.com/diekaiju/duckAssist) by diekaiju, which is itself a fork of
[gptAssist](https://github.com/woheller69/gptAssist) by woheller69.

## Features available today

- 🔀 **Several AI providers in one app**: duck.ai, ChatGPT, Claude, DeepSeek and Z.ai. Tap the chip in the
  top bar to switch; up to 3 accounts (changeable in Settings, 1–10) stay loaded, so switching back is instant.
- 👥 **Several accounts per provider** (e.g. ChatGPT "Work" and "Personal"), each in its own container with
  separate logins, cookies and site data. Name them, choose which ones appear in the menu, sign out or remove
  them in Settings → Accounts. (Needs a recent Android System WebView.)
- 🎨 **Material You**: Light / Dark / Black (OLED) / System theme, 8 colour palettes, or your wallpaper colours
  (Android 12+), in Settings → Appearance.
- 📌 **Choose where things open**: which account the assistant gesture opens, which one gets **Share** and the
  **"Ask AI"** text action, and which one the app starts with (Settings → Assistant & sharing / Behaviour).
  duck.ai stays the default.
- ✍️ **Prompt pre-fill on every provider**: shared text lands in the chosen chat's message box, ready to edit and send.
- ↕️ **Re-order accounts** (Settings → Accounts → Re-order accounts: drag the handle; any order, across providers).
- 🔑 **Sign-in pop-ups** (e.g. "Continue with Microsoft/Apple/GitHub") open in the right account.
- 🖥️ **Desktop site** toggle in the account menu, remembered per account.
- ⛶ **Hide status bar** (Settings → Appearance) for more room.
- 📥 **Downloads on every provider**, including files the chat creates itself (e.g. a CSV), saved to
  `Downloads/<Provider>/`.
- 🗂️ **Account menu tools**: Desktop site, 🔄 Reload page, Reset text size, **Hide status bar** and Clear cache as one
  icon row; a **✕ on every loaded account** unloads it (frees memory; duck.ai stays loaded); an **Active accounts** switch
  lists only the loaded accounts.
- 🔁 **Tap to retry**: a page that can't be loaded (e.g. no connection) shows a calm black screen instead of Chromium's
  error page; tap it to try again.
- 🛡️ **Block trackers** (on by default; Settings → Data & tools): the chat sites' analytics, telemetry and
  fingerprinting requests get no answer. A small list built from real measurements on each site; sign-in, captchas
  and chats are never blocked.
- 🧹 **Clear cache** (Settings → Data & tools; logins stay).
- ⚙️ **Settings button on every provider** in the top bar; a minimal Material 3 settings screen, with an
  **About** page (version, developer, source code link).
- 🔒 **Safer by design**: only duck.ai's own page can talk to the app, through a checked message bridge; the
  other chat sites get no bridge at all.

### Inherited from duckAssist (duck.ai)

- 🎯 **Focused interface** for [duck.ai](https://duck.ai/), with a native loading progress bar.
- 🎙️ **Digital assistant integration**: set the app as your device's default digital assistant and open
  it with the system assistant gesture; with duck.ai it can start voice chat automatically.
- 🚀 **"Ask AI" text action**: select text in any app → overflow menu → *Ask AI*.
- 📁 **File support**: upload files/photos, download attachments to your Downloads folder.
- 📄 **Share any file**: files shared to the app (documents, PDFs, images…) reach the chat exactly as
  they are, same name and content; tap 📎 on the page to attach them.
- 💬 **Offline chat history** for duck.ai, shown automatically when there's no connection (can be switched off in Settings → Data & tools).
- 🧩 **User scripts**: optional custom scripts per website (off by default, with a security warning).
- 🌐 **External links** open in your normal browser.

## Planned

Per-account offline history, per-account cache sizes, app lock.

## Signing in to providers

Google blocks "Sign in with Google" inside in-app web views. Use **email/password or email-code login**
for each provider instead.

## Building

Needs JDK 17+ and the Android SDK (platform 36). Then:

```bash
./gradlew test assembleDebug        # unit tests + debug APK
./gradlew assembleRelease           # release APK (unsigned)
```

- **What changed in each version:** `fastlane/metadata/android/en-US/changelogs/`, also shown on each GitHub release.

## License

GPLv3 (see [LICENSE](LICENSE)). If you distribute the app, you must also offer its source code.

The app uses:
- Parts from GMaps WV (https://gitlab.com/divested-mobile/maps), licensed under the GPLv3.
- Code from duckAssist and gptAssist (GPLv3).
- Provider logos from Lobe Icons (MIT) and, for duck.ai, Simple Icons (CC0); the logos are trademarks of their
  owners. See
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
