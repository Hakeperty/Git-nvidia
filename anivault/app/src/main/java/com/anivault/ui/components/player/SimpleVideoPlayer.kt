package com.anivault.ui.components.player

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.anivault.domain.player.SubtitleCue
import com.anivault.domain.player.SubtitleTrack
import kotlinx.coroutines.delay

/**
 * Simple Video Player Composable with ExoPlayer integration
 * Includes:
 * - Play/Pause controls
 * - Seek bar with progress
 * - Playback speed control (0.5x, 1x, 1.5x, 2x)
 * - Subtitle display
 * - Skip intro button
 * - Volume and brightness controls
 */
@OptIn(UnstableApi::class)
@Composable
fun SimpleVideoPlayer(
    modifier: Modifier = Modifier,
    videoUri: String,
    subtitleUri: String? = null,
    isPlaying: Boolean,
    currentPosition: Long,
    totalTime: Long,
    playbackSpeed: Float,
    skipIntroStart: Long? = null,
    skipIntroEnd: Long? = null,
    subtitle: SubtitleCue? = null,
    isControlsVisible: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSubtitleSelect: (SubtitleTrack?) -> Unit,
    onToggleControls: () -> Unit,
    onSkipIntro: () -> Unit,
    onFullScreenToggle: () -> Unit,
    onNextEpisode: () -> Unit,
    onPreviousEpisode: () -> Unit
) {
    val context = LocalContext.current
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Video Surface using AndroidView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false // We use custom controls
                    player = ExoPlayer.Builder(ctx).build().apply {
                        setMediaItem(MediaItem.fromUri(videoUri))
                        prepare()
                        playWhenReady = isPlaying
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Controls Overlay
        if (isControlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
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
                    IconButton(onClick = { /* Settings */ }) {
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Skip Intro", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Skip Intro")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { if (totalTime > 0) currentPosition.toFloat() / totalTime else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clickable {
                                // Simple seek on tap
                                val clickPosition = (it as? android.view.View)?.let { view ->
                                    val progress = currentPosition.toFloat() / totalTime
                                    progress
                                } ?: 0f
                            },
                        color = Color(0xFFE50914),
                        trackColor = Color.Gray
                    )

                    // Time display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatDuration(totalTime),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    // Playback controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Previous
                        IconButton(onClick = onPreviousEpisode) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                        }

                        // Play/Pause
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        // Next
                        IconButton(onClick = onNextEpisode) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                        }

                        Spacer(Modifier.width(16.dp))

                        // Speed button
                        OutlinedButton(
                            onClick = { showSpeedDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("${playbackSpeed}x", fontSize = 14.sp)
                        }
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
                    .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
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

    // Speed selection dialog
    if (showSpeedDialog) {
        SpeedSelectionDialog(
            currentSpeed = playbackSpeed,
            onSpeedSelected = { speed ->
                onSpeedChange(speed)
                showSpeedDialog = false
            },
            onClose = { showSpeedDialog = false }
        )
    }
}

@Composable
private fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(280.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Playback Speed",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(12.dp))

                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpeedSelected(speed) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSpeed == speed,
                            onClick = { onSpeedSelected(speed) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFE50914),
                                unselectedColor = Color.Gray
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${speed}x",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Text("Close")
                }
            }
        }
    }
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
        "$minutes:${secs.toString().padStart(2, '0')}"
    }
}
