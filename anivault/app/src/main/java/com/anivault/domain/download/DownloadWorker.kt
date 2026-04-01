package com.anivault.domain.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anivault.data.database.DownloadDao
import com.anivault.data.model.DownloadStatus
import com.anivault.domain.repository.AnivaultRepository
import kotlinx.coroutines.flow.first

/**
 * WorkManager worker for background video and image downloads.
 * Handles downloads that need to survive app restarts.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val downloadDao: DownloadDao by lazy {
        val database = AnivaultDatabase.getInstance(applicationContext)
        database.downloadDao()
    }

    private val repository: AnivaultRepository by lazy {
        AnivaultRepository(applicationContext)
    }

    private val downloadPathManager: DownloadPathManager by lazy {
        DownloadPathManager(applicationContext)
    }

    private val episodeDownloader: EpisodeDownloader by lazy {
        EpisodeDownloader(repository, downloadPathManager)
    }

    private val chapterDownloader: ChapterDownloader by lazy {
        ChapterDownloader(repository, downloadPathManager)
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_ITEM_TYPE = "item_type"
        const val KEY_QUALITY = "quality"
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)
            ?: return Result.failure()

        val download = downloadDao.getById(downloadId)
            ?: return Result.failure()

        return try {
            downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING, 0f)

            when (download.itemType) {
                com.anivault.data.model.DownloadItemType.EPISODE -> {
                    downloadEpisode(download)
                }
                com.anivault.data.model.DownloadItemType.CHAPTER -> {
                    downloadChapter(download)
                }
            }

            downloadDao.setCompleted(downloadId, System.currentTimeMillis())
            downloadDao.updateStatus(downloadId, DownloadStatus.COMPLETED, 100f)
            Result.success()
        } catch (e: Exception) {
            handleDownloadFailure(downloadId, e)
        }
    }

    private suspend fun downloadEpisode(download: com.anivault.data.model.Download): Result {
        val episode = repository.getEpisode(download.itemId)
            ?: return Result.failure()

        val streamUrl = episode.streamUrl
            ?: return Result.failure()

        val savePath = downloadPathManager.getEpisodePath(download)

        episodeDownloader.downloadEpisode(
            episodeId = download.itemId,
            downloadId = download.id,
            streamUrl = streamUrl,
            quality = download.quality,
            savePath = savePath,
            onProgress = { progress ->
                downloadDao.updateStatus(download.id, DownloadStatus.DOWNLOADING, progress)
            }
        )

        repository.markEpisodeDownloaded(download.itemId, true, savePath)
        return Result.success()
    }

    private suspend fun downloadChapter(download: com.anivault.data.model.Download): Result {
        val chapter = repository.getChapter(download.itemId)
            ?: return Result.failure()

        val savePath = downloadPathManager.getChapterPath(download)

        chapterDownloader.downloadChapter(
            chapterId = download.itemId,
            downloadId = download.id,
            pageUrls = chapter.pageUrls,
            savePath = savePath,
            onProgress = { progress ->
                downloadDao.updateStatus(download.id, DownloadStatus.DOWNLOADING, progress)
            }
        )

        repository.markChapterDownloaded(download.itemId, true, savePath)
        return Result.success()
    }

    private suspend fun handleDownloadFailure(
        downloadId: String,
        error: Exception
    ): Result {
        val download = downloadDao.getById(downloadId) ?: return Result.failure()

        val currentStatus = download.status
        val retryCount = if (currentStatus == DownloadStatus.FAILED) {
            (download.errorMessage?.substringBefore(":")?.toIntOrNull() ?: 0) + 1
        } else {
            0
        }

        return if (retryCount < 3) {
            val errorMessage = "$retryCount: ${error.message}"
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, 0f, errorMessage)

            val inputData = workDataOf(
                KEY_DOWNLOAD_ID to downloadId
            )

            val retryConstraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val retryRequest = androidx.work.OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(retryConstraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()

            androidx.work.WorkManager.getInstance(applicationContext)
                .enqueue(retryRequest)

            Result.retry()
        } else {
            downloadDao.updateStatus(
                downloadId,
                DownloadStatus.FAILED,
                0f,
                "Failed after 3 attempts: ${error.message}"
            )
            Result.failure()
        }
    }
}
