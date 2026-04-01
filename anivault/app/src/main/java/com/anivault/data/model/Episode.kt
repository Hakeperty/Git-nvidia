package com.anivault.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Anime::class,
            parentColumns = ["id"],
            childColumns = ["animeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("animeId")]
)
data class Episode(
    @PrimaryKey val id: String,
    val animeId: String,
    val episodeNumber: Int,
    val seasonNumber: Int = 1,
    val title: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null,
    val releaseDate: Long? = null,
    val streamUrl: String? = null,
    val subtitleUrl: String? = null,
    val isDub: Boolean = false,
    val quality: String = "720p",
    val status: EpisodeStatus = EpisodeStatus.UNWATCHED,
    val watchedProgress: Float = 0f,
    val lastWatchedAt: Long? = null,
    val downloadPath: String? = null,
    val isDownloaded: Boolean = false
)

enum class EpisodeStatus {
    UNWATCHED,
    IN_PROGRESS,
    WATCHED
}
