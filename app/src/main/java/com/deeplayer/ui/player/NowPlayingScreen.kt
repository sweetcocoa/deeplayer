package com.deeplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deeplayer.core.contracts.AlignmentProgress
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.PlaybackState
import com.deeplayer.feature.lyricsui.AlignmentProgressIndicator
import com.deeplayer.feature.lyricsui.SyncedLyricsView
import com.deeplayer.ui.RepeatMode

@Composable
fun NowPlayingScreen(
  playbackState: PlaybackState,
  shuffleEnabled: Boolean,
  repeatMode: RepeatMode,
  lyrics: List<LineAlignment>,
  alignmentProgress: AlignmentProgress?,
  playbackSpeed: Float,
  sleepTimerMinutes: Int,
  isFavorite: Boolean,
  onCollapse: () -> Unit,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onSeek: (Long) -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onSetPlaybackSpeed: (Float) -> Unit,
  onSetSleepTimer: (Int) -> Unit,
  onToggleFavorite: () -> Unit,
  onLyricsClick: () -> Unit,
  onQueueClick: () -> Unit,
  onStartAlignment: () -> Unit,
  onOpenEqualizer: () -> Unit,
  onShare: () -> Unit,
  albumArtUri: String?,
  modifier: Modifier = Modifier,
) {
  val track = playbackState.track
  var showLyrics by remember { mutableStateOf(false) }
  var showMenu by remember { mutableStateOf(false) }
  var showTrackInfo by remember { mutableStateOf(false) }
  var showSleepTimerDialog by remember { mutableStateOf(false) }
  var showSpeedDialog by remember { mutableStateOf(false) }

  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(
      modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Top bar
      TopBar(
        onCollapse = onCollapse,
        showMenu = showMenu,
        onMenuToggle = { showMenu = it },
        onTrackInfoClick = { showTrackInfo = true },
        onSleepTimerClick = { showSleepTimerDialog = true },
        onSpeedClick = { showSpeedDialog = true },
        onEqualizerClick = onOpenEqualizer,
        onShareClick = onShare,
        playbackSpeed = playbackSpeed,
        sleepTimerMinutes = sleepTimerMinutes,
      )

      if (showLyrics) {
        // Lyrics mode: compact header with small album art + track info
        Spacer(modifier = Modifier.height(8.dp))

        CompactTrackHeader(
          albumArtUri = albumArtUri,
          title = track?.title ?: "",
          artist = track?.artist ?: "",
          album = track?.album ?: "",
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Lyrics area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          val isInProgress =
            alignmentProgress is AlignmentProgress.Processing ||
              alignmentProgress is AlignmentProgress.PartialResult
          val isFailed = alignmentProgress is AlignmentProgress.Failed

          when {
            // Progress in progress (Processing/PartialResult)
            isInProgress -> {
              AlignmentProgressIndicator(
                progress = alignmentProgress!!,
                modifier = Modifier.align(Alignment.Center),
              )
            }
            // Failed state: error message + retry button
            isFailed -> {
              Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                AlignmentProgressIndicator(progress = alignmentProgress!!)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartAlignment) { Text("다시 시도") }
              }
            }
            // No lyrics and not processing: show placeholder
            lyrics.isEmpty() -> {
              EmptyLyricsPlaceholder(
                onStartAlignment = onStartAlignment,
                isAligning = alignmentProgress is AlignmentProgress.Processing,
              )
            }
            // Lyrics available
            else -> {
              val isSynced = lyrics.any { it.startMs > 0 || it.endMs > 0 }
              if (isSynced) {
                SyncedLyricsView(
                  lyrics = lyrics,
                  currentPositionMs = playbackState.positionMs,
                  onLineClick = { lineIndex ->
                    if (lineIndex in lyrics.indices) {
                      onSeek(lyrics[lineIndex].startMs)
                    }
                  },
                  modifier = Modifier.fillMaxSize(),
                )
              } else {
                // Unsynced lyrics: show text with "정렬하기" button
                Column(modifier = Modifier.fillMaxSize()) {
                  SyncedLyricsView(
                    lyrics = lyrics,
                    currentPositionMs = playbackState.positionMs,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                  )
                  Button(
                    onClick = onStartAlignment,
                    enabled = alignmentProgress !is AlignmentProgress.Processing,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp),
                  ) {
                    Text("정렬하기")
                  }
                }
              }
            }
          }
        }
      } else {
        // Default mode: large album art + track info
        Spacer(modifier = Modifier.height(24.dp))

        AlbumArt(albumArtUri = albumArtUri, size = 280.dp)

        Spacer(modifier = Modifier.height(32.dp))

        TrackInfo(
          title = track?.title ?: "",
          artist = track?.artist ?: "",
          album = track?.album ?: "",
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Seekbar
      SeekBar(
        positionMs = playbackState.positionMs,
        durationMs = playbackState.durationMs,
        onSeek = onSeek,
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Playback controls
      PlaybackControls(
        isPlaying = playbackState.isPlaying,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onPrevious = onPrevious,
        onToggleShuffle = onToggleShuffle,
        onCycleRepeat = onCycleRepeat,
      )

      if (!showLyrics) {
        Spacer(modifier = Modifier.weight(1f))
      } else {
        Spacer(modifier = Modifier.height(8.dp))
      }

      // Bottom actions
      BottomActions(
        showLyrics = showLyrics,
        onLyricsToggle = { showLyrics = !showLyrics },
        onQueueClick = onQueueClick,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
      )

      Spacer(modifier = Modifier.height(24.dp))
    }
  }

  // Track info dialog
  if (showTrackInfo && track != null) {
    TrackInfoDialog(
      title = track.title,
      artist = track.artist,
      album = track.album,
      filePath = track.filePath,
      durationMs = track.durationMs,
      onDismiss = { showTrackInfo = false },
    )
  }

  // Sleep timer dialog
  if (showSleepTimerDialog) {
    SleepTimerDialog(
      currentMinutes = sleepTimerMinutes,
      onSetTimer = onSetSleepTimer,
      onDismiss = { showSleepTimerDialog = false },
    )
  }

  // Playback speed dialog
  if (showSpeedDialog) {
    PlaybackSpeedDialog(
      currentSpeed = playbackSpeed,
      onSetSpeed = onSetPlaybackSpeed,
      onDismiss = { showSpeedDialog = false },
    )
  }
}

@Composable
private fun TopBar(
  onCollapse: () -> Unit,
  showMenu: Boolean,
  onMenuToggle: (Boolean) -> Unit,
  onTrackInfoClick: () -> Unit,
  onSleepTimerClick: () -> Unit,
  onSpeedClick: () -> Unit,
  onEqualizerClick: () -> Unit,
  onShareClick: () -> Unit,
  playbackSpeed: Float,
  sleepTimerMinutes: Int,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onCollapse) {
      Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "접기")
    }
    Box {
      IconButton(onClick = { onMenuToggle(true) }) {
        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "메뉴")
      }
      DropdownMenu(expanded = showMenu, onDismissRequest = { onMenuToggle(false) }) {
        DropdownMenuItem(
          text = { Text("타이머 설정") },
          onClick = {
            onMenuToggle(false)
            onSleepTimerClick()
          },
          leadingIcon = { Icon(imageVector = Icons.Default.Timer, contentDescription = null) },
          trailingIcon =
            if (sleepTimerMinutes > 0) {
              {
                Text(
                  text = "${sleepTimerMinutes}분",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            } else {
              null
            },
        )
        DropdownMenuItem(
          text = { Text("재생 속도") },
          onClick = {
            onMenuToggle(false)
            onSpeedClick()
          },
          leadingIcon = { Icon(imageVector = Icons.Default.Speed, contentDescription = null) },
          trailingIcon = {
            Text(
              text = "${playbackSpeed}x",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
        )
        DropdownMenuItem(
          text = { Text("이퀄라이저") },
          onClick = {
            onMenuToggle(false)
            onEqualizerClick()
          },
          leadingIcon = { Icon(imageVector = Icons.Default.Equalizer, contentDescription = null) },
        )
        DropdownMenuItem(
          text = { Text("곡 정보") },
          onClick = {
            onMenuToggle(false)
            onTrackInfoClick()
          },
          leadingIcon = { Icon(imageVector = Icons.Default.Info, contentDescription = null) },
        )
        DropdownMenuItem(
          text = { Text("공유") },
          onClick = {
            onMenuToggle(false)
            onShareClick()
          },
          leadingIcon = { Icon(imageVector = Icons.Default.Share, contentDescription = null) },
        )
      }
    }
  }
}

@Composable
private fun AlbumArt(albumArtUri: String?, size: Dp) {
  Box(
    modifier =
      Modifier.size(size)
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    if (albumArtUri != null) {
      AsyncImage(
        model = albumArtUri,
        contentDescription = "앨범 아트",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    } else {
      Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        modifier = Modifier.size(size / 4),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun CompactTrackHeader(albumArtUri: String?, title: String, artist: String, album: String) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    AlbumArt(albumArtUri = albumArtUri, size = 48.dp)

    Spacer(modifier = Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val subtitle = if (album.isNotBlank()) "$artist \u2014 $album" else artist
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun EmptyLyricsPlaceholder(
  onStartAlignment: () -> Unit,
  isAligning: Boolean = false,
) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "가사가 없습니다",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(onClick = onStartAlignment, enabled = !isAligning) { Text("가사 정렬 시작") }
    }
  }
}

@Composable
private fun TrackInfo(title: String, artist: String, album: String) {
  Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(4.dp))
    val subtitle = if (album.isNotBlank()) "$artist \u2014 $album" else artist
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
  val sliderValue = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

  Column(modifier = Modifier.fillMaxWidth()) {
    Slider(
      value = sliderValue,
      onValueChange = { fraction -> onSeek((fraction * durationMs).toLong()) },
      modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        text = formatDuration(positionMs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = formatDuration(durationMs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun PlaybackControls(
  isPlaying: Boolean,
  shuffleEnabled: Boolean,
  repeatMode: RepeatMode,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Shuffle
    IconButton(onClick = onToggleShuffle) {
      Icon(
        imageVector = Icons.Default.Shuffle,
        contentDescription = "셔플",
        tint =
          if (shuffleEnabled) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurface,
      )
    }

    // Previous
    IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
      Icon(
        imageVector = Icons.Default.SkipPrevious,
        contentDescription = "이전 곡",
        modifier = Modifier.size(32.dp),
      )
    }

    // Play / Pause
    FilledIconButton(
      onClick = onPlayPause,
      modifier = Modifier.size(64.dp),
      colors =
        IconButtonDefaults.filledIconButtonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
      Icon(
        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = if (isPlaying) "일시정지" else "재생",
        modifier = Modifier.size(36.dp),
      )
    }

    // Next
    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
      Icon(
        imageVector = Icons.Default.SkipNext,
        contentDescription = "다음 곡",
        modifier = Modifier.size(32.dp),
      )
    }

    // Repeat
    IconButton(onClick = onCycleRepeat) {
      Icon(
        imageVector =
          if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
        contentDescription = "반복",
        tint =
          if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun BottomActions(
  showLyrics: Boolean,
  onLyricsToggle: () -> Unit,
  onQueueClick: () -> Unit,
  isFavorite: Boolean,
  onToggleFavorite: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Favorite
    IconButton(onClick = onToggleFavorite) {
      Icon(
        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = "좋아요",
        tint =
          if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
      )
    }

    // Lyrics (toggle, highlighted when active)
    IconButton(onClick = onLyricsToggle) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.Article,
        contentDescription = "가사",
        tint =
          if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      )
    }

    // Queue
    IconButton(onClick = onQueueClick) {
      Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "재생 대기열")
    }
  }
}

@Composable
private fun TrackInfoDialog(
  title: String,
  artist: String,
  album: String,
  filePath: String,
  durationMs: Long,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("곡 정보") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TrackInfoRow(label = "제목", value = title)
        TrackInfoRow(label = "아티스트", value = artist)
        TrackInfoRow(label = "앨범", value = album)
        TrackInfoRow(label = "경로", value = filePath)
        TrackInfoRow(label = "재생 시간", value = formatDuration(durationMs))
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
  )
}

@Composable
private fun TrackInfoRow(label: String, value: String) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value.ifBlank { "-" },
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun SleepTimerDialog(
  currentMinutes: Int,
  onSetTimer: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("슬립 타이머") },
    text = {
      Column {
        listOf(0 to "끄기", 15 to "15분", 30 to "30분", 45 to "45분", 60 to "60분").forEach {
          (minutes, label) ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  onSetTimer(minutes)
                  onDismiss()
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = currentMinutes == minutes,
              onClick = {
                onSetTimer(minutes)
                onDismiss()
              },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
  )
}

@Composable
private fun PlaybackSpeedDialog(
  currentSpeed: Float,
  onSetSpeed: (Float) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("재생 속도") },
    text = {
      Column {
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  onSetSpeed(speed)
                  onDismiss()
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = currentSpeed == speed,
              onClick = {
                onSetSpeed(speed)
                onDismiss()
              },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("${speed}x")
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
  )
}

private fun formatDuration(ms: Long): String {
  val totalSeconds = ms / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}
