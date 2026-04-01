package com.anivault.domain.download

import com.anivault.domain.repository.AnivaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Chapter image downloader.
 * Handles downloading all chapter images, caching locally, and updating progress.
 */
class ChapterDownloader(
    private val repository: AnivaultRepository,
    private val downloadPathManager: DownloadPathManager,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    suspend fun downloadChapter(
        chapterId: String,
        downloadId: String,
        pageUrls: List<String>,
        savePath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val chapter = repository.getChapter(chapterId) ?: return@withContext false

            val dir = File(savePath)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            if (pageUrls.isEmpty()) {
                throw IllegalStateException("No page URLs available for chapter")
            }

            val availableSpace = StorageUtils.getFreeSpace(downloadPathManager.baseDir.absolutePath)
            val estimatedSize = estimateChapterSize(pageUrls.size)
            if (availableSpace < estimatedSize) {
                throw IllegalStateException("Insufficient storage space")
            }

            var downloadedPages = 0
            var failedPages = 0
            val downloadedFiles = mutableListOf<String>()

            pageUrls.forEachIndexed { index, url ->
                try {
                    val pagePath = downloadPathManager.getChapterImagePath(downloadId, index + 1)
                    if (downloadImage(url, pagePath)) {
                        downloadedFiles.add(pagePath)
                        downloadedPages++
                    } else {
                        failedPages++
                    }

                    val progress = (downloadedPages.toFloat() / pageUrls.size * 100).coerceIn(0f, 100f)
                    onProgress(progress)
                } catch (e: Exception) {
                    failedPages++
                }
            }

            val finalProgress = if (pageUrls.isNotEmpty()) {
                (downloadedPages.toFloat() / pageUrls.size * 100)
            } else {
                0f
            }
            onProgress(finalProgress)

            repository.updateChapterDownloadInfo(
                chapterId = chapterId,
                downloadPath = savePath,
                pageCount = downloadedPages,
                failedPageCount = failedPages
            )

            downloadedPages == pageUrls.size

        } catch (e: Exception) {
            e.printStackTrace()
            cleanupFailedDownload(savePath)
            false
        }
    }

    suspend fun downloadChapterWithRetry(
        chapterId: String,
        downloadId: String,
        pageUrls: List<String>,
        savePath: String,
        onProgress: (Float) -> Unit,
        maxRetries: Int = 3
    ): Boolean {
        var lastError: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                if (downloadChapter(chapterId, downloadId, pageUrls, savePath, onProgress)) {
                    return true
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(2000L * attempt)
                }
            }
        }

        throw lastError ?: IllegalStateException("Download failed after all retries")
    }

    suspend fun downloadChapterImages(
        chapterId: String,
        pageUrls: List<String>,
        savePath: String,
        onProgress: (Int, Int) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        val downloadedFiles = mutableListOf<String>()
        val dir = File(savePath)

        if (!dir.exists()) {
            dir.mkdirs()
        }

        pageUrls.forEachIndexed { index, url ->
            try {
                val pagePath = File(savePath, "page_${String.format("%04d", index + 1)}.jpg").absolutePath
                if (downloadImage(url, pagePath)) {
                    downloadedFiles.add(pagePath)
                }
                onProgress(downloadedFiles.size, pageUrls.size)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        downloadedFiles
    }

    suspend fun downloadSingleImage(
        url: String,
        savePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        downloadImage(url, savePath)
    }

    private fun downloadImage(url: String, savePath: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Anivault/1.0")
                .header("Referer", "https://anivault.com/")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return false
            }

            val body = response.body ?: return false
            val inputStream = body.byteStream()

            FileOutputStream(savePath).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }

            val file = File(savePath)
            file.length() > 0

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun cleanupFailedDownload(savePath: String) {
        try {
            val dir = File(savePath)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun estimateChapterSize(pageCount: Int, avgPageSizeKB: Long = 500): Long {
        return pageCount * avgPageSizeKB * 1024
    }

    suspend fun getChapterEstimate(pageUrls: List<String>): ChapterEstimate {
        val estimatedSize = estimateChapterSize(pageUrls.size)
        val sizeFormatted = StorageUtils.formatBytes(estimatedSize)

        return ChapterEstimate(
            pageCount = pageUrls.size,
            estimatedSize = estimatedSize,
            estimatedSizeFormatted = sizeFormatted
        )
    }

    fun getExistingChapterImages(chapterPath: String): List<String> {
        val dir = File(chapterPath)
        if (!dir.exists()) return emptyList()

        return dir.listFiles { file ->
            file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
        }?.sortedBy { it.name }?.map { it.absolutePath } ?: emptyList()
    }

    fun isChapterFullyDownloaded(chapterPath: String, expectedPageCount: Int): Boolean {
        val images = getExistingChapterImages(chapterPath)
        return images.size >= expectedPageCount
    }

    suspend fun verifyDownloadedChapter(chapterPath: String): ChapterVerification {
        return withContext(Dispatchers.IO) {
            val images = getExistingChapterImages(chapterPath)
            val totalSize = images.sumOf { fileSize(it) }

            ChapterVerification(
                imageCount = images.size,
                totalSize = totalSize,
                totalSizeFormatted = StorageUtils.formatBytes(totalSize),
                isValid = images.isNotEmpty()
            )
        }
    }

    private fun fileSize(path: String): Long {
        return try {
            File(path).length()
        } catch (e: Exception) {
            0L
        }
    }

    data class ChapterEstimate(
        val pageCount: Int,
        val estimatedSize: Long,
        val estimatedSizeFormatted: String
    )

    data class ChapterVerification(
        val imageCount: Int,
        val totalSize: Long,
        val totalSizeFormatted: String,
        val isValid: Boolean
    )
}
