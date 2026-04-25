# PRD — StreamCloud (Native Android Kotlin)
*(formerly AioWeb)*

## Original problem statement
> "Can you rewrite my app to be kotlin and installs by apk https://github.com/owenconnorz/AioWeb"
> "I dont want this to be an web app I want this to be a kotlin app"
> "Lets make a mediaplayer that movies and porn tab can use also support PTP and anything that the movies need to support also so that the porn videos doesnt need to use embedded website to work"
> "Like cloudstream when switching provider can you get its home feed data from the plugin also when trying to add this repo it doesnt load https://raw.githubusercontent.com/phisher98/CXXX/builds/CXXX.json and can you make my mediaplayer run and look like nuvio app and can you add a in app updater that receives updates from github commits and also rename the entire app to StreamCloud"

## Architecture
**Fully native Kotlin Compose app**. Lives at `/app/AioWebAndroid/`.
Backend lives at `/app/backend/` (FastAPI + emergentintegrations).

```
Android (Kotlin Compose) ──→ TMDB           (movies)
                          ──→ NewPipe/YT     (music)
                          ──→ Eporner API    (adult, direct MP4)
                          ──→ libtorrent4j   (P2P / magnet)
                          ──→ GitHub API     (in-app updater)
                          ──→ AioWeb FastAPI ──→ emergentintegrations (AI)
```

## Tech stack
- Kotlin 1.9.24 / Compose BOM 2024.06.00 / Material 3
- AGP 8.5.2, Gradle 8.7, JDK 17
- Retrofit 2.11 + Kotlinx Serialization
- **Media3 ExoPlayer 1.4.1** (HLS + DASH + MediaSession)
- **libtorrent4j 2.1.0-32** (arm/arm64/x86_64) + NanoHTTPD 2.3.1
- NewPipe Extractor 0.24.2
- Coil 2.7, DataStore 1.1, WorkManager 2.9
- **FileProvider** for sideload-installer hand-off
- minSdk 24, targetSdk 34

## What's implemented (Feb 2026, latest update)
**Backend (`/app/backend/server.py`)** — `/api/ai/chat`, `/api/ai/image`, `/api/movies/trending`.

**Android Native App (StreamCloud)**
- 5-tab Compose shell (Movies / Music / AI / Library or Adult / Settings)
- **Movies**: TMDB trending + plugin source switcher chips (Metrolist redesign) — selecting a plugin shows an honest banner: *"home feed and search results will appear here once the CloudStream runtime ships"*
- **Music**: NewPipe + Media3 + mini player + Metrolist look
- **Adult**: Eporner search → **native Media3 playback (no WebView)** via direct-MP4 resolution from `/api/v2/video/search/?id=` (`bestMp4()` picks 1080p > 720p > 480p…)
- **Plugins screen**: per-plugin install/uninstall, multi-repo, granular HTTP error messages on fetch failure ("HTTP 404 — file not found at that URL"; lists every URL we tried)
- **Settings**: backend URL, AI provider/model, NSFW toggle, video/audio quality, Wi-Fi-only downloads, **Check for updates**
- **In-app updater** — `UpdateChecker` polls GitHub Releases API for `${GITHUB_OWNER}/${GITHUB_REPO}` (auto-injected by CI from `GITHUB_REPOSITORY`). Compares `versionCode` against the build-number embedded in the release tag (`build-N`), downloads the best APK asset (signed > unsigned > debug), then hands off to Android's PackageInstaller via `FileProvider`. UI shows progress bar + "Install" button.
- **Nuvio-style media player** — fullscreen black canvas, auto-hiding controls (3s timer), tap-to-toggle, double-tap regions for ±10s seek, large center play/pause + Replay10/Forward10 circle buttons, gradient scrims, bottom slider with current/total times. Used by both Movies and Adult tabs.
- **Unified player** supports HLS (.m3u8), DASH (.mpd), progressive MP4/MKV/WEBM, and `magnet:`/`.torrent` (libtorrent4j sequential download → NanoHTTPD local proxy → ExoPlayer).
- **Display name renamed to "StreamCloud"** (kept `applicationId=com.aioweb.app` so existing installs can still receive updates).

**CI/CD** — GitHub Actions builds debug + signed-release APKs on every push, publishes them to Releases tagged `build-${run_number}`. The new workflow uses `gradle/actions/setup-gradle@v3` for caching and retry on transient CDN 502s. Release keystore reused for debug builds so updates don't require uninstall.

## Known constraints
- **APK build cannot run inside this Emergent preview container** (arm64 vs Google's x86_64 aapt2). CI on `ubuntu-latest` builds successfully.
- Plugin runtime (`.cs3` execution via DexClassLoader + cloudstream3 API stubs) is **not yet implemented** — it's a multi-day port. Current behaviour: UI lets you install/uninstall plugins and pick them as the source for the home feed, but actual streaming via plugins shows the honest "coming soon" banner. Movies still falls back to TMDB + Vidsrc embed + user-pasted URL/magnet.

## Backlog / next iterations
- **P0** CloudStream `.cs3` execution (DexClassLoader + cloudstream3 API stubs) — turns the plugin manager into actual streaming
- **P1** Subtitle track picker + external SRT/VTT loader in NativePlayerScreen
- **P1** Picture-in-Picture (PiP) + landscape orientation lock for the player
- **P1** Brightness/volume vertical drag gestures (Nuvio-style)
- **P2** Room DB for Library + Downloads (WorkManager)
- **P2** Per-ABI APK splits / AAB
- **P3** Supabase auth, AI face-swap, FCM push

## Next action items for user
1. **Use "Save to GitHub"** to push these changes (rename + updater + Nuvio player + better plugin error)
2. CI builds APK → first time, install manually from Releases (signed). Once installed, all future updates can be triggered from inside the app via **Settings → About → Check for updates**.
3. Test:
   - Settings → "Check for updates" → should fetch latest from GitHub, show "Install" button, and download + launch the system installer
   - Movies → tap any movie → "Play in App (URL / Magnet)" → magnet link → Nuvio-style player
   - Adult → tap any clip → native player (no WebView)
   - Plugins → try `https://raw.githubusercontent.com/phisher98/CXXX/builds/CXXX.json` → now shows "HTTP 404" with URL list rather than silent failure
