package com.aioweb.app.player

import android.annotation.SuppressLint
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified native media player powered by Media3 ExoPlayer.
 * Handles HTTP(S) progressive (MP4/MKV/WEBM), HLS (.m3u8), DASH (.mpd) and
 * magnet/torrent (via [TorrentStreamServer] proxied through a local HTTP port).
 *
 * @param streamUrl direct video URL or `magnet:` link
 * @param title    title shown in the top bar
 * @param headers  optional HTTP headers (e.g., Referer for plugin sources)
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun NativePlayerScreen(
    streamUrl: String,
    title: String,
    headers: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    val torrentServer = remember { mutableStateOf<TorrentStreamServer?>(null) }

    // Resolve magnet → local proxy URL, otherwise use the URL directly.
    LaunchedEffect(streamUrl) {
        val isTorrent = streamUrl.startsWith("magnet:", true) ||
                streamUrl.endsWith(".torrent", true)
        if (isTorrent) {
            val server = TorrentStreamServer(context.applicationContext)
            torrentServer.value = server
            scope.launch {
                val proxied = withContext(Dispatchers.IO) {
                    runCatching { server.start(streamUrl) }.getOrNull()
                }
                if (proxied == null) {
                    resolveError = "Could not fetch torrent metadata. Try another source."
                } else {
                    resolvedUrl = proxied
                }
            }
        } else {
            resolvedUrl = streamUrl
        }
    }

    // Build the ExoPlayer instance once we have a URL.
    val player = remember { mutableStateOf<ExoPlayer?>(null) }
    LaunchedEffect(resolvedUrl) {
        val url = resolvedUrl ?: return@LaunchedEffect
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("AioWebAndroid/1.0 (ExoPlayer)")
            .also { f ->
                if (headers.isNotEmpty()) f.setDefaultRequestProperties(headers)
            }
        val dataSourceFactory: DataSource.Factory = httpFactory

        val mediaItem = MediaItem.fromUri(url)
        val source: MediaSource = when {
            url.contains(".m3u8", true) ->
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            url.contains(".mpd", true) ->
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            else ->
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        val ex = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaSource(source)
                prepare()
                playWhenReady = true
            }
        player.value = ex
    }

    // Keep screen on while playing.
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

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val ex = player.value
            if (ex != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { it.player = ex },
                )
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (resolveError != null) {
                        Text(resolveError!!, color = Color.White)
                    } else {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (streamUrl.startsWith("magnet:", true))
                                "Fetching torrent metadata…"
                            else "Loading…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
