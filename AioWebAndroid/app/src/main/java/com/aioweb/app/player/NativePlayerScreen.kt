package com.aioweb.app.player

import android.annotation.SuppressLint
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Nuvio-style native media player.
 *
 * Behaviour:
 *  - Edge-to-edge black canvas, no system bars
 *  - Tap once: toggles overlay (auto-hides after 3s of inactivity)
 *  - Double-tap left half: rewind 10s | double-tap right half: forward 10s
 *  - Top bar: back button + title
 *  - Bottom bar: progress slider + current/total time
 *  - Center: large play/pause button
 *
 * Supports HLS (.m3u8), DASH (.mpd), progressive (MP4/MKV/WEBM) and `magnet:`/`.torrent`
 * (proxied through libtorrent4j + NanoHTTPD via [TorrentStreamServer]).
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun NativePlayerScreen(
    streamUrl: String,
    title: String,
    headers: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
    /** Subtitle line under the title (e.g. "Torrentio · 1080p"). */
    subtitle: String? = null,
    /** Optional: full stream catalog. When non-empty, the player shows a "Sources" pill
     *  that opens a switcher sheet so the user can change source mid-playback. */
    sources: List<PlayerSource> = emptyList(),
    /** Currently selected source id — drives the "selected" highlight in the sheet. */
    selectedSourceId: String? = null,
    /** Called with a different source id when the user picks a new stream. The host
     *  is expected to swap [streamUrl]/[subtitle] and re-enter the composition. */
    onSwitchSource: ((PlayerSource) -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Stream resolution (handle magnets through TorrentStreamServer) -------------------------
    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    val torrentServer = remember { mutableStateOf<TorrentStreamServer?>(null) }

    LaunchedEffect(streamUrl) {
        val isTorrent = streamUrl.startsWith("magnet:", true) || streamUrl.endsWith(".torrent", true)
        if (isTorrent) {
            val server = TorrentStreamServer(context.applicationContext)
            torrentServer.value = server
            scope.launch {
                val proxied = withContext(Dispatchers.IO) {
                    runCatching { server.start(streamUrl) }.getOrNull()
                }
                if (proxied == null) resolveError = "Could not fetch torrent metadata."
                else resolvedUrl = proxied
            }
        } else {
            resolvedUrl = streamUrl
        }
    }

    // --- ExoPlayer ------------------------------------------------------------------------------
    val player = remember { mutableStateOf<ExoPlayer?>(null) }
    val needsWebView = remember(resolvedUrl) {
        val u = resolvedUrl?.lowercase().orEmpty()
        u.isNotEmpty() && !u.startsWith("http://127.0.0.1") &&
            !u.endsWith(".mp4") && !u.endsWith(".mkv") && !u.endsWith(".webm") &&
            !u.endsWith(".m4v") && !u.endsWith(".mov") &&
            !u.contains(".m3u8") && !u.contains(".mpd") &&
            !u.startsWith("magnet:") &&
            // Common embed URL hints — Eporner, vidsrc, etc.
            (u.contains("/embed") || u.contains("/iframe") || u.contains("/video/") ||
             u.endsWith(".html") || u.endsWith("/"))
    }

    LaunchedEffect(resolvedUrl, needsWebView) {
        if (needsWebView) {
            // Don't build ExoPlayer for HTML embed pages — WebView handles them.
            player.value?.release()
            player.value = null
            return@LaunchedEffect
        }
        val url = resolvedUrl ?: return@LaunchedEffect
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("StreamCloud/1.0 (ExoPlayer)")
            .also { f -> if (headers.isNotEmpty()) f.setDefaultRequestProperties(headers) }
        val dsFactory: DataSource.Factory = httpFactory

        val mediaItem = MediaItem.fromUri(url)
        val source: MediaSource = when {
            url.contains(".m3u8", true) -> HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
            url.contains(".mpd", true)  -> DashMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
            else -> ProgressiveMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
        }
        val ex = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
            .apply {
                setMediaSource(source)
                prepare()
                playWhenReady = true
            }
        player.value = ex
    }

    // --- Window flags + cleanup ----------------------------------------------------------------
    val window = (context as? Activity)?.window
    DisposableEffect(Unit) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.value?.release()
            player.value = null
            torrentServer.value?.stop()
            torrentServer.value = null
        }
    }

    // --- Player state for the overlay ----------------------------------------------------------
    val ex = player.value
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferingMs by remember { mutableStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(ex) {
        ex ?: return@LaunchedEffect
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(state: Int) {
                durationMs = ex.duration.coerceAtLeast(0L)
            }
        }
        ex.addListener(listener)
        while (true) {
            positionMs = ex.currentPosition.coerceAtLeast(0L)
            durationMs = ex.duration.coerceAtLeast(0L)
            bufferingMs = ex.bufferedPosition.coerceAtLeast(0L)
            isPlaying = ex.isPlaying
            delay(500)
        }
    }

    // Auto-hide controls after 3s.
    LaunchedEffect(controlsVisible, lastInteractionTs) {
        if (controlsVisible) {
            delay(3000)
            if (System.currentTimeMillis() - lastInteractionTs >= 2900) controlsVisible = false
        }
    }

    fun bumpInteraction() {
        controlsVisible = true
        lastInteractionTs = System.currentTimeMillis()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- Video surface ---------------------------------------------------------------------
        if (needsWebView && resolvedUrl != null) {
            EmbedWebView(resolvedUrl!!)
        } else if (ex != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = ex },
            )
        }

        // --- Gesture layer ---------------------------------------------------------------------
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(ex) {
                        detectTapGestures(
                            onTap = { bumpInteraction() },
                            onDoubleTap = { offset: Offset ->
                                ex ?: return@detectTapGestures
                                val side = if (offset.x < widthPx / 2f) -10_000L else +10_000L
                                ex.seekTo((ex.currentPosition + side).coerceAtLeast(0L))
                                bumpInteraction()
                            },
                        )
                    }
            )
        }

        // --- Overlay (top bar + center play + bottom bar) — only for ExoPlayer mode ----------
        var locked by remember { mutableStateOf(false) }
        var showSourcesSheet by remember { mutableStateOf(false) }

        AnimatedVisibility(
            visible = controlsVisible && !needsWebView,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (!locked) {
                    // ── Top-LEFT: title + subtitle ─────────────────────────────────
                    Column(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 28.dp, top = 22.dp, end = 220.dp),
                    ) {
                        Text(
                            title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                subtitle,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                // ── Top-RIGHT: lock + back as dark capsule pills ───────────────────
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 14.dp, top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerCapsuleIcon(
                        icon = if (locked) androidx.compose.material.icons.Icons.Default.LockOpen
                               else androidx.compose.material.icons.Icons.Default.Lock,
                        contentDescription = if (locked) "Unlock" else "Lock controls",
                        onClick = { locked = !locked; bumpInteraction() },
                    )
                    if (!locked) {
                        PlayerCapsuleIcon(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onBack,
                        )
                    }
                }

                if (!locked) {
                    // ── Center: ⏮10 / ▶ / ⏭10 — outlined white, no scrim ─────────
                    Row(
                        Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(56.dp),
                    ) {
                        OutlinedPlayIcon(Icons.Default.Replay10, "Rewind 10s") {
                            ex?.seekTo((ex.currentPosition - 10_000L).coerceAtLeast(0L))
                            bumpInteraction()
                        }
                        OutlinedPlayIcon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            big = true,
                        ) {
                            ex ?: return@OutlinedPlayIcon
                            if (ex.isPlaying) ex.pause() else ex.play()
                            bumpInteraction()
                        }
                        OutlinedPlayIcon(Icons.Default.Forward10, "Forward 10s") {
                            ex?.seekTo((ex.currentPosition + 10_000L).coerceAtMost(durationMs))
                            bumpInteraction()
                        }
                    }

                    // ── Bottom: chip-pill timestamps + slider + toolbar pill ──────
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 22.dp),
                    ) {
                        Slider(
                            value = if (durationMs > 0) positionMs / durationMs.toFloat() else 0f,
                            onValueChange = { v ->
                                ex?.seekTo((v * durationMs).toLong())
                                bumpInteraction()
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.30f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TimestampChip(formatTime(positionMs))
                            TimestampChip(formatTime(durationMs))
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            PlayerToolbarPill(
                                onSourcesClick = if (sources.isNotEmpty())
                                    { -> showSourcesSheet = true; bumpInteraction() }
                                else null,
                            )
                        }
                    }
                }
            }
        }

        if (showSourcesSheet) {
            SourcesPickerSheet(
                sources = sources,
                selectedSourceId = selectedSourceId,
                onPick = { src ->
                    showSourcesSheet = false
                    onSwitchSource?.invoke(src)
                },
                onDismiss = { showSourcesSheet = false },
            )
        }

        // --- Top bar for WebView mode (just back button + title) -----------------------------
        if (needsWebView) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent))
                    )
                    .padding(top = 12.dp, start = 8.dp, end = 16.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        }

        // --- Loading / error states ------------------------------------------------------------
        if (ex == null && !needsWebView) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (resolveError != null) {
                    Text(
                        resolveError!!,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (streamUrl.startsWith("magnet:", true))
                            "Connecting to peers…" else "Loading…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun NuvioCircleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    val size = if (big) 76.dp else 56.dp
    val iconSize = if (big) 44.dp else 28.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun OutlinedPlayIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    val size = if (big) 84.dp else 60.dp
    val iconSize = if (big) 48.dp else 32.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun PlayerCapsuleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(width = 64.dp, height = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TimestampChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PlayerToolbarPill(onSourcesClick: (() -> Unit)?) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarItem(Icons.Default.AspectRatio, "Fit") { /* TODO: cycle resize mode */ }
        ToolbarItem(Icons.Default.Speed, "1x") { /* TODO: speed picker */ }
        ToolbarItem(Icons.Default.ClosedCaption, "Subs") { /* TODO: subtitle picker */ }
        ToolbarItem(Icons.Default.VolumeUp, "Audio") { /* TODO: audio track picker */ }
        ToolbarItem(
            Icons.AutoMirrored.Filled.CompareArrows,
            "Sources",
            enabled = onSourcesClick != null,
        ) { onSourcesClick?.invoke() }
    }
}

@Composable
private fun ToolbarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesPickerSheet(
    sources: List<PlayerSource>,
    selectedSourceId: String?,
    onPick: (PlayerSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val addonFilters = remember(sources) {
        listOf("All") + sources.map { it.addonName }.distinct()
    }
    var activeFilter by remember(sources) { mutableStateOf("All") }
    val filtered = remember(activeFilter, sources) {
        if (activeFilter == "All") sources else sources.filter { it.addonName == activeFilter }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111111),
        scrimColor = Color.Black.copy(alpha = 0.7f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Header: Reload (left) — title (center) — Close (right)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* TODO: re-resolve from caller */ }) {
                    Icon(Icons.Default.Refresh, "Reload", tint = Color.White)
                }
                Text(
                    "Streams",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        "Close",
                        tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Addon filter chip row
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            ) {
                items(
                    addonFilters,
                    key = { it },
                ) { name ->
                    SourceFilterChip(name, name == activeFilter) { activeFilter = name }
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(filtered, key = { it.id }) { src ->
                    StreamPickerRow(
                        src = src,
                        selected = src.id == selectedSourceId,
                        onClick = { onPick(src) },
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No streams from $activeFilter.",
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Color.White
                else Color.White.copy(alpha = 0.12f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.Black else Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun StreamPickerRow(src: PlayerSource, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) Color.White.copy(alpha = 0.18f)
                else Color.White.copy(alpha = 0.06f)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Icon(
            if (src.isMagnet) Icons.Default.Bolt
            else Icons.Default.PlayArrow,
            null,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                src.label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
            )
            Text(
                "${src.addonName} · ${if (src.isMagnet) "Torrent" else "Direct"}" +
                    (src.qualityTag?.let { " · $it" } ?: ""),
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}


private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

/**
 * Renders an HTML embed page in a fullscreen WebView. Used when the resolved URL
 * isn't a direct media stream (e.g., Eporner embed pages, plugin iframe sources).
 */
@Composable
private fun EmbedWebView(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webChromeClient = android.webkit.WebChromeClient()
                webViewClient = android.webkit.WebViewClient()
                loadUrl(url)
            }
        },
    )
}

/**
 * Extracts a playable URL from a user-pasted string. Accepts:
 *  - A bare URL  → returned as-is
 *  - A magnet:   → returned as-is
 *  - An HTML `<iframe ... src="...">` snippet → returns the `src` attribute
 *  - A `<video src="...">` snippet → returns the `src` attribute
 */
fun extractEmbedUrl(input: String): String {
    val s = input.trim()
    if (s.isEmpty()) return s
    if (s.startsWith("magnet:", true)) return s
    if (s.startsWith("http://", true) || s.startsWith("https://", true)) return s
    // Pull `src="..."` from any iframe / video / source tag.
    val srcRegex = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    return srcRegex.find(s)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: s
}
