package com.aioweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aioweb.app.data.library.LibraryDb
import com.aioweb.app.data.library.TrackEntity
import com.aioweb.app.data.ytmusic.YtMusicLibrary
import com.aioweb.app.data.ytmusic.YtmLibraryArtist
import com.aioweb.app.data.ytmusic.YtmPlaylist
import com.aioweb.app.data.ytmusic.YtmSong
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private enum class LibTab(val label: String) {
    Playlists("Playlists"),
    Songs("Songs"),
    Albums("Albums"),
    Artists("Artists"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPlaylist: (id: String, title: String) -> Unit = { _, _ -> },
    onOpenArtist: (channelUrl: String) -> Unit = {},
) {
    val context = LocalContext.current
    val dao = remember { LibraryDb.get(context).tracks() }
    val sl = remember(context) { com.aioweb.app.data.ServiceLocator.get(context) }
    val ytCookie by sl.settings.ytMusicCookie.collectAsState(initial = "")
    val scope = rememberCoroutineScope()

    // YT Music library — synced lazily the first time the tab is opened while signed in,
    // and again when the user hits the refresh button.
    var ytLibrary by remember { mutableStateOf(com.aioweb.app.data.ytmusic.YtMusicLibrary()) }
    var ytLoading by remember { mutableStateOf(false) }

    LaunchedEffect(ytCookie) {
        if (ytCookie.isBlank()) {
            ytLibrary = com.aioweb.app.data.ytmusic.YtMusicLibrary(
                failureReason = "Not signed in.",
            )
            return@LaunchedEffect
        }
        ytLoading = true
        ytLibrary = com.aioweb.app.data.ytmusic.YtMusicLibraryRepository.sync(ytCookie)
        ytLoading = false
    }

    val combined by remember(dao) {
        combine(dao.liked(), dao.recent(), dao.downloaded(), dao.mostPlayed()) { l, r, d, mp ->
            arrayOf(l, r, d, mp)
        }
    }.collectAsState(initial = arrayOf<List<TrackEntity>>(emptyList(), emptyList(), emptyList(), emptyList()))

    val liked = combined[0]; val recent = combined[1]; val downloaded = combined[2]; val mostPlayed = combined[3]

    var tab by remember { mutableStateOf(LibTab.Playlists) }
    var openTile by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Library",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))

        // Top filter chips: Playlists / Songs / Albums / Artists
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibTab.values().forEach { t ->
                LibFilterChip(
                    label = t.label,
                    selected = tab == t,
                    onClick = { tab = t },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (openTile != null) {
            val list = when (openTile) {
                "liked" -> liked
                "downloaded" -> downloaded
                "top50" -> mostPlayed
                "cached" -> recent
                else -> emptyList()
            }
            BackButton(label = openTile.orEmpty().replaceFirstChar { it.uppercase() }) { openTile = null }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.url }) { e -> LibTrackRow(e) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                // ─── YT Music sync status ────────────────────────────────────
                item {
                    YtMusicSyncHeader(
                        signedIn = ytCookie.isNotBlank(),
                        loading = ytLoading,
                        library = ytLibrary,
                        onRefresh = {
                            scope.launch {
                                ytLoading = true
                                ytLibrary = com.aioweb.app.data.ytmusic.YtMusicLibraryRepository.sync(ytCookie)
                                ytLoading = false
                            }
                        },
                    )
                }
                if (ytLibrary.likedSongs.isNotEmpty()) {
                    item {
                        YtSongsSection(
                            title = "Liked songs · YouTube Music",
                            songs = ytLibrary.likedSongs,
                            onSongClick = { /* TODO: wire to MusicPlaybackService */ },
                        )
                    }
                }
                if (ytLibrary.playlists.isNotEmpty()) {
                    item {
                        YtPlaylistSection(
                            title = "Your playlists",
                            playlists = ytLibrary.playlists,
                            onClick = { p -> onOpenPlaylist(p.id, p.title) },
                        )
                    }
                }
                if (ytLibrary.albums.isNotEmpty()) {
                    item {
                        YtPlaylistSection(
                            title = "Your albums",
                            playlists = ytLibrary.albums,
                            onClick = { p -> onOpenPlaylist(p.id, p.title) },
                        )
                    }
                }
                if (ytLibrary.artists.isNotEmpty()) {
                    item {
                        YtArtistsSection(
                            artists = ytLibrary.artists,
                            onClick = { a ->
                                onOpenArtist("https://music.youtube.com/channel/${a.channelId}")
                            },
                        )
                    }
                }

                // ─── Existing local library tiles (2×2 grid) ────────────────
                item {
                    Text(
                        "On this device",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                // Render the four tiles as two manual rows so the whole list stays in one scroll.
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            LibTile(
                                "Liked", Icons.Default.Favorite, liked.size,
                                liked.mapNotNull { it.thumbnail }.take(4),
                                onClick = { openTile = "liked" },
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            LibTile(
                                "Downloaded", Icons.Default.DownloadDone, downloaded.size,
                                downloaded.mapNotNull { it.thumbnail }.take(4),
                                onClick = { openTile = "downloaded" },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            LibTile(
                                "My top 50", Icons.Default.TrendingUp,
                                mostPlayed.size.coerceAtMost(50),
                                mostPlayed.mapNotNull { it.thumbnail }.take(4),
                                onClick = { openTile = "top50" },
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            LibTile(
                                "Cached", Icons.Default.History, recent.size,
                                recent.mapNotNull { it.thumbnail }.take(4),
                                onClick = { openTile = "cached" },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun LibFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun LibTile(
    title: String,
    icon: ImageVector,
    count: Int,
    thumbs: List<String>,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbs.isEmpty()) {
                Icon(
                    icon, title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                // 2x2 thumbnail mosaic (Metrolist style)
                Column {
                    Row(Modifier.weight(1f)) {
                        ThumbCell(thumbs.getOrNull(0), Modifier.weight(1f))
                        ThumbCell(thumbs.getOrNull(1), Modifier.weight(1f))
                    }
                    Row(Modifier.weight(1f)) {
                        ThumbCell(thumbs.getOrNull(2), Modifier.weight(1f))
                        ThumbCell(thumbs.getOrNull(3), Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$count " + if (count == 1) "track" else "tracks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThumbCell(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight().background(Color(0xFF222222))) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BackButton(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            "Back",
            tint = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun LibTrackRow(entity: TrackEntity) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = entity.thumbnail,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entity.title, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                entity.artist, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (entity.localPath != null) {
            Icon(
                Icons.Default.DownloadDone, "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─────────────────────── YouTube Music sync widgets ───────────────────────

@Composable
private fun YtMusicSyncHeader(
    signedIn: Boolean,
    loading: Boolean,
    library: YtMusicLibrary,
    onRefresh: () -> Unit,
) {
    if (!signedIn) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CloudDone, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "YouTube Music · Synced",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            val subtitle = when {
                loading -> "Refreshing…"
                library.failureReason != null -> library.failureReason.orEmpty()
                library.likedSongs.isEmpty() && library.playlists.isEmpty() &&
                    library.albums.isEmpty() && library.artists.isEmpty() ->
                    "Nothing in your YouTube Music library yet."
                else -> "${library.playlists.size} playlists · " +
                    "${library.albums.size} albums · " +
                    "${library.artists.size} artists · " +
                    "${library.likedSongs.size} liked"
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, "Refresh")
            }
        }
    }
}

@Composable
private fun YtPlaylistSection(
    title: String,
    playlists: List<YtmPlaylist>,
    onClick: (YtmPlaylist) -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(playlists, key = { "yp_${it.id}" }) { pl ->
            Column(
                Modifier
                    .width(150.dp)
                    .clickable { onClick(pl) },
            ) {
                AsyncImage(
                    model = pl.thumbnail,
                    contentDescription = pl.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    pl.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                pl.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun YtArtistsSection(
    artists: List<YtmLibraryArtist>,
    onClick: (YtmLibraryArtist) -> Unit,
) {
    Text(
        "Subscribed artists",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(artists, key = { "ya_${it.channelId}" }) { a ->
            Column(
                Modifier
                    .width(120.dp)
                    .clickable { onClick(a) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = a.thumbnail,
                    contentDescription = a.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    a.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun YtSongsSection(
    title: String,
    songs: List<YtmSong>,
    onSongClick: (YtmSong) -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    // Show a preview — first 5 songs — with a "View all" affordance at the bottom.
    Column {
        songs.take(5).forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSongClick(s) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = s.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        s.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (songs.size > 5) {
            TextButton(
                onClick = { /* TODO: open dedicated liked songs screen */ },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text("View all ${songs.size} liked songs")
            }
        }
    }
}

