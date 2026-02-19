package com.deeplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deeplayer.core.contracts.PlaybackState

@Composable
fun MiniPlayerBar(
  state: PlaybackState,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val track = state.track ?: return
  val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

  Surface(tonalElevation = 3.dp, modifier = modifier) {
    Column {
      LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (track.albumArtUri != null) {
          AsyncImage(
            model = track.albumArtUri,
            contentDescription = "앨범 아트",
            modifier = Modifier.size(40.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
          )
        } else {
          Box(
            modifier =
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.MusicNote,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(24.dp),
            )
          }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
          Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(onClick = onPlayPause) {
          Icon(
            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (state.isPlaying) "일시정지" else "재생",
            modifier = Modifier.size(32.dp),
          )
        }
        IconButton(onClick = onNext) {
          Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = "다음 곡",
            modifier = Modifier.size(32.dp),
          )
        }
      }
    }
  }
}
