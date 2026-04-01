package com.anivault.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = Manga::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mangaId")]
)
data class Chapter(
    @PrimaryKey val id: String,
    val mangaId: String,
    val chapterNumber: Float,
    val title: String? = null,
    val volumeNumber: Int? = null,
    val pageCount: Int = 0,
    val releaseDate: Long? = null,
    val scanlator: String? = null,
    val status: ChapterStatus = ChapterStatus.UNREAD,
    val lastReadPage: Int = 0,
    val downloadPath: String? = null,
    val isDownloaded: Boolean = false,
    val pageUrls: List<String> = emptyList()
)

enum class ChapterStatus {
    UNREAD,
    IN_PROGRESS,
    READ
}
