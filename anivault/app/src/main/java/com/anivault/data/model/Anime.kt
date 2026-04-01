package com.anivault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "anime")
data class Anime(
    @PrimaryKey val id: String,
    val title: String,
    val originalTitle: String? = null,
    val description: String? = null,
    val coverImage: String? = null,
    val bannerImage: String? = null,
    val sourceUrl: String,
    val sourceSite: String,
    val genres: List<String> = emptyList(),
    val status: AnimeStatus = AnimeStatus.UNKNOWN,
    val year: Int? = null,
    val totalEpisodes: Int = 0,
    val hasDub: Boolean = false,
    val hasSub: Boolean = true,
    val defaultLanguage: String = "sub",
    val addedDate: Long = System.currentTimeMillis(),
    val lastWatchedDate: Long? = null,
    val currentEpisode: Int = 0,
    val totalWatched: Int = 0
)

enum class AnimeStatus {
    UNKNOWN,
    AIRING,
    COMPLETED,
    CANCELLED,
    POSTPONED
}
