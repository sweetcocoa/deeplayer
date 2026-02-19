package com.deeplayer.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deeplayer.ui.MainViewModel
import com.deeplayer.ui.library.LibraryScreen
import com.deeplayer.ui.player.MiniPlayerBar
import com.deeplayer.ui.player.NowPlayingScreen
import com.deeplayer.ui.search.SearchScreen
import com.deeplayer.ui.settings.SettingsScreen

enum class TopLevelDestination(val route: String, val icon: ImageVector, val label: String) {
  LIBRARY(route = "library", icon = Icons.Default.LibraryMusic, label = "라이브러리"),
  SEARCH(route = "search", icon = Icons.Default.Search, label = "검색"),
  SETTINGS(route = "settings", icon = Icons.Default.Settings, label = "설정"),
}

@Composable
fun DeeplaterApp(viewModel: MainViewModel) {
  val navController = rememberNavController()
  val playbackState by viewModel.playbackState.collectAsState()
  val tracks by viewModel.tracks.collectAsState()
  val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
  val repeatMode by viewModel.repeatMode.collectAsState()
  val lyrics by viewModel.currentLyrics.collectAsState()
  val alignmentProgress by viewModel.alignmentProgress.collectAsState()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  val context = LocalContext.current
  var showNowPlaying by remember { mutableStateOf(false) }
  val playbackSpeed by viewModel.playbackSpeed.collectAsState()
  val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
  val favorites by viewModel.favorites.collectAsState()

  // Permission handling
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

  Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      bottomBar = {
        Column {
          if (playbackState.track != null) {
            MiniPlayerBar(
              state = playbackState,
              onPlayPause = {
                if (playbackState.isPlaying) viewModel.pause() else viewModel.resume()
              },
              onNext = { viewModel.next() },
              onClick = { showNowPlaying = true },
            )
          }
          NavigationBar {
            TopLevelDestination.entries.forEach { destination ->
              val selected =
                currentDestination?.hierarchy?.any { it.route == destination.route } == true
              NavigationBarItem(
                selected = selected,
                onClick = {
                  navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(imageVector = destination.icon, contentDescription = destination.label)
                },
                label = { Text(destination.label) },
              )
            }
          }
        }
      },
    ) { innerPadding ->
      NavHost(
        navController = navController,
        startDestination = TopLevelDestination.LIBRARY.route,
        modifier = Modifier.padding(innerPadding),
      ) {
        composable(TopLevelDestination.LIBRARY.route) {
          LibraryScreen(
            tracks = tracks,
            currentTrackId = playbackState.track?.id,
            onTrackClick = { viewModel.play(it.id) },
            onPermissionRequest = { launcher.launch(permission) },
            permissionGranted = permissionGranted,
          )
        }
        composable(TopLevelDestination.SEARCH.route) {
          SearchScreen(
            tracks = tracks,
            currentTrackId = playbackState.track?.id,
            onTrackClick = { viewModel.play(it.id) },
          )
        }
        composable(TopLevelDestination.SETTINGS.route) {
          val availableFolders by viewModel.availableFolders.collectAsState()
          val selectedFolders by viewModel.selectedFolders.collectAsState()
          SettingsScreen(
            availableFolders = availableFolders,
            selectedFolders = selectedFolders,
            onUpdateSelectedFolders = { viewModel.updateSelectedFolders(it) },
          )
        }
      }
    }

    // Now Playing overlay
    AnimatedVisibility(
      visible = showNowPlaying,
      enter = slideInVertically(initialOffsetY = { it }),
      exit = slideOutVertically(targetOffsetY = { it }),
    ) {
      NowPlayingScreen(
        playbackState = playbackState,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        lyrics = lyrics,
        alignmentProgress = alignmentProgress,
        playbackSpeed = playbackSpeed,
        sleepTimerMinutes = sleepTimerMinutes,
        isFavorite = playbackState.track?.id?.let { favorites.contains(it) } ?: false,
        onCollapse = { showNowPlaying = false },
        onPlayPause = { if (playbackState.isPlaying) viewModel.pause() else viewModel.resume() },
        onNext = { viewModel.next() },
        onPrevious = { viewModel.previous() },
        onSeek = { viewModel.seekTo(it) },
        onToggleShuffle = { viewModel.toggleShuffle() },
        onCycleRepeat = { viewModel.cycleRepeatMode() },
        onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
        onSetSleepTimer = { viewModel.setSleepTimer(it) },
        onToggleFavorite = { playbackState.track?.id?.let { viewModel.toggleFavorite(it) } },
        onLyricsClick = {},
        onQueueClick = { /* TODO: queue view */ },
        onStartAlignment = { viewModel.startAlignment() },
        albumArtUri = playbackState.track?.albumArtUri,
        onOpenEqualizer = {
          try {
            val intent =
              android.content.Intent(
                android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL
              )
            intent.putExtra(
              android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME,
              context.packageName,
            )
            context.startActivity(intent)
          } catch (e: Exception) {
            // No equalizer app installed
          }
        },
        onShare = {
          playbackState.track?.let { track ->
            val shareIntent =
              android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "${track.title} - ${track.artist}")
              }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "공유"))
          }
        },
      )
    }
  }
}
