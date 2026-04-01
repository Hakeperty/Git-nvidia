package com.anivault.ui.screens.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.anivault.data.model.Anime
import com.anivault.data.model.Episode
import com.anivault.domain.player.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun EpisodePlayerScreen(
    initialEpisode: Episode,
    seriesInfo: Anime?,
    nextEpisodes: List<Episode>,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalTime by remember { mutableStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showEpisodeList by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var showAutoPlayDialog by remember { mutableStateOf(false) }

    // State from ViewModel
    val playerState by viewModel.state.collectAsState()
    val subtitle by remember { derivedStateOf { viewModel.getCurrentSubtitle() } }

    // Initialize player
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            isPlaying = true
                            totalTime = duration
                        }
                        Player.STATE_ENDED -> {
                            isPlaying = false
                        }
                        Player.STATE_BUFFERING -> {
                            // Handle buffering
                        }
                        Player.STATE_IDLE -> {
                            isPlaying = false
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    this@EpisodePlayerScreen.isPlaying = isPlaying
                }
            })
        }
    }

    // Set initial episode
    LaunchedEffect(initialEpisode.id) {
        viewModel.setEpisode(initialEpisode, seriesInfo, nextEpisodes)

        // Load media
        player.setMediaItem(
            MediaItem.fromUri(
                playerState.currentEpisode?.streamUrl ?: ""
            )
        )
        player.prepare()
        player.play()

        // Set fullscreen
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Update state from player
    LaunchedEffect(playerState.currentEpisode?.id) {
        playerState.currentEpisode?.streamUrl?.let { url ->
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    // Auto-play next episode
    LaunchedEffect(playerState.isCompleted) {
        if (playerState.isCompleted && playerState.autoPlayNext) {
            delay(2000)
            showAutoPlayDialog = true
        }
    }

    // Controls visibility timer
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(3000)
            isControlsVisible = false
        }
    }

    // Set skip intro timestamps if available
    LaunchedEffect(playerState.currentEpisode?.id) {
        // These would come from metadata or user configuration
        playerState.currentEpisode?.let { episode ->
            // Example: skip intro from 0-90 seconds
            viewModel.setSkipIntro(0, 90000)
        }
    }

    // Handle player events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayerEvent.Play -> player.play()
                is PlayerEvent.Pause -> player.pause()
                is PlayerEvent.Seek -> player.seekTo(event.position)
                is PlayerEvent.SetPlaybackSpeed -> player.playbackParameters = PlaybackParameters(event.speed)
                is PlayerEvent.SelectSubtitle -> {
                    // Handle subtitle selection
                }
                is PlayerEvent.PlayNext -> {
                    viewModel.handleEvent(PlayerEvent.PlayNext)
                }
                is PlayerEvent.EpisodeComplete -> {
                    showAutoPlayDialog = true
                }
                else -> {}
            }
        }
    }

    // Update position
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            currentPosition = player.currentPosition
            totalTime = player.duration
            isPlaying = player.isPlaying
        }
    }

    // Set initial values
    LaunchedEffect(playerState.currentPosition, playerState.totalTime) {
        if (currentPosition == 0L && playerState.currentPosition > 0L) {
            player.seekTo(playerState.currentPosition)
        }
    }

    // Fullscreen toggle
    fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        if (isFullScreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Progress update
    val progress = remember { mutableStateOf(0f) }
    LaunchedEffect(currentPosition, totalTime) {
        progress.value = if (totalTime > 0) currentPosition.toFloat() / totalTime else 0f
    }

    // Save progress periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            viewModel.handleEvent(PlayerEvent.SaveProgress(currentPosition, totalTime))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Video Player
            VideoPlayer(
                player = player,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                totalTime = totalTime,
                isControlsVisible = isControlsVisible,
                subtitle = subtitle,
                skipIntroStart = playerState.skipIntroStart,
                skipIntroEnd = playerState.skipIntroEnd,
                onPlayPause = {
                    isPlaying = !isPlaying
                    viewModel.handleEvent(
                        if (isPlaying) PlayerEvent.Play(playerState.currentEpisode!!)
                        else PlayerEvent.Pause()
                    )
                },
                onSeek = { position ->
                    player.seekTo(position)
                    currentPosition = position
                },
                onToggleControls = {
                    isControlsVisible = !isControlsVisible
                },
                onSkipIntro = {
                    player.seekTo(playerState.skipIntroEnd ?: 0)
                },
                onOpenSettings = { showSettings = true },
                onOpenEpisodeList = { showEpisodeList = true },
                onFullScreenToggle = { toggleFullScreen() }
            )

            // Loading indicator
            if (playerState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFE50914)
                )
            }

            // Error message
            playerState.error?.let { error ->
                AlertDialog(
                    onDismissRequest = { viewModel.handleEvent(PlayerEvent.Error(null)) },
                    title = { Text("Error") },
                    text = { Text(error) },
                    confirmButton = {
                        Button(onClick = { viewModel.handleEvent(PlayerEvent.Error(null)) }) {
                            Text("Dismiss")
                        }
                    }
                )
            }
        }
    }

    // Settings Dialog
    if (showSettings) {
        PlayerSettingsDialog(
            isOpen = showSettings,
            onClose = { showSettings = false },
            playbackSpeed = playerState.playbackSpeed,
            subtitleTrack = playerState.selectedSubtitleTrack,
            subtitleTracks = playerState.subtitleTracks,
            onPlaybackSpeedChange = { speed ->
                viewModel.handleEvent(PlayerEvent.SetPlaybackSpeed(speed))
            },
            onSubtitleSelect = { track ->
                viewModel.handleEvent(PlayerEvent.SelectSubtitle(track))
            }
        )
    }

    // Episode List Dialog
    if (showEpisodeList) {
        EpisodeListDialog(
            isOpen = showEpisodeList,
            onClose = { showEpisodeList = false },
            episodes = nextEpisodes,
            currentEpisode = playerState.currentEpisode,
            seriesInfo = playerState.seriesInfo,
            onEpisodeSelect = { episode ->
                viewModel.setEpisode(episode, seriesInfo, nextEpisodes.filter { it.id != episode.id })
                showEpisodeList = false
            },
            progressMap = emptyMap()
        )
    }

    // Auto-play next episode dialog
    if (showAutoPlayDialog) {
        AutoPlayDialog(
            onNext = {
                viewModel.handleEvent(PlayerEvent.PlayNext)
                showAutoPlayDialog = false
            },
            onSkip = {
                showAutoPlayDialog = false
            }
        )
    }
}

@Composable
private fun VideoPlayer(
    player: androidx.media3.common.Player,
    isPlaying: Boolean,
    currentPosition: Long,
    totalTime: Long,
    isControlsVisible: Boolean,
    subtitle: SubtitleCue?,
    skipIntroStart: Long?,
    skipIntroEnd: Long?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onSkipIntro: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEpisodeList: () -> Unit,
    onFullScreenToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video surface
        androidx.media3.ui.PlayerView(
            context = LocalContext.current,
            modifier = Modifier.fillMaxSize(),
            player = player,
            useController = false
        )

        // Controls overlay
        if (isControlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f)
                            )
                        )
                    )
                    .clickable(onClick = onToggleControls)
                    .padding(16.dp)
            ) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onFullScreenToggle) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                    }
                    IconButton(onClick = onOpenEpisodeList) {
                        Icon(Icons.Default.List, contentDescription = "Episodes", tint = Color.White)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }

                Spacer(Modifier.weight(1f))

                // Bottom controls
                Column {
                    // Skip intro button
                    if (skipIntroStart != null && skipIntroEnd != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = onSkipIntro,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Skip Intro", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Skip Intro")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Progress bar
                    SeekBar(
                        value = currentPosition.toFloat(),
                        onValueChange = onSeek,
                        valueRange = 0f..totalTime.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Playback controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* Previous */ }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                        }

                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(onClick = { /* Next */ }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = "${formatDuration(currentPosition)} / ${formatDuration(totalTime)}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            // Center play button when controls hidden
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Subtitle overlay
        subtitle?.let { cue ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(
                    text = cue.text,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun AutoPlayDialog(
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    var countdown by remember { mutableStateOf(5) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = { /* Do nothing */ },
        title = { Text("Episode Complete") },
        text = {
            Column {
                Text("Starting next episode in $countdown...")
            }
        },
        confirmButton = {
            Button(onClick = {
                onNext()
            }) {
                Text("Play Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDuration(ms: Long): String {
    if (ms < 0) return "0:00"
    val seconds = ms / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    } else {
        "$minutes:$secs"
    }
}
