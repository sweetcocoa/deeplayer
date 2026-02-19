package com.deeplayer.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deeplayer.core.contracts.TrackMetadata

@Composable
fun SearchScreen(
  tracks: List<TrackMetadata>,
  currentTrackId: String?,
  onTrackClick: (TrackMetadata) -> Unit,
  modifier: Modifier = Modifier,
) {
  var query by remember { mutableStateOf("") }

  val filteredTracks =
    remember(query, tracks) {
      if (query.isBlank()) {
        emptyList()
      } else {
        tracks.filter { track ->
          track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true) ||
            track.album.contains(query, ignoreCase = true)
        }
      }
    }

  Column(modifier = modifier.fillMaxSize()) {
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      placeholder = { Text("곡, 아티스트, 앨범 검색...") },
      leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "검색") },
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = { query = "" }) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "지우기")
          }
        }
      },
      singleLine = true,
    )

    when {
      query.isBlank() -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = "검색어를 입력하세요",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      filteredTracks.isEmpty() -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = "검색 결과가 없습니다",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      else -> {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          item {
            Text(
              text = "곡 (${filteredTracks.size})",
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
          }
          items(filteredTracks, key = { it.id }) { track ->
            SearchResultItem(
              track = track,
              isPlaying = track.id == currentTrackId,
              onClick = { onTrackClick(track) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SearchResultItem(track: TrackMetadata, isPlaying: Boolean, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Default.MusicNote,
      contentDescription = null,
      modifier = Modifier.size(24.dp),
      tint =
        if (isPlaying) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(12.dp))
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
        text = track.artist,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = formatDuration(track.durationMs),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun formatDuration(ms: Long): String {
  val totalSeconds = ms / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}
