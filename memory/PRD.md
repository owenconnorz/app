# PRD — AioWeb Native Android (Kotlin)

## Original problem statement
> "Can you rewrite my app to be kotlin and installs by apk https://github.com/owenconnorz/AioWeb"
> "build this into another new repo for me and keep this one as a web one"
> "I dont want this to be an web app I want this to be a kotlin app"
> "Everything just add it all"
> "Lets make a mediaplayer that movies and porn tab can use also support PTP and anything that the movies need to support also so that the porn videos doesnt need to use embedded website to work" (Feb 2026)

## Architecture
**Fully native Kotlin Compose app** (NOT a WebView wrapper). Lives at `/app/AioWebAndroid/`.
Backend lives at `/app/backend/` (FastAPI + emergentintegrations) and exposes AI endpoints.

```
Android (Kotlin Compose) ──→ TMDB           (movies)
                          ──→ NewPipe/YT     (music)
                          ──→ Eporner API    (adult, direct MP4)
                          ──→ libtorrent4j   (P2P / magnet streaming)
                          ──→ AioWeb FastAPI ──→ emergentintegrations (AI)
```

## Tech stack
- Kotlin 1.9.24 / Compose BOM 2024.06.00 / Material 3
- AGP 8.5.2, Gradle 8.7, JDK 17
- Retrofit 2.11 + Kotlinx Serialization
- **Media3 ExoPlayer 1.4.1** + HLS + DASH + MediaSession
- **libtorrent4j 2.1.0-32** (arm, arm64, x86_64) + NanoHTTPD 2.3.1 — P2P streaming
- NewPipe Extractor 0.24.2 (YouTube without API keys)
- Coil 2.7, DataStore 1.1, WorkManager 2.9
- minSdk 24, targetSdk 34

## What's implemented (Feb 2026 update)
**Backend (`/app/backend/server.py`)**
- `POST /api/ai/chat` — text gen (OpenAI/Claude/Gemini via emergentintegrations)
- `POST /api/ai/image` — Nano Banana image gen
- `GET /api/movies/trending` — TMDB proxy
- `EMERGENT_LLM_KEY` configured in backend .env

**Android Native App**
- Compose shell with Material 3 dark theme (brand violet/cyan/pink)
- Bottom navigation with 5 tabs (NSFW-on swaps Library for Adult)
- **Movies**: TMDB trending + plugin source switcher (Metrolist redesign) + detail screen with trailer + Vidsrc embed + **NEW** "Play in App (URL/Magnet)" CTA
- **Music**: NewPipe Extractor + Media3 audio + mini player + Metrolist aesthetic
- **AI tab**: Chat (multi-turn) + Nano Banana image gen
- **Adult tab**: Eporner search grid → **NEW native Media3 ExoPlayer playback (no WebView)** via direct-MP4 resolution from `/api/v2/video/search/?id=` (`all_qualities` / `sources`)
- **Plugins screen**: per-plugin install + uninstall, multi-repo, Metrolist look
- **Settings**: backend URL + provider/model + NSFW toggle (DataStore)
- **NEW Unified player** at `com.aioweb.app.player.NativePlayerScreen` — used by both Movies and Adult tabs, auto-detects HLS (.m3u8), DASH (.mpd), progressive MP4/MKV/WEBM, and `magnet:`/`.torrent` (proxied through embedded NanoHTTPD fed by libtorrent4j sequential download)
- Release signing keystore + GitHub Actions CI → debug + signed-release APK
- Auto-incrementing `versionCode` from `GITHUB_RUN_NUMBER` so debug APKs install over each other
- Per-plugin uninstall + repo source chips on Movies tab

## Known constraints
- **APK build cannot run in this Emergent preview container** (arm64 vs Google's x86_64 aapt2 binary).
  Local `compileDebugKotlin` triggers AAPT2 transitively → also fails.
  CI on `ubuntu-latest` (x86_64) builds successfully. User must push to GitHub.

## Not done yet
- **CloudStream plugin EXECUTION** (DEX classloader → invoking `MainAPI.search/load` from .cs3 files) — installed plugins are listed/managed but not yet executed for actual streaming. Movies still falls back to Vidsrc embed or user-pasted URL/magnet.
- **Library tab:** placeholder UI only (Room DB persistence pending)
- **Downloads:** WorkManager queue + notifications planned, not implemented
- **Supabase auth:** deferred
- **AI image-to-image (face swap):** not yet wired
- **Subtitle UI**: ExoPlayer can render subs, but no track selection UI / external SRT loader yet

## Backlog / next iterations (priority order)
- P0  CloudStream `.cs3` execution → real streaming via installed plugins (DEX classloader + cloudstream3 API stubs)
- P1  Subtitle track picker + external SRT/VTT loader in NativePlayerScreen
- P1  Pip-in-Picture (PiP) for video player + landscape orientation handling
- P2  Room DB for Library + Downloads with WorkManager queue
- P2  Per-ABI APK splits / AAB for Play Store
- P3  Supabase auth (sign-in screen + persist session)
- P3  AI face-swap & image editing (with reference image upload)
- P3  Native push notifications via FCM

## Next action items for user
1. **Use "Save to GitHub"** to push the latest changes (player + libtorrent4j + Eporner direct MP4 + PluginsScreen fix)
2. Wait for CI → grab the new debug or signed APK from the Releases tab
3. Sideload over the existing install (no uninstall needed; debug builds use the same release keystore now)
4. Test:
   - Adult tab → tap any video → should open native player with direct MP4 (no WebView)
   - Movies → tap a movie → "Play in App (URL / Magnet)" → paste a `magnet:?…` link → should resolve metadata then play through the embedded HTTP server
   - Plugins screen → install / uninstall flow should work end-to-end
