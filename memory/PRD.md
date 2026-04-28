# PRD — StreamCloud (Native Android Kotlin)
*(formerly AioWeb)*

## Original problem statement
> "Can you rewrite my app to be kotlin and installs by apk https://github.com/owenconnorz/AioWeb"
> "I dont want this to be an web app I want this to be a kotlin app"
> "Lets make a mediaplayer that movies and porn tab can use also support PTP and anything that the movies need to support also so that the porn videos doesnt need to use embedded website to work"
> "Like cloudstream when switching provider can you get its home feed data from the plugin also when trying to add this repo it doesnt load https://raw.githubusercontent.com/phisher98/CXXX/builds/CXXX.json and can you make my mediaplayer run and look like nuvio app and can you add a in app updater that receives updates from github commits and also rename the entire app to StreamCloud"
> "all metrolist features" — synced lyrics, library (room), sleep timer, repeat/shuffle, equalizer, monet dynamic theme, offline music downloads.

## Architecture
**Fully native Kotlin Compose app**. Lives at `/app/AioWebAndroid/`.
Backend lives at `/app/backend/` (FastAPI + emergentintegrations + HuggingFace proxy).

```
Android (Kotlin Compose) ──→ TMDB           (movies)
                          ──→ NewPipe/YT     (music)
                          ──→ Eporner API    (adult, direct MP4)
                          ──→ libtorrent4j   (P2P / magnet)
                          ──→ LRClib         (lyrics)
                          ──→ GitHub API     (in-app updater)
                          ──→ AioWeb FastAPI ──→ emergentintegrations (chat)
                                            ──→ HuggingFace (image gen + image-to-image edit)
```

## Tech stack
- Kotlin 1.9.24 / Compose BOM 2024.06.00 / Material 3
- AGP 8.5.2, Gradle 8.7, JDK 17
- Retrofit 2.11 + Kotlinx Serialization
- **Media3 ExoPlayer 1.4.1** (HLS + DASH + MediaSession + foreground service)
- **libtorrent4j 2.1.0-32** (arm/arm64/x86_64) + NanoHTTPD 2.3.1
- NewPipe Extractor 0.26.0
- **Room 2.6.1 (kapt)** for Library + downloaded songs
- **android.media.audiofx (Equalizer + BassBoost)** for Audio FX
- Coil 2.7, DataStore 1.1, WorkManager 2.9
- **FileProvider** for sideload-installer hand-off
- minSdk 24, targetSdk 34

## What's implemented (Feb 2026)
**Backend (`/app/backend/server.py`)** — `/api/ai/chat`, `/api/ai/image` (Nano Banana), `/api/ai/image_hf` (HuggingFace text→image), `/api/ai/edit_image` (HuggingFace image-to-image), `/api/movies/trending`.

**Android Native App (StreamCloud)**
- 5-tab Compose shell (Movies / Music / AI / Library or Adult / Settings)
- **Movies**: TMDB trending + CloudStream plugin source switcher; full `.cs3` runtime via DexClassLoader (Android 14+ read-only workaround).
- **Music** (Metrolist parity):
  - NewPipe Extractor home feed + search
  - Foreground `MusicPlaybackService` w/ MediaSession notification + lock-screen controls
  - SimpleCache (256 MB LRU)
  - LRClib **synced lyrics** with active-line highlight
  - **Sleep timer** chip (5/10/15/30/45/60/90 min)
  - **Repeat / Shuffle** mirrored from MediaController
  - **Now-Playing** full sheet (artwork, slider, lyrics)
  - **Library (Room DB)**: liked, recent, most-played, downloaded
  - **Equalizer** (Flat/Pop/Rock/Jazz/Bass/Vocal) + **Bass Boost** via `audiofx.Equalizer`/`BassBoost` bound to player's audio session — reactive to settings
  - **Offline music downloads** (`MusicDownloader`) — resolves stream via NewPipe, writes to `<files>/music/<hash>.m4a`, persists `localPath` in Room. `play()` prefers offline copy when present
- **AI**:
  - Chat (Emergent Universal Key)
  - Image gen (Nano Banana default; HuggingFace SDXL when NSFW toggle is on, requires user HF token in Settings)
  - **Image-to-image editing** tab (HuggingFace) with system image picker
- **Adult**: Eporner search → **native Media3 playback (no WebView)** via direct-MP4 resolution from `/api/v2/video/search/?id=` (`bestMp4()` picks 1080p > 720p > 480p…)
- **Plugins screen**: per-plugin install/uninstall, multi-repo, granular HTTP error messages
- **Settings**: backend URL, HF token, AI provider/model, NSFW toggle, video/audio quality, **Audio FX** section (Equalizer + Bass Boost), **Appearance** section (Material You / Monet toggle), Wi-Fi-only downloads, **Check for updates**
- **Library tab** — sections: Downloaded, Liked, Recently played (Room-backed)
- **In-app updater** — polls GitHub Releases API, downloads APK, hands off to PackageInstaller via FileProvider
- **Nuvio-style media player** — fullscreen, auto-hiding controls, double-tap ±10s, gradient scrims, slider, used by Movies + Adult
- **Unified player** supports HLS, DASH, MP4/MKV/WEBM, and magnet/.torrent (libtorrent4j → NanoHTTPD → ExoPlayer)
- **Material You (Monet)** — opt-in toggle in Settings; `dynamicDarkColorScheme` on Android 12+, falls back to in-house dark palette
- **Display name renamed to "StreamCloud"** (kept `applicationId=com.aioweb.app` so existing installs can still receive updates).

## CI / Build
- GitHub Actions builds debug + signed-release APKs on every push
- Local container CAN now run `./gradlew compileDebugKotlin` via:
  - Java 17 + Android cmdline-tools 34 + build-tools 34 (installed in pod)
  - aapt2 wrapped through `qemu-x86_64-static` because the preview is ARM64-only
  - `-Pandroid.aapt2FromMavenOverride=/opt/aapt2-wrap/aapt2`
- Full `assembleDebug` is still slow under qemu — keep using GitHub Actions for releases.

## Known constraints
- Local APK packaging is slow (qemu-emulated aapt2). CI on `ubuntu-latest` builds in ~2 min.
- Music downloads use OkHttp (single-shot). WorkManager queue not yet attached for movie/video downloads.
- Plugin runtime works for most providers but tested mostly with phisher98/CXXX repo.

## Latest changes (Feb 2026)
- Fixed Gradle plugin DSL conflict (`org.jetbrains.kotlin.kapt` was being requested twice)
- Fixed `AiScreen.kt` non-exhaustive `when` (added missing `Edit` branch + image-picker UI)
- Fixed `CloudstreamApi.kt` JVM-signature clash (`fixUrl` top-level vs extension) via `@JvmName`
- Fixed `MusicController.kt` invalid `cont.resume(throw it)` → `resumeWith(Result.failure(it))`
- Added Equalizer / Bass-boost (Metrolist parity)
- Added Material You (Monet) toggle
- Added Offline music downloads (TrackEntity.localPath + MusicDownloader + MiniPlayer download icon)
- Library tab fully reworked — Downloaded / Liked / Recently played sections from Room
- **(NEW)** Fixed Kotlin compile error in `NuvioRuntime.kt` — Rhino `BaseFunction` is an abstract Java class so SAM-conversion lambdas don't infer; added a `jsFn { c, s, t, args -> ... }` helper that returns an anonymous `BaseFunction()` subclass.
- **(NEW)** Fixed missing imports in `PluginsScreen.kt` (`verticalScroll`, `rememberScrollState`, `Icons.Default.CheckCircle`).
- **(NEW)** Player now **forces landscape orientation** (`ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE`) while on screen, restored on dispose.
- **(NEW)** Fixed crash when tapping the in-player **Sources** button — defensive `distinctBy { it.id }` to dedupe colliding Stremio/Nuvio source ids that were violating Compose `LazyColumn(items, key={...})` uniqueness.
- **(NEW)** Cleaned up the awkward `{ -> ... }` empty-arg lambda in `PlayerToolbarPill(onSourcesClick = ...)` to a standard `() -> Unit` closure.
- **(NEW — Metrolist playlist parity, Feb 2026)** Redesigned `YtPlaylistScreen.kt`:
  - Large 220dp hero cover art (first track's artwork) with gradient fallback, title, track count, Play + Shuffle buttons.
  - Replaced per-row play/download buttons with a **3-dot menu** (Play / Play next / Add to queue / Download / Remove download / Share).
  - In-progress download ring still shown inline when a download is mid-flight.
  - Downloaded badge (`DownloadDone` icon) inline with the title.
- **(NEW — Feb 2026)** `YtPlayback.kt`:
  - Extracted `resolvePlayable()` that returns a ready-to-play `MediaItem` and upserts the Room `TrackEntity`. Offline-first: uses `TrackEntity.localPath` when the file exists, else resolves via NewPipe.
  - Added `playNext()` / `addToQueue()` which thread-hop to Main before calling `MediaController.addMediaItem()`.
  - `playPlaylist()` now actually builds a queue (first plays immediately, rest are appended on IO).
  - Added `removeDownload()` as the counterpart to `downloadSong()`.
- **(NEW — OpenTune-style Settings hub, Feb 2026)** Full rewrite of `SettingsScreen.kt`:
  - Large "Settings" display title, hero card (StreamCloud icon + version chip).
  - 2×2 big tile grid (Appearance / Player and audio / Storage / Privacy).
  - Horizontal chip row (Integration → CloudStream Plugins, Account → YT Music login, AI → provider defaults).
  - Grouped sections (USER INTERFACE / PLAYER & CONTENT / PRIVACY & SECURITY / STORAGE & DATA / SYSTEM & ABOUT) with tinted-icon hub rows that expand inline.
  - About dialog with GitHub source + bug report links.
- **(NEW — Downloads & Theme polish, Feb 2026)**
  - **Parallel downloads** — `MusicDownloader` now uses a `Semaphore(3)` so up to 3 songs download concurrently (Metrolist parity).
  - **System notifications** — `MusicDownloadNotifier` posts an ongoing progress notification per download (throttled to ~250ms updates) and a 4-second auto-dismiss "Downloaded" confirmation when complete. Uses the existing `POST_NOTIFICATIONS` permission already in the manifest.
  - **Album-art-driven dynamic theme** — new `AlbumArtThemeBus` extracts the vibrant Palette swatch from the currently playing track's artwork (Coil → Bitmap → `Palette.from(...).vibrantSwatch ?? lightVibrantSwatch ?? dominantSwatch`). `AioWebTheme` now overlays this color as `MaterialTheme.colorScheme.primary`, so the play button / nav highlight / mini player / 3-dot menu accent the current track. Falls back to the house violet when nothing is playing.
- **(NEW — Now-playing indicator + home click fix, Feb 2026)**
  - Added `audio/PlaybackBus.kt` — global StateFlow of `nowPlayingMediaId` + `isPlaying`, hooked into `MusicController` via a single `Player.Listener`. Attached once on app start in `AioWebApp.kt`.
  - Added `ui/components/PlayingBars.kt` — Metrolist's signature 3-bar animated equalizer. Each bar uses an independent `infiniteRepeatable` so it feels organic; freezes when paused.
  - `PlaylistTrackRow` (YtPlaylistScreen) now overlays `PlayingBars` on the album art and tints the row primary-18% when the playback bus says this song is current.
  - Fixed home-feed playlist tap → `MusicScreen` `YtHomePlaylistCard` was passing no `onClick`, which silently used the default empty lambda. Now wires through `onOpenPlaylist(pl.id, pl.title)`.
- **(NEW — gitignore cleanup, Feb 2026)** Replaced corrupted 568-line `.gitignore` (had the same env-vars block duplicated 60+ times due to a `-e` heredoc bug) with the user-supplied 91-line clean version. This was confusing the platform's 3-way merger and producing phantom conflicts on `SettingsScreen.kt`.

## Backlog / next iterations
- **P1** Picture-in-Picture (PiP) for the player
- **P1** Brightness/volume vertical drag gestures (Nuvio-style)
- **P1** Subtitle track picker + external SRT/VTT loader in NativePlayerScreen
- **P1** Refactor monolithic `MovieDetailScreen.kt` / `PluginsScreen.kt` into per-ecosystem modules (CloudStream / Stremio / Nuvio)
- **P2** Downloads tab for movies/adult via WorkManager
- **P2** Real-world torrent streaming verification with magnet links
- **P2** Per-ABI APK splits / AAB
- **P3** Supabase auth (`supabase-kt`), AI face-swap, FCM push

## Next action items for user
1. **Use "Save to GitHub"** to push these changes (compile fixes + Equalizer + Monet + offline downloads + Library)
2. CI will build a fresh APK; install on device.
3. Test:
   - Settings → Audio FX → Equalizer ON, pick "Rock" → confirm bass/treble change while a song plays
   - Settings → Appearance → Material You ON (Android 12+) → confirm primary colors match wallpaper
   - Music → tap Download icon on mini-player → progress bar → song appears under Library → Downloaded
   - Toggle airplane mode and replay a downloaded song — should stream from local `m4a` file
   - AI → "Image edit" tab → pick photo → describe edit → confirm HF call returns edited image
