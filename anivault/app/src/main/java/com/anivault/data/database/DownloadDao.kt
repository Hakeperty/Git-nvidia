package com.anivault.data.database

import androidx.room.*
import com.anivault.data.model.Download
import com.anivault.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads")
    fun getAll(): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): Download?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: Download)

    @Update
    suspend fun update(download: Download)

    @Delete
    suspend fun delete(download: Download)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM downloads WHERE status = :status")
    fun getByStatus(status: DownloadStatus): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses)")
    fun getByStatuses(statuses: List<DownloadStatus>): Flow<List<Download>>

    @Query("UPDATE downloads SET status = :status, progress = :progress, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, progress: Float, error: String? = null)

    @Query("UPDATE downloads SET startedAt = :timestamp WHERE id = :id")
    suspend fun setStarted(id: String, timestamp: Long)

    @Query("UPDATE downloads SET completedAt = :timestamp WHERE id = :id")
    suspend fun setCompleted(id: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM downloads WHERE status = :status")
    suspend fun getCountByStatus(status: DownloadStatus): Int

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED' AND completedAt < :timestamp")
    suspend fun deleteOldCompleted(timestamp: Long): Int
}
