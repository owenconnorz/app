# AioWeb Android (Kotlin)

Native Android (Kotlin) companion app for [AioWeb](https://github.com/owenconnorz/AioWeb) — your all-in-one platform for Movies, Music, AI, Pictures, and Downloads.

This is a separate repository from the web app. The web project (`AioWeb`) continues to live as the deployed web/PWA experience; this repo packages it as a sideloadable Android APK with native enhancements.

## Architecture

A native Kotlin Android shell that loads the deployed AioWeb web app inside a hardened, full-featured `WebView` and adds true Android capabilities:

| Native feature | Implementation |
|---|---|
| Hardware-accelerated WebView | `MainActivity` with multi-window support, JS, DOM storage, third-party cookies |
| File uploads (image picker, AI face-swap, etc.) | `WebChromeClient.onShowFileChooser` → ActivityResult API |
| Native fullscreen video / immersive mode | `onShowCustomView` / `onHideCustomView` |
| Native downloads (movies, AI images, music) | `DownloadManager` → public `Downloads/` with notification |
| Camera & mic (AI face-swap, voice prompts) | Runtime permissions + `PermissionRequest` grant in WebView |
| Pull-to-refresh | `SwipeRefreshLayout` |
| Back-stack navigation | `OnBackPressedDispatcher` + WebView history |
| Splash screen | `Theme.AioWeb.Splash` with brand-colored layered drawable |
| External links open in browser | URL filtering in `shouldOverrideUrlLoading` |
| Persistent sessions | Cookie persistence across launches |
| Modern OS support | minSdk 24 (Android 7+), targetSdk 34 (Android 14) |

## Tech stack

- Kotlin 1.9.24
- Android Gradle Plugin 8.5.2
- Gradle 8.7 / JDK 17
- AndroidX (`appcompat`, `core-ktx`, `webkit`, `swiperefreshlayout`, `material3`)
- minSdk 24 · targetSdk 34 · compileSdk 34

## How the APK is built

You don't need Android Studio installed. Every push to `main` triggers GitHub Actions which:

1. Spins up an Ubuntu x86_64 runner
2. Installs JDK 17 + Android SDK
3. Runs `./gradlew assembleDebug`
4. Uploads `AioWeb-debug-<build>.apk` as a workflow artifact
5. Creates a GitHub Release tagged `build-<n>` with the APK attached

### Get the APK

After pushing this repo to GitHub:

1. Go to **Actions** tab → latest "Build Debug APK" run → scroll to **Artifacts** → download `AioWeb-debug-apk`.
2. Or check the **Releases** page for the latest auto-tagged `build-<n>` release.
3. Transfer the `.apk` to your Android device, allow "Install unknown apps" for your file manager / browser, and tap to install.

### Build locally (requires x86_64 host or Android Studio)

```bash
git clone <this-repo>
cd AioWebAndroid
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

> ⚠️ Note: building on an arm64 Linux container without Android Studio is non-trivial because Google's official `aapt2` is x86_64-only. Use Android Studio, an x86_64 Linux/macOS host, or just rely on the GitHub Actions pipeline.

## Configuration

The web URL the app loads is set in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "APP_URL", "\"https://v0-ai-image-and-text-generator.vercel.app\"")
```

To point at a different deployment (e.g., your own Vercel/Netlify URL), edit that one line and rebuild.

## Project structure

```
AioWebAndroid/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/aioweb/app/
│       │   ├── AioWebApplication.kt    # WebView multi-process safety
│       │   └── MainActivity.kt         # WebView host + downloads + fullscreen
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/{strings,colors,themes}.xml
│           └── drawable/
├── build.gradle.kts                    # root
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
├── gradlew, gradlew.bat
└── .github/workflows/build-apk.yml     # CI APK builder
```

## What this app is NOT

This is intentionally a **WebView-shell architecture**, not a full native rewrite of AioWeb's TypeScript codebase. A native rewrite of every feature (Movies plugin engine, YouTube Music via `youtubei.js`, Replicate image gen, Promptchan, Gradio AI, P2P torrent streaming via WebTorrent, Supabase auth, dashjs/hls.js players, etc.) would be a multi-month effort and would require re-implementing every integration in Kotlin equivalents.

The shell approach gives you:
- Instant feature parity with the web app
- One source of truth (every web update ships to Android automatically)
- Native install + launcher icon + offline cache + push-style installs
- Native downloads, file picker, fullscreen video, camera/mic — none of which the PWA can do reliably

## License

Same license as the parent AioWeb project.
