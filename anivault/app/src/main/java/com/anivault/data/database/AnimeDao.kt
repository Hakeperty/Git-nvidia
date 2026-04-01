package com.anivault.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.anivault.data.model.Anime
import com.anivault.data.model.AnimeStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    @Query("SELECT * FROM anime")
    fun getAll(): Flow<List<Anime>>

    @Query("SELECT * FROM anime WHERE id = :id")
    suspend fun getById(id: String): Anime?

    @Query("SELECT * FROM anime WHERE id = :id")
    fun getByIdLive(id: String): LiveData<Anime?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anime: Anime)

    @Update
    suspend fun update(anime: Anime)

    @Delete
    suspend fun delete(anime: Anime)

    @Query("DELETE FROM anime WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM anime WHERE title LIKE '%' || :query || '%'")
    fun searchByTitle(query: String): Flow<List<Anime>>

    @Query("SELECT * FROM anime WHERE status IN (:statuses)")
    fun getByStatus(statuses: List<AnimeStatus>): Flow<List<Anime>>

    @Query("SELECT * FROM anime ORDER BY addedDate DESC")
    fun getSortedByAddedDate(): Flow<List<Anime>>

    @Query("SELECT * FROM anime ORDER BY title ASC")
    fun getSortedByTitle(): Flow<List<Anime>>

    @Query("SELECT * FROM anime WHERE genres LIKE '%' || :genre || '%'")
    fun getByGenre(genre: String): Flow<List<Anime>>

    @Query("SELECT * FROM anime WHERE currentEpisode > 0 ORDER BY lastWatchedDate DESC")
    fun getContinueWatching(): Flow<List<Anime>>

    @Query("SELECT COUNT(*) FROM anime")
    suspend fun getCount(): Int
}
