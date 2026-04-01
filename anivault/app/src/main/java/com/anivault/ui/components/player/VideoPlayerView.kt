package com.anivault.ui.components.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
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

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    modifier: Modifier = Modifier,
    player: ExoPlayer,
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
    onFullScreenToggle: () -> Unit,
    isFullScreen: Boolean
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Video Surface
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    this.useController = false // We're using custom controls
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Controls Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    GradientBrush.vertical(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f)
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(0f, 1f)
                    )
                )
                .clickable(onClick = onToggleControls)
        )

        // Play/Pause Button (center)
        if (!isControlsVisible) {
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Controls when visible
        if (isControlsVisible) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.End),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onFullScreenToggle) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Fullscreen",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onOpenEpisodeList) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Episode List",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Skip intro button
                    skipIntroStart?.let { start ->
                        skipIntroEnd?.let { end ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = onSkipIntro,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE50914)
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Skip Intro",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Skip Intro")
                                }
                            }
                        }
                    }

                    // Progress bar
                    SeekBar(
                        value = player.currentPosition.toFloat(),
                        onValueChange = { onSeek(it.toLong()) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Playback controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White
                            )
                        }

                        // Play/Pause
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (player.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        // Next
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // Speed
                        Text(
                            text = "${player.playbackParameters.speed}x",
                            color = Color.White,
                            fontSize = 14.sp
                        )

                        Spacer(Modifier.width(16.dp))

                        // Time
                        Text(
                            text = formatDuration(player.currentPosition) + " / " + formatDuration(player.duration),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Subtitle overlay
        subtitle?.let { cue ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
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

@OptIn(UnstableApi::class)
@Composable
fun PlayerSettingsDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    playbackSpeed: Float,
    subtitleTrack: SubtitleTrack?,
    subtitleTracks: List<SubtitleTrack>,
    onPlaybackSpeedChange: (Float) -> Unit,
    onSubtitleSelect: (SubtitleTrack?) -> Unit
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(300.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(16.dp))

                // Playback Speed
                Text(
                    text = "Playback Speed",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(0.5f, 1f, 1.5f, 2f).forEach { speed ->
                        FilterChip(
                            selected = playbackSpeed == speed,
                            onClick = { onPlaybackSpeedChange(speed) },
                            label = { Text("${speed}x", color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE50914),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Subtitles
                Text(
                    text = "Subtitles",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Column {
                    FilterChip(
                        selected = subtitleTrack == null,
                        onClick = { onSubtitleSelect(null) },
                        label = { Text("Off", color = Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE50914),
                            selectedLabelColor = Color.White
                        )
                    )

                    subtitleTracks.forEach { track ->
                        FilterChip(
                            selected = subtitleTrack?.id == track.id,
                            onClick = { onSubtitleSelect(track) },
                            label = { Text(track.label, color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE50914),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

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

@Composable
fun EpisodeListDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    episodes: List<com.anivault.data.model.Episode>,
    currentEpisode: com.anivault.data.model.Episode?,
    seriesInfo: com.anivault.data.model.Anime?,
    onEpisodeSelect: (com.anivault.data.model.Episode) -> Unit,
    progressMap: Map<String, Float>
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .heightIn(max = 600.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = seriesInfo?.title ?: "Episodes",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Divider(color = Color.Gray)

                Spacer(Modifier.height(8.dp))

                seriesInfo?.let { info ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        info.coverImage?.let { image ->
                            AsyncImage(
                                model = image,
                                contentDescription = "Cover",
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${info.totalEpisodes} Episodes",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = info.year?.toString() ?: "",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Episode list
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
                        episodes.forEach { episode ->
                            EpisodeListItem(
                                episode = episode,
                                isCurrent = episode.id == currentEpisode?.id,
                                progress = progressMap[episode.id] ?: 0f,
                                onClick = { onEpisodeSelect(episode) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListItem(
    episode: com.anivault.data.model.Episode,
    isCurrent: Boolean,
    progress: Float,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode number
        Column(
            modifier = Modifier.width(60.dp)
        ) {
            Text(
                text = "Ep. ${episode.episodeNumber}",
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) Color(0xFFE50914) else Color.White,
                fontSize = 14.sp
            )
            if (episode.isDub) {
                Text(
                    text = "DUB",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Thumbnail
        episode.thumbnail?.let { thumb ->
            AsyncImage(
                model = thumb,
                contentDescription = "Thumbnail",
                modifier = Modifier
                    .size(80.dp, 45.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title and progress
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = episode.title ?: "Episode ${episode.episodeNumber}",
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 2
            )

            if (episode.duration != null) {
                Text(
                    text = "${episode.duration}s",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Progress bar
            if (progress > 0) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .padding(top = 4.dp),
                    color = Color(0xFFE50914),
                    trackColor = Color.Gray
                )
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
        "$minutes:$secs"
    }
}

@Composable
private fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    // Placeholder for AsyncImage - in real app use coil
    Box(
        modifier = modifier.background(Color.Gray),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Image",
            color = Color.White,
            fontSize = 12.sp
        )
    }
}
