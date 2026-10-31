# Building LLM Central

This guide takes you from nothing to a running build, with Android Studio or the command line. The app is plain Java
on the Android framework with a single library (`androidx.webkit`), so there is not much to install.

**Contents:** [Requirements](#requirements) · [Get the source](#get-the-source) ·
[Android Studio](#option-a-android-studio) · [Command line](#option-b-command-line) · [Other editors](#other-editors) ·
[Before you send a change](#before-you-send-a-change) · [Where things are](#where-things-are) ·
[Development tools](#development-tools) · [Release builds and signing](#release-builds-and-signing) ·
[Troubleshooting](#troubleshooting)

## Requirements

| What | Version | Notes |
|---|---|---|
| JDK | **17 or newer** (17 and 21 tested) | Android Studio's bundled JDK works. |
| Android SDK Platform | **36** (`platforms;android-36`) | `compileSdk = 36`; the app targets 35 and runs on Android 7.0 (API 24) and newer. |
| Android SDK Build-Tools | **35.0.0** (`build-tools;35.0.0`) | The version the Android Gradle Plugin 8.13 uses by default. |
| Gradle | 8.14.5, **downloaded by the wrapper** | Don't install Gradle yourself; always run `./gradlew`. The download is checked against a pinned SHA-256. |
| Android Gradle Plugin | 8.13.2 | Downloaded automatically. Needs JDK 17+ and Gradle 8.13+. |
| Git | any recent | See [Get the source](#get-the-source). |
| Android Studio *(optional)* | **Narwhal 3 Feature Drop (2025.1.3) or newer** | The oldest release that supports AGP 8.13; current releases work too. |
| Node.js *(optional)* | 18 or newer | Only for the [development tools](#development-tools), never for the app itself. |

Disk: about 0.5 GB for the SDK packages plus about 0.7 GB of Gradle caches (measured on a fresh set-up). No NDK, Kotlin or Google Play Services needed.

## Get the source

```bash
git clone <this repository's address> llmcentral
cd llmcentral
```

Use `git clone` rather than the ZIP download: release builds record the Git commit they were built from, which is
how a published APK can be rebuilt byte for byte.

## Option A: Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (Narwhal 3 Feature Drop or newer).
2. **File → Open…** and pick the cloned folder (the one with `settings.gradle`). Studio runs a Gradle sync; the first
   one downloads Gradle and the plugin and takes a few minutes.
3. If the sync asks for missing SDK parts: **Tools → SDK Manager** → *SDK Platforms*: **Android 16 (API 36)**;
   *SDK Tools* (tick *Show Package Details*): **Android SDK Build-Tools 35.0.0**. Then **File → Sync Project with
   Gradle Files**.
4. Check the JDK: **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** = 17 or newer (the
   bundled *jbr* is fine).
5. Pick the **app** run configuration and a phone (USB debugging on) or an emulator, then **Run ▶**. The debug build
   installs as **"LLM Central (Debug)"** (`org.llmcentral.app.debug`), next to a released copy, never over it.

> **If Studio offers to upgrade the Android Gradle Plugin or Gradle, decline.** The versions are pinned on purpose:
> F-Droid rebuilds the app from source and compares it byte for byte with the published APK, so a version change has
> to be made (and re-checked) deliberately, not by an assistant.

Tests: right-click `app/src/test/java` → **Run 'All Tests'**, or use the Gradle commands below in Studio's terminal.

## Option B: Command line

1. **JDK 17+**, e.g. [Eclipse Temurin](https://adoptium.net/) (`java -version` should say 17 or newer).
2. **Android SDK** without Studio: download the *Command line tools only* package from the bottom of the
   [Android Studio page](https://developer.android.com/studio#command-line-tools-only), unpack it to
   `<sdk>/cmdline-tools/latest/`, then:
   ```bash
   <sdk>/cmdline-tools/latest/bin/sdkmanager --licenses
   <sdk>/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools"
   ```
3. Tell Gradle where the SDK is: either `export ANDROID_HOME=<sdk>` or a file `local.properties` in the project folder
   with `sdk.dir=<sdk>` (this file is git-ignored; Android Studio writes it for you).
4. Build:

| Command | What it does | Output |
|---|---|---|
| `./gradlew assembleDebug` | Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew installDebug` | Builds and installs it on the connected phone (`adb`) | — |
| `./gradlew test` | Unit tests (JUnit 4, run on your computer, no phone needed) | `app/build/reports/tests/testDebugUnitTest/` |
| `./gradlew lint` | Android lint | `app/build/reports/lint-results-debug.html` |
| `./gradlew assembleRelease` | Release APK, shrunk by R8, **unsigned** | `app/build/outputs/apk/release/app-release-unsigned.apk` |

On Windows use `gradlew.bat` instead of `./gradlew`.

## Other editors

- **IntelliJ IDEA** (with the Android plugin) opens the project like Android Studio does, with the same requirements.
- **VS Code** or any other editor: edit freely and build from the terminal with the commands above. For Java
  completion in VS Code, the *Extension Pack for Java* reads the Gradle project (Android classes resolve once the SDK
  is set up).

The code is Java 11 (no Kotlin). The settings screen and other in-app pages are HTML in `app/src/main/assets/`, so
any editor with HTML/CSS/JavaScript support works for them.

## Before you send a change

Run exactly what the CI does on every push and pull request:

```bash
./gradlew test lint assembleDebug assembleRelease
```

It must pass with **0 lint errors** (lint warnings are allowed but shouldn't grow). If you changed an HTML page in
`app/src/main/assets/` or a script in `assets/js/`, also run the [page checks](#development-tools).

## Where things are

| Path | What |
|---|---|
| `app/src/main/java/org/llmcentral/app/` | The app (Java): `MainActivity`, then packages `account/`, `provider/`, `web/`, `ui/`, `theme/`, `storage/`, `downloads/`, `scripts/` |
| `app/src/main/assets/` | Local pages (`settings.html`, `about.html`, …) and the scripts injected into chat sites (`js/`) |
| `app/src/main/res/` | Layouts, icons (vector drawables), strings |
| `app/src/test/` | Unit tests |
| `app/src/debug/` | Debug-only extras (a request log screen); never part of a release |
| `fastlane/metadata/` | Store texts, screenshots and changelogs (used by F-Droid) |
| `artwork/` | Logo and banner sources; `artwork/readme/` = the README badges |
| `tools/` | Development tools (below) |
| `.github/workflows/` | CI (`main.yml`), releases (`release.yml`), app icon (`icon.yml`) |

## Development tools

Optional helpers in `tools/`, each with its own `package.json` (Node.js 18+). None of them is part of the app.

| Tool | What it does | Run |
|---|---|---|
| `tools/page-checks/` | Opens the app's HTML pages and injected scripts in Chromium with stand-in Android bridges and checks them | `npm install && npm run check` (downloads Playwright's Chromium once with `npx playwright install chromium`) |
| `tools/icon/` | Makes every launcher icon file from `artwork/icon.svg`; never edit the generated icons by hand | `npm install && npm run generate` |
| `tools/palettes/` | Rebuilds `assets/palettes.json` (the colour palettes) with Material Color Utilities | `npm install && npm run generate` |
| `tools/readme-badges/` | Rebuilds the README badges (`artwork/readme/`) in the app's colours; run after a version change | `npm install && npm run generate` (needs a release build first, for the APK size) |
| `tools/provider-probe/` | Opens a live chat site like an app tab would and reports its cost and hosts (for adding a provider) | `node probe.mjs <url>` |

## Release builds and signing

`assembleRelease` makes an **unsigned** APK. Published releases are signed afterwards by the release workflow with
`apksigner` from Build-Tools 34, because F-Droid checks published APKs by copying their signature onto its own build,
and Gradle-signed APKs fail that check.

To install your own release build on a phone, sign it yourself: add these lines to `local.properties` (never commit
them) and run `assembleRelease` again:

```properties
signing.storeFile=/path/to/your.keystore
signing.storePassword=…
signing.keyAlias=…
signing.keyPassword=…
```

Debug builds are signed with the committed `app/debug.keystore` (password `android`), so a debug APK from any machine
installs over another one without uninstalling. It is not a secret.

## Troubleshooting

| Problem | Fix |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME`, or put `sdk.dir=<sdk>` in `local.properties`. |
| `Unsupported class file major version`, or "requires Java 17" | Gradle runs on an older JDK: install 17+, and in Studio check the *Gradle JDK* setting. |
| `Failed to find target with hash string 'android-36'` / missing Build-Tools | Install `platforms;android-36` and `build-tools;35.0.0` (see the SDK steps above). |
| Studio suggests upgrading AGP or Gradle | Decline (see the note under [Android Studio](#option-a-android-studio)). |
| "App not installed" when installing a debug APK | Another debug build signed with a different key is installed; uninstall *LLM Central (Debug)* first. |
| Only one account per provider in the app | The phone's *Android System WebView* is too old for separate profiles; update it from the app store. |
| Page checks can't find a browser | Run `npx playwright install chromium` in `tools/page-checks/`, or set `CHROMIUM_PATH` to a Chromium binary. |
