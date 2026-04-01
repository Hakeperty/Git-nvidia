package com.anivault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey val id: String,
    val itemId: String,
    val itemType: DownloadItemType,
    val title: String,
    val coverImage: String? = null,
    val quality: String = "720p",
    val size: Long = 0,
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val queuedAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null
)

enum class DownloadItemType {
    EPISODE,
    CHAPTER
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
