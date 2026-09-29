# LLM Central

> **Status: early development.** duck.ai, ChatGPT, Claude, DeepSeek and Z.ai work as switchable tabs, with
> several isolated accounts per provider and Material You theming. See [`PROJECT.md`](PROJECT.md) for the plan
> and [`CHANGES.md`](CHANGES.md) for what has changed.

**LLM Central** is a lightweight, privacy-focused Android app that puts several AI chat websites in one
place: duck.ai, ChatGPT, Claude, DeepSeek, Z.ai and more, with several isolated accounts per provider and
instant switching between them, while keeping the APK tiny (~260 KB).

It is a fork of [duckAssist](https://github.com/diekaiju/duckAssist) by diekaiju, which is itself a fork of
[gptAssist](https://github.com/woheller69/gptAssist) by woheller69.

## Features available today

- 🔀 **Several AI providers in one app**: duck.ai, ChatGPT, Claude, DeepSeek and Z.ai. Tap the chip in the
  top bar to switch; up to 3 accounts (changeable in Settings, 1–5) stay loaded, so switching back is instant.
- 👥 **Several accounts per provider** (e.g. ChatGPT "Work" and "Personal"), each in its own container with
  separate logins, cookies and site data. Name them, choose which ones appear in the menu, sign out or remove
  them in Settings → Accounts. (Needs a recent Android System WebView.)
- 🎨 **Material You**: Light / Dark / Black (OLED) / System theme, 8 colour palettes, or your wallpaper colours
  (Android 12+), in Settings → Appearance.
- ⚙️ **Settings button on every provider** in the top bar; a minimal Material 3 settings screen.

### Inherited from duckAssist (duck.ai)

- 🎯 **Focused interface** for [duck.ai](https://duck.ai/), with a native loading progress bar.
- 🎙️ **Digital assistant integration**: set the app as your device's default digital assistant and open
  it with the system assistant gesture; it can start duck.ai's voice chat automatically.
- 🚀 **"Ask duck.ai" text action**: select text in any app → overflow menu → *Ask duck.ai*.
- 📁 **File support**: upload files/photos, download attachments to your Downloads folder.
- 📄 **Offline document conversion**: shared Word/Excel/PowerPoint/OpenDocument/ePub/RTF/CSV files are
  converted on the phone into text or PDF; large PDFs are split into 15-page parts. The converter is
  downloaded once and verified with a SHA-256 fingerprint before it runs.
- 💬 **Offline chat history** for duck.ai, shown automatically when there's no connection.
- 🧩 **User scripts**: optional custom scripts per website (off by default, with a security warning).
- 🌐 **External links** open in your normal browser.

## Planned (see PROJECT.md)

A default assistant account, share targets per account, per-account offline history, and account re-ordering.

## Signing in to providers

Google blocks "Sign in with Google" inside in-app web views. Use **email/password or email-code login**
for each provider instead.

## Building

Needs JDK 17+ and the Android SDK (platform 36). Then:

```bash
./gradlew test assembleDebug        # unit tests + debug APK
./gradlew assembleRelease           # release APK (unsigned unless signing is configured in local.properties)
```

## License

GPLv3 (see [LICENSE](LICENSE)). If you distribute the app, you must also offer its source code.

The app uses:
- Parts from GMaps WV (https://gitlab.com/divested-mobile/maps), licensed under the GPLv3.
- Code from duckAssist and gptAssist (GPLv3).
