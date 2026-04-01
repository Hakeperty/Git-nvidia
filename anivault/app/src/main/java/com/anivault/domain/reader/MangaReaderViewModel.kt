package com.anivault.domain.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anivault.data.database.ChapterDao
import com.anivault.data.database.PlayerProgressDao
import com.anivault.data.model.Chapter
import com.anivault.data.model.Manga
import com.anivault.data.model.ReadingMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Domain-level Manga Reader ViewModel for state management
 * Handles:
 * - Current chapter tracking
 * - Page loading and navigation
 * - Reading progress saving
 * - Reading mode preferences
 */
class MangaReaderViewModel(
    application: Application,
    private val chapterDao: ChapterDao
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MangaReaderState())
    val state: StateFlow<MangaReaderState> = _state.asStateFlow()

    private var currentPages: List<String> = emptyList()
    private var saveJob: kotlinx.coroutines.Job? = null

    /**
     * Set the current chapter to read
     */
    fun setChapter(chapter: Chapter, manga: Manga?) {
        viewModelScope.launch {
            _state.update { it.copy(
                currentChapter = chapter,
                seriesInfo = manga,
                isLoading = true,
                error = null
            ) }

            // Load saved progress
            val savedProgress = chapterDao.getChapterProgressSync(chapter.id)

            _state.update { it.copy(
                currentPage = savedProgress?.lastReadPage ?: 0,
                totalPages = chapter.pageCount,
                readingMode = manga?.readingMode ?: ReadingMode.PAGINATED,
                isLoading = false
            ) }

            // Load page URLs
            currentPages = chapter.pageUrls
            _state.update { it.copy(totalPages = currentPages.size) }
        }
    }

    /**
     * Set current page
     */
    fun setCurrentPage(page: Int) {
        if (page in 0 until _state.value.totalPages) {
            _state.update { it.copy(currentPage = page) }
            startProgressSave()
        }
    }

    /**
     * Navigate pages (positive = next, negative = previous)
     */
    fun navigatePage(direction: Int) {
        val current = _state.value.currentPage
        val next = (current + direction).coerceIn(0, _state.value.totalPages - 1)
        setCurrentPage(next)
    }

    /**
     * Set reading mode
     */
    fun setReadingMode(mode: ReadingMode) {
        _state.update { it.copy(readingMode = mode) }
        // Reset to first page when changing modes
        setCurrentPage(0)
    }

    /**
     * Set chapters list
     */
    fun setChapters(chapters: List<Chapter>) {
        _state.update { it.copy(chapters = chapters) }
    }

    /**
     * Load chapter progress
     */
    private fun loadChapterProgress(chapterId: String) {
        viewModelScope.launch {
            val progress = chapterDao.getChapterProgressSync(chapterId)
            progress?.let {
                _state.update { state ->
                    state.copy(
                        currentPage = it.lastReadPage,
                        totalPages = it.totalPages
                    )
                }
            }
        }
    }

    /**
     * Save progress periodically
     */
    private fun startProgressSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(2000) // Wait 2 seconds before saving
            saveProgress()
        }
    }

    private fun saveProgress() {
        viewModelScope.launch {
            val chapter = _state.value.currentChapter ?: return@launch
            val page = _state.value.currentPage

            chapterDao.updateProgress(
                chapterId = chapter.id,
                lastReadPage = page,
                totalPages = currentPages.size
            )

            // Update manga last read date
            _state.value.seriesInfo?.let { manga ->
                chapterDao.updateMangaLastReadDate(manga.id, System.currentTimeMillis())
            }
        }
    }

    /**
     * Mark chapter as read
     */
    fun markChapterAsRead(chapterId: String) {
        viewModelScope.launch {
            chapterDao.updateStatus(chapterId, com.anivault.data.model.ChapterStatus.READ)
        }
    }

    /**
     * Mark chapter as in progress
     */
    fun markChapterInProgress(chapterId: String) {
        viewModelScope.launch {
            chapterDao.updateStatus(chapterId, com.anivault.data.model.ChapterStatus.IN_PROGRESS)
        }
    }

    /**
     * Get next chapter
     */
    fun getNextChapter(): Chapter? {
        val current = _state.value.currentChapter ?: return null
        return _state.value.chapters
            .filter { it.chapterNumber > current.chapterNumber }
            .minByOrNull { it.chapterNumber }
    }

    /**
     * Get previous chapter
     */
    fun getPreviousChapter(): Chapter? {
        val current = _state.value.currentChapter ?: return null
        return _state.value.chapters
            .filter { it.chapterNumber < current.chapterNumber }
            .maxByOrNull { it.chapterNumber }
    }

    /**
     * Navigate to next chapter
     */
    fun navigateToNextChapter() {
        getNextChapter()?.let { setChapter(it, _state.value.seriesInfo) }
    }

    /**
     * Navigate to previous chapter
     */
    fun navigateToPreviousChapter() {
        getPreviousChapter()?.let { setChapter(it, _state.value.seriesInfo) }
    }

    fun setCurrentChapter(chapter: Chapter) {
        _state.update { it.copy(currentChapter = chapter) }
    }

    fun setSeriesInfo(manga: Manga) {
        _state.update { it.copy(seriesInfo = manga) }
    }

    fun setTotalPages(total: Int) {
        _state.update { it.copy(totalPages = total) }
    }

    fun setPageUrls(urls: List<String>) {
        currentPages = urls
        _state.update { it.copy(totalPages = urls.size) }
    }

    override fun onCleared() {
        super.onCleared()
        saveJob?.cancel()
        // Save final progress
        viewModelScope.launch {
            saveProgress()
        }
    }
}

data class MangaReaderState(
    val currentChapter: Chapter? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val readingMode: ReadingMode = ReadingMode.PAGINATED,
    val seriesInfo: Manga? = null,
    val chapters: List<Chapter> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val pageUrls: List<String>
        get() = emptyList() // Would come from chapter if loaded

    val progressPercentage: Float
        get() = if (totalPages > 0) (currentPage.toFloat() / totalPages * 100) else 0f

    val hasNextChapter: Boolean
        get() = currentChapter != null && chapters.any { it.chapterNumber > currentChapter!!.chapterNumber }

    val hasPreviousChapter: Boolean
        get() = currentChapter != null && chapters.any { it.chapterNumber < currentChapter!!.chapterNumber }
}
