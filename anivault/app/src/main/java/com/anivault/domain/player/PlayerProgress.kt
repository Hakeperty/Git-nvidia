package com.anivault.domain.player

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "player_progress",
    foreignKeys = [
        ForeignKey(
            entity = com.anivault.data.model.Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("episodeId")]
)
data class PlayerProgress(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val episodeId: String,
    val currentPosition: Long = 0L, // in milliseconds
    val totalTime: Long = 0L, // in milliseconds
    val playbackSpeed: Float = 1f,
    val isPlaying: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val completed: Boolean = false
)

data class PlaybackState(
    val episodeId: String,
    val currentPosition: Long,
    val totalTime: Long,
    val playbackSpeed: Float,
    val isPlaying: Boolean,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val selectedSubtitleTrack: SubtitleTrack? = null,
    val skipIntroStart: Long? = null,
    val skipIntroEnd: Long? = null
)

data class SubtitleTrack(
    val id: String,
    val label: String,
    val language: String,
    val url: String? = null,
    val content: String? = null,
    val format: SubtitleFormat = SubtitleFormat.UNKNOWN
)

enum class SubtitleFormat {
    VTT,
    SRT,
    UNKNOWN
}
