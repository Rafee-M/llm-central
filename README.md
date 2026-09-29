# LLM Central

> **Status: early development.** Today the app still works like duckAssist (duck.ai only).
> Multi-provider and multi-account support are being built. See [`PROJECT.md`](PROJECT.md) for the plan
> and [`CHANGES.md`](CHANGES.md) for what has changed.

**LLM Central** is a lightweight, privacy-focused Android app that puts several AI chat websites in one
place: duck.ai, ChatGPT, Claude, DeepSeek and more. It supports **multiple isolated accounts per
provider**, instant switching between them, and Material You theming, while keeping the APK tiny (~220 KB).

It is a fork of [duckAssist](https://github.com/diekaiju/duckAssist) by diekaiju, which is itself a fork of
[gptAssist](https://github.com/woheller69/gptAssist) by woheller69.

## Features available today (inherited from duckAssist)

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

Multiple providers and accounts, a provider/account switcher, universal Material You settings, a default
assistant provider, and per-account offline history.

## Signing in to providers

Google blocks "Sign in with Google" inside in-app web views. Use **email/password or email-code login**
for each provider instead.

## Building

Needs JDK 17+ and the Android SDK (platform 35). Then:

```bash
./gradlew test assembleDebug        # unit tests + debug APK
./gradlew assembleRelease           # release APK (unsigned unless signing is configured in local.properties)
```

## License

GPLv3 (see [LICENSE](LICENSE)). If you distribute the app, you must also offer its source code.

The app uses:
- Parts from GMaps WV (https://gitlab.com/divested-mobile/maps), licensed under the GPLv3.
- Code from duckAssist and gptAssist (GPLv3).
