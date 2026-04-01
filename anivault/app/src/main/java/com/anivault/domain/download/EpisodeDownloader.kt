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
 * Episode video downloader.
 * Handles downloading video files, updating progress, and marking episodes as downloaded.
 */
class EpisodeDownloader(
    private val repository: AnivaultRepository,
    private val downloadPathManager: DownloadPathManager,
    private val qualitySelector: QualitySelector = QualitySelector(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    suspend fun downloadEpisode(
        episodeId: String,
        downloadId: String,
        streamUrl: String,
        quality: String = "720p",
        savePath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val episode = repository.getEpisode(episodeId) ?: return@withContext false
            val duration = episode.duration ?: 1200

            val selectedQuality = qualitySelector.selectBestQuality(
                availableQualities = listOf(quality),
                preferredQuality = quality
            ).selectedQuality

            val finalUrl = resolveStreamUrl(streamUrl, selectedQuality)
            if (finalUrl.isEmpty()) {
                throw IllegalStateException("Could not resolve stream URL")
            }

            val file = File(savePath)
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            val request = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "Anivault/1.0")
                .header("Referer", "https://anivault.com/")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("No response body")
            val totalBytes = body.contentLength()

            if (totalBytes <= 0) {
                throw IllegalStateException("Could not determine file size")
            }

            val availableSpace = StorageUtils.getFreeSpace(downloadPathManager.baseDir.absolutePath)
            if (availableSpace < totalBytes) {
                throw IllegalStateException("Insufficient storage space")
            }

            FileOutputStream(file).use { output ->
                var downloadedBytes = 0L
                val source = body.source()

                while (true) {
                    val buffer = source.readByteArray(8192)
                    if (buffer.isEmpty()) break

                    output.write(buffer)
                    downloadedBytes += buffer.size

                    val progress = (downloadedBytes.toFloat() / totalBytes * 100).coerceIn(0f, 100f)
                    onProgress(progress)
                }
            }

            val fileSize = file.length()
            val actualProgress = if (fileSize > 0) 100f else 0f
            onProgress(actualProgress)

            repository.updateEpisodeDownloadInfo(
                episodeId = episodeId,
                downloadPath = savePath,
                fileSize = fileSize,
                quality = selectedQuality
            )

            true

        } catch (e: Exception) {
            e.printStackTrace()
            cleanupFailedDownload(savePath)
            false
        }
    }

    suspend fun downloadEpisodeWithRetry(
        episodeId: String,
        downloadId: String,
        streamUrl: String,
        quality: String = "720p",
        savePath: String,
        onProgress: (Float) -> Unit,
        maxRetries: Int = 3
    ): Boolean {
        var lastError: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                if (downloadEpisode(episodeId, downloadId, streamUrl, quality, savePath, onProgress)) {
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

    private suspend fun resolveStreamUrl(streamUrl: String, quality: String): String {
        return try {
            if (streamUrl.contains(".m3u8")) {
                resolveM3u8Url(streamUrl, quality)
            } else {
                streamUrl
            }
        } catch (e: Exception) {
            streamUrl
        }
    }

    private suspend fun resolveM3u8Url(m3u8Url: String, quality: String): String {
        try {
            val request = Request.Builder()
                .url(m3u8Url)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return m3u8Url

            val body = response.body?.string() ?: return m3u8Url
            val lines = body.split("\n")

            val qualityMap = mapOf(
                "2160" to listOf("2160p", "4k"),
                "1440" to listOf("1440p"),
                "1080" to listOf("1080p", "full", "hd1080"),
                "720" to listOf("720p", "hd"),
                "480" to listOf("480p"),
                "360" to listOf("360p")
            )

            val targetQualities = qualityMap[quality.substringBefore("p")]
                ?: qualityMap["720"] ?: listOf("720p")

            for (line in lines) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val resolutionLine = lines.getOrNull(lines.indexOf(line) + 1) ?: continue
                    if (resolutionLine.isNotEmpty() && !resolutionLine.startsWith("#")) {
                        for (targetQuality in targetQualities) {
                            if (line.lowercase().contains("${targetQuality.substringBefore("p")}")) {
                                return resolveRelativeUrl(m3u8Url, resolutionLine.trim())
                            }
                        }
                    }
                }
            }

            val bestQualityLine = lines.firstOrNull {
                it.startsWith("#EXT-X-STREAM-INF")
            }
            val bestQualityUrl = lines.getOrNull(lines.indexOf(bestQualityLine!!) + 1)
            if (bestQualityUrl != null && !bestQualityUrl.startsWith("#")) {
                return resolveRelativeUrl(m3u8Url, bestQualityUrl.trim())
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return m3u8Url
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeUrl: String): String {
        return if (relativeUrl.startsWith("http")) {
            relativeUrl
        } else {
            val baseUrlWithoutFile = baseUrl.substringBeforeLast("/")
            "$baseUrlWithoutFile/$relativeUrl"
        }
    }

    private fun cleanupFailedDownload(savePath: String) {
        try {
            val file = File(savePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getDownloadEstimate(
        duration: Int = 1200,
        quality: String = "720p"
    ): DownloadEstimate {
        val fileSize = qualitySelector.estimateFileSizeWithOverhead(quality, duration)
        val sizeFormatted = StorageUtils.formatBytes(fileSize)

        return DownloadEstimate(
            fileSize = fileSize,
            fileSizeFormatted = sizeFormatted,
            quality = quality,
            duration = duration
        )
    }

    data class DownloadEstimate(
        val fileSize: Long,
        val fileSizeFormatted: String,
        val quality: String,
        val duration: Int
    )
}
