# PRD — AioWeb Android (Kotlin)

## Original problem statement
> "Can you rewrite my app to be kotlin and installs by apk https://github.com/owenconnorz/AioWeb"
> Follow-up: "build this into another new repo for me and keep this one as a web one"

## Architecture decision
The web AioWeb (Next.js / TypeScript / 50+ components, Supabase, Replicate, Gradio,
youtubei.js, dashjs/hls.js, WebTorrent) is too large/integration-heavy for a same-session
native Kotlin rewrite. The pragmatic shippable answer is a **Kotlin native shell** that:
- Loads the deployed AioWeb URL inside a hardened Android `WebView`
- Adds native Android features the web/PWA cannot deliver: file picker bridge,
  fullscreen video, native DownloadManager, runtime permissions, splash, immersive mode,
  third-party cookies, JS popups, hardware acceleration, back-stack
- Is delivered as a separate repo that can be pushed to GitHub independently

## Project location
`/app/AioWebAndroid/` (separate from the existing web repo)

## Tech stack
- Kotlin 1.9.24 / Android Gradle Plugin 8.5.2 / Gradle 8.7 / JDK 17
- AndroidX (appcompat, core-ktx, webkit, swiperefreshlayout, material3)
- minSdk 24 (Android 7+) · targetSdk 34 (Android 14)

## What's been implemented (Apr 2026)
- Full Kotlin Android Studio project structure
- `MainActivity` with WebView host: fullscreen video, file uploads, downloads,
  permissions, swipe-to-refresh, back-stack handling, external URL routing
- `AioWebApplication` with WebView multi-process safety
- Manifest with all needed runtime permissions (camera, mic, media, notifications)
- Themed launcher icon (vector drawable, brand colors), splash theme
- `BuildConfig.APP_URL` driven — single line to repoint to a different deployment
- GitHub Actions workflow `.github/workflows/build-apk.yml`:
  - Builds debug APK on every push (Ubuntu x86_64 runner)
  - Uploads APK as workflow artifact
  - Auto-creates GitHub Release with APK attached on `main` pushes
- Comprehensive README with build/install/sideload instructions

## How user gets the APK
1. Push `AioWebAndroid/` contents to a new GitHub repo
2. Actions auto-builds → download APK from "Actions → Artifacts" or "Releases"
3. Sideload onto Android device

## Local build note
Cannot build APK inside this preview container (arm64; Google's `aapt2` is x86_64-only).
GitHub Actions x86_64 runner builds successfully. Verified locally:
- Gradle wrapper resolves project (`./gradlew tasks` succeeds)
- All Android variants/source-sets recognized

## Backlog / P1 deferred
- Native Kotlin rewrite of selected hot paths (e.g., dedicated download queue UI,
  native player using ExoPlayer for HLS/DASH instead of HTML5 video)
- Push notifications (FCM)
- Biometric lock for Parental PIN feature
- Signed release APK + Play Store listing
- App update checker (call GitHub Releases API on launch)

## Next actions
- User pushes `/app/AioWebAndroid/` to a new GitHub repo (use Save to Github feature)
- First CI run produces installable debug APK
