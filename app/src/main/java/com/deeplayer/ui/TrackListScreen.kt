package com.deeplayer.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
fun TrackListScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
  val tracks by viewModel.tracks.collectAsState()
  val playbackState by viewModel.playbackState.collectAsState()
  var permissionGranted by remember { mutableStateOf(false) }

  val permission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Manifest.permission.READ_MEDIA_AUDIO
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }

  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      permissionGranted = granted
      if (granted) viewModel.loadTracks()
    }

  LaunchedEffect(Unit) { launcher.launch(permission) }

  Column(modifier = modifier.fillMaxSize()) {
    if (!permissionGranted && tracks.isEmpty()) {
      PermissionRequest(onRequest = { launcher.launch(permission) })
    } else {
      TrackList(
        tracks = tracks,
        currentTrackId = playbackState.track?.id,
        onTrackClick = { viewModel.play(it.id) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text("음악 파일에 접근하려면 권한이 필요합니다", style = MaterialTheme.typography.bodyLarge)
      Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) { Text("권한 허용") }
    }
  }
}

@Composable
private fun TrackList(
  tracks: List<TrackMetadata>,
  currentTrackId: String?,
  onTrackClick: (TrackMetadata) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (tracks.isEmpty()) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("음악 파일이 없습니다", style = MaterialTheme.typography.bodyLarge)
    }
  } else {
    LazyColumn(modifier = modifier) {
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

@Composable
private fun TrackItem(track: TrackMetadata, isPlaying: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
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
