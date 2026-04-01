package com.anivault.data.database

import androidx.room.*
import com.anivault.domain.player.PlayerProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerProgressDao {
    @Query("SELECT * FROM player_progress WHERE episodeId = :episodeId")
    fun getByEpisodeId(episodeId: String): Flow<PlayerProgress?>

    @Query("SELECT * FROM player_progress WHERE episodeId = :episodeId")
    suspend fun getByEpisodeIdSync(episodeId: String): PlayerProgress?

    @Query("SELECT * FROM player_progress WHERE episodeId IN (:episodeIds)")
    suspend fun getByEpisodeIds(episodeIds: List<String>): List<PlayerProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: PlayerProgress)

    @Update
    suspend fun update(progress: PlayerProgress)

    @Delete
    suspend fun delete(progress: PlayerProgress)

    @Query("DELETE FROM player_progress WHERE episodeId = :episodeId")
    suspend fun deleteByEpisodeId(episodeId: String)

    @Query("UPDATE player_progress SET currentPosition = :position, totalTime = :total, playbackSpeed = :speed, isPlaying = :playing, lastUpdated = :timestamp, completed = :completed WHERE episodeId = :episodeId")
    suspend fun updateProgress(
        episodeId: String,
        position: Long,
        total: Long,
        speed: Float,
        playing: Boolean,
        timestamp: Long = System.currentTimeMillis(),
        completed: Boolean = false
    )

    @Query("SELECT * FROM player_progress WHERE completed = 0 ORDER BY lastUpdated DESC")
    fun getInProgress(): Flow<List<PlayerProgress>>
}
