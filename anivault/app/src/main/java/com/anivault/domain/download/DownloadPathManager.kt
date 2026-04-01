package com.anivault.domain.download

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import com.anivault.data.model.Download
import com.anivault.data.model.DownloadItemType
import java.io.File
import java.io.IOException

/**
 * Manages file paths for downloads.
 * Handles organization by series/episode or chapter, SD card support, and path validation.
 */
class DownloadPathManager(private val context: Context) {

    private val appBaseDir: File
        get() = File(context.filesDir, "downloads")

    private val externalBaseDir: File?
        get() {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            return externalDirs.firstOrNull { dir ->
                Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED &&
                    isExternalStoragePrimary(dir)
            }
        }

    private val baseDir: File
        get() = externalBaseDir ?: appBaseDir

    init {
        ensureBaseDirectoryExists()
    }

    private fun ensureBaseDirectoryExists() {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    fun getEpisodePath(download: Download): String {
        val episodeDir = File(baseDir, "episodes/${sanitizeFileName(download.title)}")
        if (!episodeDir.exists()) {
            episodeDir.mkdirs()
        }
        return File(episodeDir, "${download.id}.mp4").absolutePath
    }

    fun getChapterPath(download: Download): String {
        val chapterDir = File(baseDir, "chapters/${sanitizeFileName(download.title)}")
        if (!chapterDir.exists()) {
            chapterDir.mkdirs()
        }
        return chapterDir.absolutePath
    }

    fun getEpisodeThumbnailPath(download: Download): String {
        val episodeDir = File(baseDir, "episodes/${sanitizeFileName(download.title)}")
        return File(episodeDir, "thumbnail.jpg").absolutePath
    }

    fun getChapterImagePath(download: Download, pageNumber: Int): String {
        val chapterDir = File(baseDir, "chapters/${sanitizeFileName(download.title)}")
        if (!chapterDir.exists()) {
            chapterDir.mkdirs()
        }
        return File(chapterDir, "page_${String.format("%04d", pageNumber)}.jpg").absolutePath
    }

    fun getAnimeDownloadPath(animeTitle: String, episodeNumber: Int): String {
        val animeDir = File(baseDir, "episodes/${sanitizeFileName(animeTitle)}")
        if (!animeDir.exists()) {
            animeDir.mkdirs()
        }
        return File(animeDir, "episode_${String.format("%03d", episodeNumber)}.mp4").absolutePath
    }

    fun getMangaDownloadPath(mangaTitle: String, chapterNumber: Float): String {
        val mangaDir = File(baseDir, "chapters/${sanitizeFileName(mangaTitle)}")
        if (!mangaDir.exists()) {
            mangaDir.mkdirs()
        }
        return File(mangaDir, "chapter_${chapterNumber.toInt()}_${String.format("%02d", (chapterNumber % 1 * 100).toInt())}").absolutePath
    }

    fun getSeriesPath(seriesTitle: String, isAnime: Boolean): String {
        val type = if (isAnime) "episodes" else "chapters"
        val seriesDir = File(baseDir, "$type/${sanitizeFileName(seriesTitle)}")
        if (!seriesDir.exists()) {
            seriesDir.mkdirs()
        }
        return seriesDir.absolutePath
    }

    fun getAllDownloadsPath(): String {
        return baseDir.absolutePath
    }

    fun getDownloadsStats(): DownloadStats {
        var totalSize = 0L
        var episodeCount = 0
        var chapterCount = 0

        val episodesDir = File(baseDir, "episodes")
        if (episodesDir.exists()) {
            totalSize += getDirectorySize(episodesDir)
            episodeCount += countFiles(episodesDir)
        }

        val chaptersDir = File(baseDir, "chapters")
        if (chaptersDir.exists()) {
            totalSize += getDirectorySize(chaptersDir)
            chapterCount += countFiles(chaptersDir)
        }

        return DownloadStats(
            totalSize = totalSize,
            episodeCount = episodeCount,
            chapterCount = chapterCount
        )
    }

    fun isPathValid(path: String): Boolean {
        return try {
            val file = File(path)
            file.exists() || file.parentFile?.exists() == true
        } catch (e: Exception) {
            false
        }
    }

    fun validateAndCreatePath(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) {
                true
            } else {
                file.mkdirs()
                file.exists()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getAvailableStorageSpace(): Long {
        return StorageUtils.getFreeSpace(baseDir.absolutePath)
    }

    fun getTotalStorageSpace(): Long {
        return StorageUtils.getTotalSpace(baseDir.absolutePath)
    }

    fun isStorageLow(thresholdBytes: Long = 500 * 1024 * 1024): Boolean {
        return getAvailableStorageSpace() < thresholdBytes
    }

    fun deleteDownloadFiles(download: Download) {
        when (download.itemType) {
            DownloadItemType.EPISODE -> {
                val episodeDir = File(baseDir, "episodes/${sanitizeFileName(download.title)}")
                deleteRecursively(episodeDir)
            }
            DownloadItemType.CHAPTER -> {
                val chapterDir = File(baseDir, "chapters/${sanitizeFileName(download.title)}")
                deleteRecursively(chapterDir)
            }
        }
    }

    fun deleteSeriesFiles(seriesTitle: String, isAnime: Boolean) {
        val path = getSeriesPath(seriesTitle, isAnime)
        val dir = File(path)
        deleteRecursively(dir)
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    private fun getDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists()) {
            directory.listFiles()?.forEach {
                size += if (it.isDirectory) getDirectorySize(it) else it.length()
            }
        }
        return size
    }

    private fun countFiles(directory: File): Int {
        var count = 0
        if (directory.exists()) {
            directory.listFiles()?.forEach {
                count += if (it.isDirectory) countFiles(it) else 1
            }
        }
        return count
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[<>:\"/\\|?*]"), "_")
            .trim()
            .take(200)
    }

    private fun isExternalStoragePrimary(file: File): Boolean {
        return Environment.getExternalStorageDirectory().absolutePath == file.absolutePath
            || Environment.getExternalStorageDirectory().absolutePath.startsWith(file.absolutePath)
    }

    fun migrateDownloadsToExternal(): Boolean {
        val externalDir = externalBaseDir ?: return false
        if (externalDir == appBaseDir) return true

        return try {
            if (appBaseDir.exists()) {
                appBaseDir.copyRecursively(externalDir, overwrite = true)
                appBaseDir.deleteRecursively()
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    fun migrateDownloadsToInternal(): Boolean {
        if (externalBaseDir == null || externalBaseDir == appBaseDir) return true

        return try {
            if (externalBaseDir.exists()) {
                externalBaseDir.copyRecursively(appBaseDir, overwrite = true)
                externalBaseDir.deleteRecursively()
            }
            true
        } catch (e: IOException) {
            false
        }
    }
}

data class DownloadStats(
    val totalSize: Long,
    val episodeCount: Int,
    val chapterCount: Int
)
