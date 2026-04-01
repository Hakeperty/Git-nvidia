package com.anivault.data.database

import androidx.room.*
import com.anivault.data.model.Chapter
import com.anivault.data.model.ChapterStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY chapterNumber ASC")
    fun getByMangaId(mangaId: String): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getById(id: String): Chapter?

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId AND chapterNumber = :chapNum")
    suspend fun getChapter(mangaId: String, chapNum: Float): Chapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: Chapter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<Chapter>)

    @Update
    suspend fun update(chapter: Chapter)

    @Delete
    suspend fun delete(chapter: Chapter)

    @Query("DELETE FROM chapters WHERE mangaId = :mangaId")
    suspend fun deleteByMangaId(mangaId: String)

    @Query("UPDATE chapters SET status = :status, lastReadPage = :page WHERE id = :id")
    suspend fun updateProgress(id: String, status: ChapterStatus, page: Int)

    @Query("SELECT * FROM chapters WHERE isDownloaded = 1")
    fun getDownloaded(): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE status = :status ORDER BY releaseDate DESC")
    fun getByStatus(status: ChapterStatus): Flow<List<Chapter>>

    @Query("SELECT COUNT(*) FROM chapters WHERE mangaId = :mangaId AND isDownloaded = 1")
    suspend fun getDownloadedCount(mangaId: String): Int
}
