package com.anivault.domain.player

data class PlayerState(
    val currentEpisode: com.anivault.data.model.Episode? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val totalTime: Long = 0L,
    val playbackSpeed: Float = 1f,
    val volume: Float = 1f,
    val brightness: Float = 1f,
    val isControlsVisible: Boolean = true,
    val selectedSubtitleTrack: SubtitleTrack? = null,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val skipIntroStart: Long? = null,
    val skipIntroEnd: Long? = null,
    val autoPlayNext: Boolean = true,
    val upNextEpisodes: List<com.anivault.data.model.Episode> = emptyList(),
    val seriesInfo: com.anivault.data.model.Anime? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val progressPercentage: Float
        get() = if (totalTime > 0) (currentPosition.toFloat() / totalTime * 100) else 0f

    val isCompleted: Boolean
        get() = currentPosition >= totalTime - 10000 // Consider complete if within 10 seconds

    val remainingTime: Long
        get() = totalTime - currentPosition
}

data class EpisodeQueueItem(
    val episode: com.anivault.data.model.Episode,
    val isCurrent: Boolean = false,
    val isPlayed: Boolean = false,
    val progress: Float = 0f
)
