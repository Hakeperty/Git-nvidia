package com.anivault.domain.download

import com.anivault.data.database.DownloadDao
import com.anivault.data.model.Download
import com.anivault.data.model.DownloadItemType
import com.anivault.data.model.DownloadStatus
import com.anivault.domain.repository.AnivaultRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Core download manager with queue management, parallel downloads, and progress tracking.
 * Supports pause/resume, retry logic, and Flow-based progress updates.
 */
class DownloadManager(
    private val downloadDao: DownloadDao,
    private val repository: AnivaultRepository,
    private val downloadPathManager: DownloadPathManager,
    private val episodeDownloader: EpisodeDownloader,
    private val chapterDownloader: ChapterDownloader,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val maxConcurrentDownloads: Int = 3,
    private val maxRetryAttempts: Int = 3
) {
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private val downloadSemaphore = Semaphore(maxConcurrentDownloads)
    private val downloadIdCounter = AtomicInteger(0)

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _downloadStatus = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = _downloadStatus.asStateFlow()

    private val _downloadErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadErrors: StateFlow<Map<String, String>> = _downloadErrors.asStateFlow()

    private val _downloadEvents = MutableSharedFlow<DownloadEvent>(replay = 0)
    val downloadEvents: SharedFlow<DownloadEvent> = _downloadEvents.asSharedFlow()

    init {
        scope.launch {
            downloadDao.getByStatuses(listOf(DownloadStatus.DOWNLOADING)).collect { downloads ->
                downloads.forEach { download ->
                    activeDownloads.remove(download.id)
                    updateDownloadStatus(download.id, DownloadStatus.QUEUED)
                }
            }
        }
    }

    sealed class DownloadEvent {
        data class Progress(val downloadId: String, val progress: Float) : DownloadEvent()
        data class StatusChanged(val downloadId: String, val status: DownloadStatus) : DownloadEvent()
        data class Error(val downloadId: String, val error: String) : DownloadEvent()
        data class Completed(val downloadId: String) : DownloadEvent()
        data class Failed(val downloadId: String, val error: String) : DownloadEvent()
        data class Paused(val downloadId: String) : DownloadEvent()
        data class Resumed(val downloadId: String) : DownloadEvent()
    }

    suspend fun queueEpisodeDownload(
        episodeId: String,
        animeId: String,
        episodeNumber: Int,
        animeTitle: String,
        quality: String = "720p"
    ): String {
        val downloadId = "ep_${episodeId}_${System.currentTimeMillis()}"
        val download = Download(
            id = downloadId,
            itemId = episodeId,
            itemType = DownloadItemType.EPISODE,
            title = "$animeTitle - Episode $episodeNumber",
            quality = quality,
            status = DownloadStatus.QUEUED
        )
        downloadDao.insert(download)
        enqueueDownload(downloadId)
        return downloadId
    }

    suspend fun queueChapterDownload(
        chapterId: String,
        mangaId: String,
        chapterNumber: Float,
        mangaTitle: String
    ): String {
        val downloadId = "ch_${chapterId}_${System.currentTimeMillis()}"
        val download = Download(
            id = downloadId,
            itemId = chapterId,
            itemType = DownloadItemType.CHAPTER,
            title = "$mangaTitle - Chapter ${chapterNumber.toInt()}",
            status = DownloadStatus.QUEUED
        )
        downloadDao.insert(download)
        enqueueDownload(downloadId)
        return downloadId
    }

    suspend fun queueMultipleDownloads(
        episodeIds: List<Pair<String, Triple<String, Int, String>>>,
        quality: String = "720p"
    ): List<String> {
        val downloadIds = mutableListOf<String>()
        episodeIds.forEach { (episodeId, triple) ->
            val (animeId, episodeNumber, animeTitle) = triple
            val downloadId = queueEpisodeDownload(
                episodeId = episodeId,
                animeId = animeId,
                episodeNumber = episodeNumber,
                animeTitle = animeTitle,
                quality = quality
            )
            downloadIds.add(downloadId)
        }
        return downloadIds
    }

    private fun enqueueDownload(downloadId: String) {
        scope.launch {
            downloadSemaphore.acquire()
            try {
                val download = downloadDao.getById(downloadId) ?: return@launch
                startDownload(download)
            } finally {
                downloadSemaphore.release()
            }
        }
    }

    private suspend fun startDownload(download: Download) {
        if (activeDownloads.containsKey(download.id)) return

        updateDownloadStatus(download.id, DownloadStatus.DOWNLOADING)
        updateDownloadStarted(download.id)

        val job = scope.launch {
            try {
                when (download.itemType) {
                    DownloadItemType.EPISODE -> {
                        val episode = repository.getEpisode(download.itemId)
                        if (episode != null) {
                            episodeDownloader.downloadEpisode(
                                episodeId = download.itemId,
                                downloadId = download.id,
                                streamUrl = episode.streamUrl ?: throw IllegalStateException("No stream URL available"),
                                quality = download.quality,
                                savePath = downloadPathManager.getEpisodePath(download),
                                onProgress = { progress ->
                                    updateDownloadProgress(download.id, progress)
                                    repository.updateEpisodeProgress(download.itemId, progress)
                                }
                            )
                        } else {
                            throw IllegalStateException("Episode not found: ${download.itemId}")
                        }
                    }
                    DownloadItemType.CHAPTER -> {
                        val chapter = repository.getChapter(download.itemId)
                        if (chapter != null) {
                            chapterDownloader.downloadChapter(
                                chapterId = download.itemId,
                                downloadId = download.id,
                                pageUrls = chapter.pageUrls,
                                savePath = downloadPathManager.getChapterPath(download),
                                onProgress = { progress ->
                                    updateDownloadProgress(download.id, progress)
                                }
                            )
                        } else {
                            throw IllegalStateException("Chapter not found: ${download.itemId}")
                        }
                    }
                }

                updateDownloadStatus(download.id, DownloadStatus.COMPLETED)
                updateDownloadCompleted(download.id)
                markItemAsDownloaded(download)
                _downloadEvents.emit(DownloadEvent.Completed(download.id))

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleDownloadFailure(download.id, e)
            }
        }

        activeDownloads[download.id] = job

        try {
            job.join()
        } catch (e: CancellationException) {
            // Job was cancelled externally
        } finally {
            activeDownloads.remove(download.id)
        }
    }

    private suspend fun handleDownloadFailure(downloadId: String, error: Exception) {
        val download = downloadDao.getById(downloadId) ?: return
        val currentStatus = _downloadStatus.value[downloadId]

        val retryCount = if (currentStatus is DownloadStatus.FAILED) {
            (download.errorMessage?.substringBefore(":")?.toIntOrNull() ?: 0) + 1
        } else {
            0
        }

        val errorMessage = "${retryCount}: ${error.message}"

        if (retryCount < maxRetryAttempts) {
            updateDownloadStatus(
                downloadId,
                DownloadStatus.QUEUED,
                errorMessage = errorMessage
            )
            _downloadEvents.emit(DownloadEvent.Error(downloadId, error.message ?: "Unknown error"))
            activeDownloads.remove(downloadId)
            enqueueDownload(downloadId)
        } else {
            updateDownloadStatus(
                downloadId,
                DownloadStatus.FAILED,
                errorMessage = "Failed after $maxRetryAttempts attempts: ${error.message}"
            )
            _downloadEvents.emit(DownloadEvent.Failed(downloadId, error.message ?: "Unknown error"))
        }
    }

    fun pauseDownload(downloadId: String) {
        val job = activeDownloads[downloadId] ?: return
        job.cancel()
        activeDownloads.remove(downloadId)
        updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
        scope.launch {
            _downloadEvents.emit(DownloadEvent.Paused(downloadId))
        }
    }

    fun resumeDownload(downloadId: String) {
        val download = runBlocking { downloadDao.getById(downloadId) } ?: return
        if (download.status != DownloadStatus.PAUSED) return
        updateDownloadStatus(downloadId, DownloadStatus.QUEUED)
        enqueueDownload(downloadId)
        scope.launch {
            _downloadEvents.emit(DownloadEvent.Resumed(downloadId))
        }
    }

    suspend fun cancelDownload(downloadId: String) {
        val job = activeDownloads[downloadId]
        job?.cancel()
        activeDownloads.remove(downloadId)

        val download = downloadDao.getById(downloadId) ?: return
        updateDownloadStatus(downloadId, DownloadStatus.CANCELLED)

        cleanupDownloadFiles(downloadId)
    }

    suspend fun cancelAllDownloads() {
        val downloads = downloadDao.getByStatuses(
            listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED)
        ).first()
        downloads.forEach { download ->
            cancelDownload(download.id)
        }
    }

    suspend fun retryFailedDownload(downloadId: String) {
        val download = downloadDao.getById(downloadId) ?: return
        if (download.status != DownloadStatus.FAILED) return

        updateDownloadStatus(downloadId, DownloadStatus.QUEUED, errorMessage = "0")
        enqueueDownload(downloadId)
    }

    suspend fun retryAllFailedDownloads() {
        val failed = downloadDao.getByStatuses(listOf(DownloadStatus.FAILED)).first()
        failed.forEach { download ->
            retryFailedDownload(download.id)
        }
    }

    suspend fun removeDownloadRecord(downloadId: String) {
        val download = downloadDao.getById(downloadId) ?: return
        cleanupDownloadFiles(download.id)
        downloadDao.deleteById(downloadId)
    }

    private fun cleanupDownloadFiles(downloadId: String) {
        scope.launch(Dispatchers.IO) {
            val download = downloadDao.getById(downloadId) ?: return@launch
            val path = when (download.itemType) {
                DownloadItemType.EPISODE -> downloadPathManager.getEpisodePath(download)
                DownloadItemType.CHAPTER -> downloadPathManager.getChapterPath(download)
            }
            val file = File(path)
            if (file.exists()) {
                deleteRecursively(file)
            }
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    private suspend fun updateDownloadProgress(downloadId: String, progress: Float) {
        _downloadProgress.value = _downloadProgress.value + (downloadId to progress)
        downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING, progress)
        _downloadEvents.emit(DownloadEvent.Progress(downloadId, progress))
    }

    private suspend fun updateDownloadStatus(
        downloadId: String,
        status: DownloadStatus,
        errorMessage: String? = null
    ) {
        _downloadStatus.value = _downloadStatus.value + (downloadId to status)
        if (errorMessage != null) {
            _downloadErrors.value = _downloadErrors.value + (downloadId to errorMessage)
        }
        val currentProgress = _downloadProgress.value[downloadId] ?: 0f
        downloadDao.updateStatus(downloadId, status, currentProgress, errorMessage)
        _downloadEvents.emit(DownloadEvent.StatusChanged(downloadId, status))
    }

    private suspend fun updateDownloadStarted(downloadId: String) {
        downloadDao.setStarted(downloadId, System.currentTimeMillis())
    }

    private suspend fun updateDownloadCompleted(downloadId: String) {
        downloadDao.setCompleted(downloadId, System.currentTimeMillis())
        updateDownloadProgress(downloadId, 100f)
    }

    private suspend fun markItemAsDownloaded(download: Download) {
        when (download.itemType) {
            DownloadItemType.EPISODE -> {
                repository.markEpisodeDownloaded(
                    download.itemId,
                    true,
                    downloadPathManager.getEpisodePath(download)
                )
            }
            DownloadItemType.CHAPTER -> {
                repository.markChapterDownloaded(
                    download.itemId,
                    true,
                    downloadPathManager.getChapterPath(download)
                )
            }
        }
    }

    suspend fun getActiveDownloads(): List<Download> {
        return downloadDao.getByStatuses(
            listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED)
        ).first()
    }

    suspend fun getDownloadInfo(downloadId: String): Download? {
        return downloadDao.getById(downloadId)
    }

    fun getDownloadProgress(downloadId: String): Float {
        return _downloadProgress.value[downloadId] ?: 0f
    }

    fun getDownloadStatus(downloadId: String): DownloadStatus {
        return _downloadStatus.value[downloadId] ?: DownloadStatus.UNKNOWN
    }

    fun getActiveDownloadCount(): Int {
        return activeDownloads.size
    }

    fun isStorageLow(): Boolean {
        return downloadPathManager.isStorageLow()
    }

    fun getAvailableSpace(): Long {
        return downloadPathManager.getAvailableStorageSpace()
    }
}
