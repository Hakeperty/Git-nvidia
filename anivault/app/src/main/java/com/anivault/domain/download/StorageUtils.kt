package com.anivault.domain.download

import java.io.File
import java.text.DecimalFormat

/**
 * Storage utilities for calculating file sizes, checking free space,
 * and formatting bytes to human-readable strings.
 */
object StorageUtils {

    private const val BYTES_PER_KB = 1024L
    private const val BYTES_PER_MB = BYTES_PER_KB * 1024
    private const val BYTES_PER_GB = BYTES_PER_MB * 1024L
    private const val BYTES_PER_TB = BYTES_PER_GB * 1024L

    private val decimalFormat = DecimalFormat("0.##")

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 0 -> "0 B"
            bytes < BYTES_PER_KB -> "$bytes B"
            bytes < BYTES_PER_MB -> "${decimalFormat.format(bytes.toDouble() / BYTES_PER_KB)} KB"
            bytes < BYTES_PER_GB -> "${decimalFormat.format(bytes.toDouble() / BYTES_PER_MB)} MB"
            bytes < BYTES_PER_TB -> "${decimalFormat.format(bytes.toDouble() / BYTES_PER_GB)} GB"
            else -> "${decimalFormat.format(bytes.toDouble() / BYTES_PER_TB)} TB"
        }
    }

    fun formatBytesWithUnit(bytes: Long, unit: String = "B"): String {
        return when (unit.uppercase()) {
            "B" -> formatBytes(bytes)
            "KB" -> "${decimalFormat.format(bytes.toDouble() / BYTES_PER_KB)} KB"
            "MB" -> "${decimalFormat.format(bytes.toDouble() / BYTES_PER_MB)} MB"
            "GB" -> "${decimalFormat.format(bytes.toDouble() / BYTES_PER_GB)} GB"
            "TB" -> "${decimalFormat.format(bytes.toDouble() / BYTES_PER_TB)} TB"
            else -> formatBytes(bytes)
        }
    }

    fun parseBytes(sizeString: String): Long {
        val trimmed = sizeString.trim().uppercase()
        return when {
            trimmed.endsWith("TB") -> (trimmed.removeSuffix("TB").trim().toDouble() * BYTES_PER_TB).toLong()
            trimmed.endsWith("GB") -> (trimmed.removeSuffix("GB").trim().toDouble() * BYTES_PER_GB).toLong()
            trimmed.endsWith("MB") -> (trimmed.removeSuffix("MB").trim().toDouble() * BYTES_PER_MB).toLong()
            trimmed.endsWith("KB") -> (trimmed.removeSuffix("KB").trim().toDouble() * BYTES_PER_KB).toLong()
            trimmed.endsWith("B") -> trimmed.removeSuffix("B").trim().toLong()
            else -> trimmed.toLongOrNull() ?: 0L
        }
    }

    fun getFileSize(file: File): Long {
        if (!file.exists()) return 0L
        return if (file.isFile) {
            file.length()
        } else {
            file.listFiles()?.sumOf { getFileSize(it) } ?: 0L
        }
    }

    fun getDirectorySize(directory: File): Long {
        if (!directory.exists() || !directory.isDirectory) return 0L
        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirectorySize(file) else getFileSize(file)
        }
        return size
    }

    fun getFreeSpace(path: String): Long {
        return try {
            val statFs = android.os.StatFs(path)
            statFs.availableBytes
        } catch (e: Exception) {
            -1L
        }
    }

    fun getFreeSpaceFormatted(path: String): String {
        return formatBytes(getFreeSpace(path))
    }

    fun getTotalSpace(path: String): Long {
        return try {
            val statFs = android.os.StatFs(path)
            statFs.totalBytes
        } catch (e: Exception) {
            -1L
        }
    }

    fun getTotalSpaceFormatted(path: String): String {
        return formatBytes(getTotalSpace(path))
    }

    fun getUsedSpace(path: String): Long {
        return try {
            val statFs = android.os.StatFs(path)
            statFs.totalBytes - statFs.availableBytes
        } catch (e: Exception) {
            -1L
        }
    }

    fun getUsedSpaceFormatted(path: String): String {
        return formatBytes(getUsedSpace(path))
    }

    fun getUsagePercentage(path: String): Float {
        return try {
            val statFs = android.os.StatFs(path)
            val used = statFs.totalBytes - statFs.availableBytes
            (used.toFloat() / statFs.totalBytes) * 100
        } catch (e: Exception) {
            0f
        }
    }

    fun canFitFile(path: String, fileSize: Long): Boolean {
        return getFreeSpace(path) >= fileSize
    }

    fun estimateDownloadTime(fileSizeBytes: Long, speedBps: Long): String {
        if (speedBps <= 0) return "Unknown"

        val seconds = fileSizeBytes.toDouble() / speedBps

        return when {
            seconds < 60 -> "${seconds.toInt()} seconds"
            seconds < 3600 -> "${(seconds / 60).toInt()} minutes"
            seconds < 86400 -> "${(seconds / 3600).toInt()} hours"
            else -> "${(seconds / 86400).toInt()} days"
        }
    }

    fun estimateDownloadTimeFormatted(fileSizeBytes: Long, speedMbps: Double): String {
        val speedBps = (speedMbps * BYTES_PER_MB).toLong()
        return estimateDownloadTime(fileSizeBytes, speedBps)
    }

    fun calculateRequiredSpaceForEpisodes(episodeCount: Int, avgSizeMB: Long = 300L): Long {
        return episodeCount * avgSizeMB * BYTES_PER_MB
    }

    fun calculateRequiredSpaceForChapters(chapterCount: Int, avgSizeKB: Long = 500L): Long {
        return chapterCount * avgSizeKB * BYTES_PER_KB
    }

    fun formatDownloadSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < BYTES_PER_KB -> "$bytesPerSecond B/s"
            bytesPerSecond < BYTES_PER_MB -> "${decimalFormat.format(bytesPerSecond.toDouble() / BYTES_PER_KB)} KB/s"
            bytesPerSecond < BYTES_PER_GB -> "${decimalFormat.format(bytesPerSecond.toDouble() / BYTES_PER_MB)} MB/s"
            else -> "${decimalFormat.format(bytesPerSecond.toDouble() / BYTES_PER_GB)} GB/s"
        }
    }

    fun formatProgress(current: Long, total: Long): String {
        val percentage = if (total > 0) {
            (current.toDouble() / total * 100).toInt()
        } else {
            0
        }
        return "$percentage% (${formatBytes(current)} / ${formatBytes(total)})"
    }

    fun formatProgressFloat(current: Float, total: Float = 100f): String {
        val percentage = ((current / total) * 100).toInt()
        return "$percentage%"
    }

    fun isStorageLow(path: String, thresholdMB: Long = 500): Boolean {
        return getFreeSpace(path) < thresholdMB * BYTES_PER_MB
    }

    fun getStorageWarningMessage(path: String, thresholdMB: Long = 500): String? {
        val freeSpace = getFreeSpace(path)
        val threshold = thresholdMB * BYTES_PER_MB

        return when {
            freeSpace < threshold -> {
                val freeFormatted = formatBytes(freeSpace)
                val thresholdFormatted = formatBytes(threshold)
                "Low storage: Only $freeFormatted free (recommended: $thresholdFormatted)"
            }
            freeSpace < threshold / 2 -> {
                val freeFormatted = formatBytes(freeSpace)
                "Critical storage: Only $freeFormatted remaining"
            }
            else -> null
        }
    }
}
