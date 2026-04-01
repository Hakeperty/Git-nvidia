package com.anivault.domain.repository

import android.content.Context
import com.anivault.data.database.*
import com.anivault.data.model.*
import com.anivault.data.scraper.ScraperManager
import kotlinx.coroutines.flow.Flow

class AnivaultRepository(
    private val animeDao: AnimeDao,
    private val episodeDao: EpisodeDao,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val downloadDao: DownloadDao,
    private val scraperManager: ScraperManager,
    private val context: Context
) {
    // Anime
    fun getAllAnime(): Flow<List<Anime>> = animeDao.getAll()
    fun getAnimeById(id: String): Flow<Anime?> = animeDao.getById(id).let { anime ->
        // Convert Flow to Flow<Anime?>
        TODO("Convert to Flow properly")
    }
    suspend fun getAnimeByIdSync(id: String): Anime? = animeDao.getById(id)
    fun searchAnime(query: String): Flow<List<Anime>> = animeDao.searchByTitle(query)

    suspend fun insertAnime(anime: Anime) = animeDao.insert(anime)
    suspend fun updateAnime(anime: Anime) = animeDao.update(anime)
    suspend fun deleteAnime(anime: Anime) = animeDao.delete(anime)

    // Episodes
    fun getEpisodesByAnimeId(animeId: String): Flow<List<Episode>> = episodeDao.getByAnimeId(animeId)
    suspend fun getEpisode(animeId: String, episodeNum: Int): Episode? =
        episodeDao.getEpisode(animeId, episodeNum)

    suspend fun insertEpisodes(episodes: List<Episode>) = episodeDao.insertAll(episodes)
    suspend fun updateEpisode(episode: Episode) = episodeDao.update(episode)

    suspend fun markEpisodeWatched(episodeId: String, progress: Float = 100f) {
        episodeDao.updateProgress(episodeId, EpisodeStatus.WATCHED, progress, System.currentTimeMillis())
    }

    suspend fun markEpisodeInProgress(episodeId: String, progress: Float) {
        episodeDao.updateProgress(episodeId, EpisodeStatus.IN_PROGRESS, progress, System.currentTimeMillis())
    }

    // Manga
    fun getAllManga(): Flow<List<Manga>> = mangaDao.getAll()
    suspend fun getMangaById(id: String): Manga? = mangaDao.getById(id)
    fun searchManga(query: String): Flow<List<Manga>> = mangaDao.searchByTitle(query)

    suspend fun insertManga(manga: Manga) = mangaDao.insert(manga)
    suspend fun updateManga(manga: Manga) = mangaDao.update(manga)
    suspend fun deleteManga(manga: Manga) = mangaDao.delete(manga)

    // Chapters
    fun getChaptersByMangaId(mangaId: String): Flow<List<Chapter>> = chapterDao.getByMangaId(mangaId)
    suspend fun getChapter(mangaId: String, chapterNum: Float): Chapter? =
        chapterDao.getChapter(mangaId, chapterNum)

    suspend fun insertChapters(chapters: List<Chapter>) = chapterDao.insertAll(chapters)
    suspend fun updateChapter(chapter: Chapter) = chapterDao.update(chapter)

    suspend fun markChapterRead(chapterId: String, page: Int = 0) {
        chapterDao.updateProgress(chapterId, ChapterStatus.READ, page)
    }

    suspend fun markChapterInProgress(chapterId: String, page: Int) {
        chapterDao.updateProgress(chapterId, ChapterStatus.IN_PROGRESS, page)
    }

    // Downloads
    fun getAllDownloads(): Flow<List<Download>> = downloadDao.getAll()
    fun getActiveDownloads(): Flow<List<Download>> =
        downloadDao.getByStatuses(listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED))

    suspend fun insertDownload(download: Download) = downloadDao.insert(download)
    suspend fun updateDownload(download: Download) = downloadDao.update(download)
    suspend fun deleteDownload(download: Download) = downloadDao.delete(download)

    // Import
    suspend fun importContent(url: String): ScraperManager.ImportResult =
        scraperManager.importContent(url)

    suspend fun getStreamUrl(episodeId: String, quality: String, isDub: Boolean): String? =
        scraperManager.getStreamUrl(episodeId, quality, isDub)

    suspend fun getChapterPages(chapterId: String): List<String> =
        scraperManager.getChapterImages(chapterId)

    // Library
    fun getContinueWatching(): Flow<List<Anime>> = animeDao.getContinueWatching()
    fun getContinueReading(): Flow<List<Manga>> = mangaDao.getContinueReading()

    suspend fun updateAnimeProgress(animeId: String, episodeNum: Int, watched: Boolean) {
        val anime = animeDao.getById(animeId) ?: return
        val updatedAnime = anime.copy(
            currentEpisode = if (watched) episodeNum + 1 else anime.currentEpisode,
            totalWatched = if (watched) anime.totalWatched + 1 else anime.totalWatched,
            lastWatchedDate = if (watched) System.currentTimeMillis() else anime.lastWatchedDate
        )
        animeDao.update(updatedAnime)
    }

    suspend fun updateMangaProgress(mangaId: String, chapterNum: Float, read: Boolean) {
        val manga = mangaDao.getById(mangaId) ?: return
        val updatedManga = manga.copy(
            currentChapter = if (read) chapterNum.toInt() + 1 else manga.currentChapter,
            totalRead = if (read) manga.totalRead + 1 else manga.totalRead,
            lastReadDate = if (read) System.currentTimeMillis() else manga.lastReadDate
        )
        mangaDao.update(updatedManga)
    }

    suspend fun getStorageUsage(): Long {
        // Calculate total storage used by downloads
        return 0L
    }

    // Download-related methods
    suspend fun getEpisode(episodeId: String): Episode? {
        return episodeDao.getById(episodeId)
    }

    suspend fun getChapter(chapterId: String): Chapter? {
        return chapterDao.getById(chapterId)
    }

    suspend fun updateEpisodeProgress(episodeId: String, progress: Float) {
        episodeDao.updateProgress(episodeId, EpisodeStatus.IN_PROGRESS, progress, System.currentTimeMillis())
    }

    suspend fun markEpisodeDownloaded(episodeId: String, isDownloaded: Boolean, downloadPath: String) {
        val episode = episodeDao.getById(episodeId) ?: return
        val updatedEpisode = episode.copy(
            isDownloaded = isDownloaded,
            downloadPath = downloadPath,
            status = if (isDownloaded) EpisodeStatus.WATCHED else EpisodeStatus.UNWATCHED
        )
        episodeDao.update(updatedEpisode)
    }

    suspend fun markChapterDownloaded(chapterId: String, isDownloaded: Boolean, downloadPath: String) {
        val chapter = chapterDao.getById(chapterId) ?: return
        val updatedChapter = chapter.copy(
            isDownloaded = isDownloaded,
            downloadPath = downloadPath,
            status = if (isDownloaded) ChapterStatus.READ else ChapterStatus.UNREAD
        )
        chapterDao.update(updatedChapter)
    }

    suspend fun updateEpisodeDownloadInfo(
        episodeId: String,
        downloadPath: String,
        fileSize: Long,
        quality: String
    ) {
        val episode = episodeDao.getById(episodeId) ?: return
        val updatedEpisode = episode.copy(
            downloadPath = downloadPath,
            isDownloaded = true,
            quality = quality
        )
        episodeDao.update(updatedEpisode)
    }

    suspend fun updateChapterDownloadInfo(
        chapterId: String,
        downloadPath: String,
        pageCount: Int,
        failedPageCount: Int
    ) {
        val chapter = chapterDao.getById(chapterId) ?: return
        val updatedChapter = chapter.copy(
            downloadPath = downloadPath,
            isDownloaded = failedPageCount == 0,
            pageCount = pageCount
        )
        chapterDao.update(updatedChapter)
    }

    suspend fun getEpisodeFromUrl(url: String): Episode? {
        return null
    }
}

fun AnivaultRepository.Companion.getInstance(context: Context): AnivaultRepository {
    val database = AnivaultDatabase.getInstance(context)
    val scraperManager = ScraperManager()
    return AnivaultRepository(
        animeDao = database.animeDao(),
        episodeDao = database.episodeDao(),
        mangaDao = database.mangaDao(),
        chapterDao = database.chapterDao(),
        downloadDao = database.downloadDao(),
        scraperManager = scraperManager,
        context = context
    )
}
