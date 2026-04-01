package com.anivault.data.database

import androidx.room.*
import com.anivault.data.model.Episode
import com.anivault.data.model.EpisodeStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE animeId = :animeId ORDER BY seasonNumber, episodeNumber")
    fun getByAnimeId(animeId: String): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: String): Episode?

    @Query("SELECT * FROM episodes WHERE animeId = :animeId AND episodeNumber = :episodeNum")
    suspend fun getEpisode(animeId: String, episodeNum: Int): Episode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: Episode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<Episode>)

    @Update
    suspend fun update(episode: Episode)

    @Delete
    suspend fun delete(episode: Episode)

    @Query("DELETE FROM episodes WHERE animeId = :animeId")
    suspend fun deleteByAnimeId(animeId: String)

    @Query("UPDATE episodes SET status = :status, watchedProgress = :progress, lastWatchedAt = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: String, status: EpisodeStatus, progress: Float, timestamp: Long)

    @Query("SELECT * FROM episodes WHERE isDownloaded = 1")
    fun getDownloaded(): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE status = :status ORDER BY lastWatchedAt DESC")
    fun getByStatus(status: EpisodeStatus): Flow<List<Episode>>

    @Query("SELECT COUNT(*) FROM episodes WHERE animeId = :animeId AND isDownloaded = 1")
    suspend fun getDownloadedCount(animeId: String): Int

    @Query("SELECT SUM(size) FROM downloads WHERE itemId IN (SELECT id FROM episodes WHERE animeId = :animeId)")
    suspend fun getTotalSize(animeId: String): Long?
}
