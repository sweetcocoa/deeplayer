package com.deeplayer.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deeplayer.core.contracts.TrackMetadata
import java.io.File

private val filterLabels = listOf("곡", "앨범", "아티스트", "폴더", "플레이리스트")

private enum class SortOption(val label: String) {
  TITLE("제목"),
  ARTIST("아티스트"),
  ALBUM("앨범"),
  DURATION("재생 시간"),
}

@Composable
fun LibraryScreen(
  tracks: List<TrackMetadata>,
  currentTrackId: String?,
  onTrackClick: (TrackMetadata) -> Unit,
  onPermissionRequest: () -> Unit,
  permissionGranted: Boolean,
  modifier: Modifier = Modifier,
) {
  var selectedChipIndex by remember { mutableStateOf(0) }
  var lyricsOnly by remember { mutableStateOf(false) }
  var showSortMenu by remember { mutableStateOf(false) }
  var sortOption by remember { mutableStateOf(SortOption.TITLE) }

  val filteredTracks =
    if (lyricsOnly) {
      tracks.filter { track ->
        val lrcPath = track.filePath.replaceAfterLast('.', "lrc")
        java.io.File(lrcPath).exists()
      }
    } else {
      tracks
    }

  val sortedTracks =
    remember(filteredTracks, sortOption) {
      when (sortOption) {
        SortOption.TITLE -> filteredTracks.sortedBy { it.title.lowercase() }
        SortOption.ARTIST -> filteredTracks.sortedBy { it.artist.lowercase() }
        SortOption.ALBUM -> filteredTracks.sortedBy { it.album.lowercase() }
        SortOption.DURATION -> filteredTracks.sortedBy { it.durationMs }
      }
    }

  Column(modifier = modifier.fillMaxSize()) {
    // Top bar area
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "라이브러리",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.weight(1f),
      )
      Box {
        IconButton(onClick = { showSortMenu = true }) {
          Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "정렬")
        }
        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
          SortOption.entries.forEach { option ->
            DropdownMenuItem(
              text = { Text(option.label) },
              onClick = {
                sortOption = option
                showSortMenu = false
              },
              trailingIcon = {
                if (sortOption == option) {
                  Icon(imageVector = Icons.Default.Check, contentDescription = null)
                }
              },
            )
          }
        }
      }
    }

    // Filter chips
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      itemsIndexed(filterLabels) { index, label ->
        FilterChip(
          selected = selectedChipIndex == index,
          onClick = { selectedChipIndex = index },
          label = { Text(label) },
        )
      }
      item {
        FilterChip(
          selected = lyricsOnly,
          onClick = { lyricsOnly = !lyricsOnly },
          label = { Text("가사") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Lyrics,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          },
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Content area
    when (selectedChipIndex) {
      0 ->
        SongsContent(
          sortedTracks,
          currentTrackId,
          onTrackClick,
          onPermissionRequest,
          permissionGranted,
          sortOption,
        )
      1 -> AlbumsContent(sortedTracks, currentTrackId, onTrackClick)
      2 -> ArtistsContent(sortedTracks, currentTrackId, onTrackClick)
      3 -> FoldersContent(sortedTracks, currentTrackId, onTrackClick)
      4 -> PlaceholderTab("플레이리스트 탭 준비 중")
    }
  }
}

// ---------------------------------------------------------------------------
// Songs Tab (index 0)
// ---------------------------------------------------------------------------

@Composable
private fun SongsContent(
  tracks: List<TrackMetadata>,
  currentTrackId: String?,
  onTrackClick: (TrackMetadata) -> Unit,
  onPermissionRequest: () -> Unit,
  permissionGranted: Boolean,
  sortOption: SortOption = SortOption.TITLE,
) {
  when {
    !permissionGranted && tracks.isEmpty() -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(text = "음악 파일에 접근하려면 권한이 필요합니다", style = MaterialTheme.typography.bodyLarge)
          Button(onClick = onPermissionRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("권한 허용")
          }
        }
      }
    }
    permissionGranted && tracks.isEmpty() -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "음악 파일이 없습니다", style = MaterialTheme.typography.bodyLarge)
      }
    }
    else -> {
      val listState = rememberLazyListState()
      val textSelector: (TrackMetadata) -> String =
        remember(sortOption) {
          when (sortOption) {
            SortOption.TITLE -> { track -> track.title }
            SortOption.ARTIST -> { track -> track.artist }
            SortOption.ALBUM -> { track -> track.album }
            SortOption.DURATION -> { track -> track.title }
          }
        }
      val indexMap = remember(tracks, textSelector) { buildIndexMap(tracks, textSelector) }

      Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState) {
          items(tracks, key = { it.id }) { track ->
            TrackItem(
              track = track,
              isPlaying = track.id == currentTrackId,
              onClick = { onTrackClick(track) },
            )
            HorizontalDivider()
          }
        }
        FastScrollBar(
          listState = listState,
          indexMap = indexMap,
          modifier = Modifier.align(Alignment.CenterEnd),
        )
      }
    }
  }
}

@Composable
private fun TrackItem(track: TrackMetadata, isPlaying: Boolean, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(64.dp)
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (track.albumArtUri != null) {
      AsyncImage(
        model = track.albumArtUri,
        contentDescription = "앨범 아트",
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
        contentScale = ContentScale.Crop,
      )
    } else {
      AlbumArtPlaceholder(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(6.dp))
    }

    Spacer(modifier = Modifier.width(12.dp))

    // Title and subtitle
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = track.title,
        style = MaterialTheme.typography.bodyLarge,
        color =
          if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${track.artist} \u00B7 ${track.album}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    // Duration
    Text(
      text = formatDuration(track.durationMs),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

// ---------------------------------------------------------------------------
// Albums Tab (index 1)
// ---------------------------------------------------------------------------

private data class AlbumInfo(
  val name: String,
  val artist: String,
  val trackCount: Int,
  val albumArtUri: String?,
)

@Composable
private fun AlbumsContent(
  tracks: List<TrackMetadata>,
  currentTrackId: String?,
  onTrackClick: (TrackMetadata) -> Unit,
) {
  var selectedAlbum by remember { mutableStateOf<String?>(null) }

  val albumMap = remember(tracks) { tracks.groupBy { it.album } }
  val albums =
    remember(albumMap) {
      albumMap
        .map { (album, albumTracks) ->
          AlbumInfo(
            name = album,
            artist = albumTracks.first().artist,
            trackCount = albumTracks.size,
            albumArtUri = albumTracks.first().albumArtUri,
          )
        }
        .sortedBy { it.name }
    }

  if (selectedAlbum != null) {
    AlbumDetailContent(
      albumName = selectedAlbum!!,
      tracks = albumMap[selectedAlbum].orEmpty(),
      currentTrackId = currentTrackId,
      onTrackClick = onTrackClick,
      onBack = { selectedAlbum = null },
    )
  } else {
    if (albums.isEmpty()) {
      EmptyContent("앨범이 없습니다")
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        items(albums, key = { it.name }) { album ->
          AlbumGridItem(album = album, onClick = { selectedAlbum = album.name })
        }
      }
    }
  }
}

@Composable
private fun AlbumGridItem(album: AlbumInfo, onClick: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (album.albumArtUri != null) {
      AsyncImage(
        model = album.albumArtUri,
        contentDescription = "앨범 아트",
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
      )
    } else {
      Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
          )
        }
      }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = album.name,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = album.artist,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun AlbumDetailContent(
  albumName: String,
  tracks: List<TrackMetadata>,
  currentTrackId: String?,
  onTrackClick: (TrackMetadata) -> Unit,
  onBack: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    DetailHeader(title = albumName, onBack = onBack)
    LazyColumn {
      items(tracks, key = { it.id }) { track ->
        TrackItem(
          track = track,
          isPlaying = track.id == currentTrackId,
          onClick = { onTrackClick(track) },
        )
        HorizontalDivider()
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Artists Tab (index 2)
// ---------------------------------------------------------------------------

private data class ArtistInfo(val name: String, val trackCount: Int)

@Composable
private fun ArtistsContent(
  tracks: List<TrackMetadata>,
  currentTrackId: String?,
  onTrackClick: (TrackMetadata) -> Unit,
) {
  var selectedArtist by remember { mutableStateOf<String?>(null) }

  val artistMap = remember(tracks) { tracks.groupBy { it.artist } }
  val artists =
    remember(artistMap) {
      artistMap
        .map { (artist, artistTracks) -> ArtistInfo(name = artist, trackCount = artistTracks.size) }
        .sortedBy { it.name }
    }

  if (selectedArtist != null) {
    ArtistDetailContent(
      artistName = selectedArtist!!,
      tracks = artistMap[selectedArtist].orEmpty(),
      currentTrackId = currentTrackId,
      onTrackClick = onTrackClick,
      onBack = { selectedArtist = null },
    )
  } else {
    if (artists.isEmpty()) {
      EmptyContent("아티스트가 없습니다")
    } else {
      LazyColumn {
        items(artists, key = { it.name }) { artist ->
          ArtistListItem(artist = artist, onClick = { selectedArtist = artist.name })
          HorizontalDivider()
        }
      }
    }
  }
}

@Composable
private fun ArtistListItem(artist: ArtistInfo, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(56.dp)
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Circular artist placeholder
    Surface(
      modifier = Modifier.size(40.dp),
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Default.Person,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = artist.name,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = "${artist.trackCount}곡",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ArtistDetailContent(
  artistName: String,
  tracks: List<TrackMetadata>,
  currentTrackId: String?,
  onTrackClick: (TrackMetadata) -> Unit,
  onBack: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    DetailHeader(title = artistName, onBack = onBack)
    LazyColumn {
      items(tracks, key = { it.id }) { track ->
        TrackItem(
          track = track,
          isPlaying = track.id == currentTrackId,
          onClick = { onTrackClick(track) },
        )
        HorizontalDivider()
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Folders Tab (index 3)
// ---------------------------------------------------------------------------

private data class FolderInfo(val path: String, val displayName: String, val trackCount: Int)

@Composable
private fun FoldersContent(
  tracks: List<TrackMetadata>,
  currentTrackId: String?,
  onTrackClick: (TrackMetadata) -> Unit,
) {
  var selectedFolder by remember { mutableStateOf<String?>(null) }

  val folderMap = remember(tracks) { tracks.groupBy { File(it.filePath).parent ?: "Unknown" } }
  val folders =
    remember(folderMap) {
      folderMap
        .map { (path, folderTracks) ->
          FolderInfo(
            path = path,
            displayName = File(path).name.ifEmpty { path },
            trackCount = folderTracks.size,
          )
        }
        .sortedBy { it.displayName }
    }

  if (selectedFolder != null) {
    FolderDetailContent(
      folderPath = selectedFolder!!,
      displayName =
        folders.firstOrNull { it.path == selectedFolder }?.displayName ?: selectedFolder!!,
      tracks = folderMap[selectedFolder].orEmpty(),
      currentTrackId = currentTrackId,
      onTrackClick = onTrackClick,
      onBack = { selectedFolder = null },
    )
  } else {
    if (folders.isEmpty()) {
      EmptyContent("폴더가 없습니다")
    } else {
      LazyColumn {
        items(folders, key = { it.path }) { folder ->
          FolderListItem(folder = folder, onClick = { selectedFolder = folder.path })
          HorizontalDivider()
        }
      }
    }
  }
}

@Composable
private fun FolderListItem(folder: FolderInfo, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(56.dp)
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      modifier = Modifier.size(40.dp),
      shape = RoundedCornerShape(6.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Default.Folder,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = folder.displayName,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = folder.path,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = "${folder.trackCount}곡",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun FolderDetailContent(
  folderPath: String,
  displayName: String,
  tracks: List<TrackMetadata>,
  currentTrackId: String?,
  onTrackClick: (TrackMetadata) -> Unit,
  onBack: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    DetailHeader(title = displayName, onBack = onBack)
    LazyColumn {
      items(tracks, key = { it.id }) { track ->
        TrackItem(
          track = track,
          isPlaying = track.id == currentTrackId,
          onClick = { onTrackClick(track) },
        )
        HorizontalDivider()
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Shared Components
// ---------------------------------------------------------------------------

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBack) {
      Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
    }
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun AlbumArtPlaceholder(
  modifier: Modifier = Modifier,
  shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp),
) {
  Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceVariant) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun EmptyContent(message: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text = message, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun PlaceholderTab(message: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text = message, style = MaterialTheme.typography.bodyLarge)
  }
}

private fun formatDuration(ms: Long): String {
  val totalSeconds = ms / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}
