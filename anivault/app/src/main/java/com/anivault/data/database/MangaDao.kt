package com.anivault.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.anivault.data.model.Manga
import com.anivault.data.model.MangaStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga")
    fun getAll(): Flow<List<Manga>>

    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun getById(id: String): Manga?

    @Query("SELECT * FROM manga WHERE id = :id")
    fun getByIdLive(id: String): LiveData<Manga?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manga: Manga)

    @Update
    suspend fun update(manga: Manga)

    @Delete
    suspend fun delete(manga: Manga)

    @Query("DELETE FROM manga WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM manga WHERE title LIKE '%' || :query || '%'")
    fun searchByTitle(query: String): Flow<List<Manga>>

    @Query("SELECT * FROM manga WHERE status IN (:statuses)")
    fun getByStatus(statuses: List<MangaStatus>): Flow<List<Manga>>

    @Query("SELECT * FROM manga ORDER BY addedDate DESC")
    fun getSortedByAddedDate(): Flow<List<Manga>>

    @Query("SELECT * FROM manga ORDER BY title ASC")
    fun getSortedByTitle(): Flow<List<Manga>>

    @Query("SELECT * FROM manga WHERE genres LIKE '%' || :genre || '%'")
    fun getByGenre(genre: String): Flow<List<Manga>>

    @Query("SELECT * FROM manga WHERE currentChapter > 0 ORDER BY lastReadDate DESC")
    fun getContinueReading(): Flow<List<Manga>>

    @Query("SELECT COUNT(*) FROM manga")
    suspend fun getCount(): Int
}
