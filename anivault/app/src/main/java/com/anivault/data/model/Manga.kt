package com.anivault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manga")
data class Manga(
    @PrimaryKey val id: String,
    val title: String,
    val originalTitle: String? = null,
    val description: String? = null,
    val coverImage: String? = null,
    val bannerImage: String? = null,
    val sourceUrl: String,
    val sourceSite: String,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val author: String? = null,
    val artist: String? = null,
    val year: Int? = null,
    val totalChapters: Int = 0,
    val addedDate: Long = System.currentTimeMillis(),
    val lastReadDate: Long? = null,
    val currentChapter: Int = 0,
    val totalRead: Int = 0,
    val readingMode: ReadingMode = ReadingMode.PAGINATED
)

enum class MangaStatus {
    UNKNOWN,
    ONGOING,
    COMPLETED,
    HIATUS,
    CANCELLED
}

enum class ReadingMode {
    PAGINATED,
    VERTICAL_SCROLL,
    RIGHT_TO_LEFT
}
