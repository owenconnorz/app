package com.aioweb.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.data.api.TmdbMovie
import com.aioweb.app.data.api.TmdbVideo
import com.aioweb.app.data.stremio.InstalledStremioAddon
import com.aioweb.app.data.stremio.StremioStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** A single resolved stream + its source addon, for the picker UI. */
private data class ResolvedStream(
    val addon: InstalledStremioAddon,
    val stream: StremioStream,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(movieId: Long, onBack: () -> Unit, onPlayUrl: (url: String, title: String) -> Unit) {
    val context = LocalContext.current
    val sl = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()

    var movie by remember { mutableStateOf<TmdbMovie?>(null) }
    var videos by remember { mutableStateOf<List<TmdbVideo>>(emptyList()) }
    var imdbId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Stremio resolver state
    val installedAddons by sl.stremio.addons.collectAsState(initial = emptyList())
    var resolving by remember { mutableStateOf(false) }
    var resolvedStreams by remember { mutableStateOf<List<ResolvedStream>>(emptyList()) }
    var resolverError by remember { mutableStateOf<String?>(null) }
    var showStremioSheet by remember { mutableStateOf(false) }

    LaunchedEffect(movieId) {
        scope.launch {
            try {
                movie = sl.tmdb.details(movieId, sl.tmdbApiKey)
                videos = sl.tmdb.videos(movieId, sl.tmdbApiKey).results
                imdbId = sl.tmdb.externalIds(movieId, sl.tmdbApiKey).imdbId
            } catch (e: Exception) {
                error = "Failed to load: ${e.message}"
            }
        }
    }

    fun resolveStreams() {
        val tt = imdbId ?: return run {
            resolverError = "No IMDB ID found for this movie — Stremio addons need an IMDB tt-id to fetch streams."
        }
        if (installedAddons.isEmpty()) {
            resolverError = "No Stremio addons installed. Add one from Settings → Plugins → Stremio addons."
            return
        }
        scope.launch {
            resolving = true
            resolverError = null
            resolvedStreams = emptyList()
            try {
                // Hit every installed addon in parallel and aggregate.
                val all = installedAddons.map { addon ->
                    async {
                        runCatching { sl.stremio.fetchStreams(addon, "movie", tt) }
                            .map { streams -> streams.map { ResolvedStream(addon, it) } }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
                if (all.isEmpty()) {
                    resolverError = "No streams found for this movie across ${installedAddons.size} addon(s)."
                } else {
                    resolvedStreams = all.sortedByDescending { it.stream.qualityScore() }
                    showStremioSheet = true
                }
            } finally {
                resolving = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                AsyncImage(
                    model = movie?.backdropUrl ?: movie?.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.background,
                            )
                        )
                    )
                )
            }
            Column(Modifier.padding(20.dp).offset(y = (-40).dp)) {
                Text(
                    movie?.displayTitle ?: "Loading…",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        String.format("%.1f", movie?.voteAverage ?: 0.0),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(16.dp))
                    movie?.releaseDate?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it.substringBefore("-"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (imdbId != null) {
                        Spacer(Modifier.width(12.dp))
                        Text(imdbId!!, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(20.dp))

                // ─── PRIMARY: Stremio addon resolver ───
                StremioResolverCta(
                    addonCount = installedAddons.size,
                    enabled = imdbId != null,
                    loading = resolving,
                    onClick = { resolveStreams() },
                )
                resolverError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))

                videos.firstOrNull { it.site == "YouTube" && (it.type == "Trailer" || it.type == "Teaser") }?.let { v ->
                    PlayCta("Play Trailer (YouTube)", filled = false) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${v.key}"))
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                PlayCta("Stream on Vidsrc.to (fallback)", filled = false) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://vidsrc.to/embed/movie/$movieId"))
                    )
                }
                Spacer(Modifier.height(10.dp))
                var showPlayUrl by remember { mutableStateOf(false) }
                var customUrl by remember { mutableStateOf("") }
                PlayCta("Play in App (URL / Magnet)", filled = false) { showPlayUrl = true }
                if (showPlayUrl) {
                    AlertDialog(
                        onDismissRequest = { showPlayUrl = false },
                        title = { Text("Play any stream") },
                        text = {
                            Column {
                                Text(
                                    "Paste any of:\n" +
                                        "• HTTP(S) URL — MP4 / MKV / WEBM / HLS (.m3u8) / DASH (.mpd)\n" +
                                        "• magnet: link or .torrent URL — streams via P2P\n" +
                                        "• Embed page URL or full <iframe> HTML — plays via WebView",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = customUrl,
                                    onValueChange = { customUrl = it },
                                    placeholder = { Text("https://… · magnet:?… · <iframe src=\"…\">") },
                                    singleLine = false,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val u = com.aioweb.app.player.extractEmbedUrl(customUrl)
                                showPlayUrl = false
                                if (u.isNotEmpty()) {
                                    onPlayUrl(u, movie?.displayTitle ?: "Playback")
                                }
                            }) { Text("Play") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPlayUrl = false }) { Text("Cancel") }
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text("Overview", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(
                    movie?.overview ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(40.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
        }
    }

    if (showStremioSheet) {
        StremioStreamPickerSheet(
            streams = resolvedStreams,
            onDismiss = { showStremioSheet = false },
            onPick = { rs ->
                showStremioSheet = false
                val url = rs.stream.toPlayableUrl() ?: return@StremioStreamPickerSheet
                onPlayUrl(url, "${movie?.displayTitle ?: "Playback"} · ${rs.addon.name}")
            },
        )
    }
}

@Composable
private fun StremioResolverCta(
    addonCount: Int,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val container = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) container else MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(20.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(
                Icons.Default.Bolt, null,
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (loading) "Resolving streams…"
                else "Find streams · ${addonCount} Stremio addon${if (addonCount == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            if (!enabled && addonCount == 0) {
                Text(
                    "Add a Stremio addon in Settings → Plugins to use this.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!enabled) {
                Text(
                    "Loading IMDB id…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StremioStreamPickerSheet(
    streams: List<ResolvedStream>,
    onDismiss: () -> Unit,
    onPick: (ResolvedStream) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                "Streams (${streams.size})",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(streams, key = { it.stream.toPlayableUrl().orEmpty() + it.addon.id + it.stream.title.orEmpty() }) { rs ->
                    StreamRow(rs, onClick = { onPick(rs) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StreamRow(rs: ResolvedStream, onClick: () -> Unit) {
    val s = rs.stream
    val title = s.title?.takeIf { it.isNotBlank() } ?: s.name ?: s.description ?: "Stream"
    val playable = s.toPlayableUrl()
    val isMagnet = playable?.startsWith("magnet:") == true
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = playable != null, onClick = onClick)
            .padding(12.dp),
    ) {
        Icon(
            if (isMagnet) Icons.Default.Bolt else Icons.Default.PlayArrow,
            null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${rs.addon.name} · ${if (isMagnet) "Torrent (P2P)" else "Direct"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Convert a Stremio stream entry into something our NativePlayer can play. */
private fun StremioStream.toPlayableUrl(): String? = when {
    !url.isNullOrBlank() -> url
    !ytId.isNullOrBlank() -> "https://www.youtube.com/watch?v=$ytId"
    !infoHash.isNullOrBlank() -> {
        // Build a magnet link with optional file index + trackers.
        val baseTrackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://9.rarbg.com:2810/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://exodus.desync.com:6969/announce",
        )
        val trackers = (sources?.filter { it.startsWith("tracker:") }?.map { it.removePrefix("tracker:") }
            ?: emptyList()) + baseTrackers
        val name = title?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: "Stream"
        val trk = trackers.joinToString("&") { "tr=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        "magnet:?xt=urn:btih:$infoHash&dn=$name&$trk"
    }
    else -> null
}

/** Crude quality score so we can sort 1080p > 720p > 480p, magnets get a small penalty. */
private fun StremioStream.qualityScore(): Int {
    val haystack = listOfNotNull(name, title, description).joinToString(" ").lowercase()
    val q = when {
        "2160" in haystack || "4k" in haystack || "uhd" in haystack -> 4
        "1080" in haystack -> 3
        "720" in haystack -> 2
        "480" in haystack -> 1
        else -> 0
    }
    val isDirect = !url.isNullOrBlank()
    return q * 10 + if (isDirect) 1 else 0
}

@Composable
private fun PlayCta(text: String, filled: Boolean = true, onClick: () -> Unit) {
    val container = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val onContainer = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = onContainer)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = onContainer)
    }
}
