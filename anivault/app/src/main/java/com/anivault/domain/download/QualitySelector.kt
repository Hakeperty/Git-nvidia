package com.anivault.domain.download

import com.anivault.data.model.Episode

/**
 * Quality selection logic for video downloads.
 * Parses available qualities, selects best available, and handles fallback logic.
 */
class QualitySelector {

    companion object {
        private val QUALITY_ORDER = listOf(
            "2160p", "4K", "1440p", "1080p", "720p", "480p", "360p", "240p"
        )

        private val QUALITY_RESOLUTION_MAP = mapOf(
            "2160p" to 2160,
            "4K" to 2160,
            "1440p" to 1440,
            "1080p" to 1080,
            "720p" to 720,
            "480p" to 480,
            "360p" to 360,
            "240p" to 240
        )

        private val DEFAULT_QUALITY = "720p"
    }

    data class QualityInfo(
        val quality: String,
        val resolution: Int,
        val bitrate: Long? = null,
        val isHdr: Boolean = false,
        val isFallback: Boolean = false
    )

    data class QualitySelectionResult(
        val selectedQuality: String,
        val availableQualities: List<String>,
        val isFallback: Boolean,
        val fallbackReason: String? = null
    )

    fun parseAvailableQualities(qualityString: String): List<String> {
        return qualityString
            .split(",", ";", "|")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { getQualityResolution(it) }
    }

    fun parseAvailableQualitiesFromList(qualities: List<String>): List<String> {
        return qualities
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { getQualityResolution(it) }
    }

    fun selectBestQuality(
        availableQualities: List<String>,
        preferredQuality: String = DEFAULT_QUALITY,
        allowFallback: Boolean = true
    ): QualitySelectionResult {
        if (availableQualities.isEmpty()) {
            return QualitySelectionResult(
                selectedQuality = DEFAULT_QUALITY,
                availableQualities = emptyList(),
                isFallback = true,
                fallbackReason = "No qualities available"
            )
        }

        val normalizedAvailable = availableQualities.map { it.lowercase() }
        val normalizedPreferred = preferredQuality.lowercase()

        val preferredResolution = getQualityResolution(normalizedPreferred)

        val matchingQuality = normalizedAvailable.find {
            getQualityResolution(it) == preferredResolution ||
                it == normalizedPreferred
        }

        if (matchingQuality != null) {
            return QualitySelectionResult(
                selectedQuality = matchingQuality,
                availableQualities = availableQualities,
                isFallback = false
            )
        }

        if (allowFallback) {
            val bestFallback = selectBestFallbackQuality(normalizedAvailable, preferredResolution)
            return QualitySelectionResult(
                selectedQuality = bestFallback,
                availableQualities = availableQualities,
                isFallback = true,
                fallbackReason = "Preferred quality $preferredQuality not available"
            )
        }

        return QualitySelectionResult(
            selectedQuality = normalizedAvailable.firstOrNull() ?: DEFAULT_QUALITY,
            availableQualities = availableQualities,
            isFallback = true,
            fallbackReason = "Preferred quality not available and fallback disabled"
        )
    }

    fun selectBestQuality(
        episode: Episode,
        preferredQuality: String = DEFAULT_QUALITY,
        allowFallback: Boolean = true
    ): QualitySelectionResult {
        val availableQualities = parseAvailableQualities(episode.quality)
        return selectBestQuality(availableQualities, preferredQuality, allowFallback)
    }

    private fun selectBestFallbackQuality(
        availableQualities: List<String>,
        preferredResolution: Int
    ): String {
        val qualitiesBelowPreferred = availableQualities.filter {
            getQualityResolution(it) <= preferredResolution
        }.sortedByDescending { getQualityResolution(it) }

        if (qualitiesBelowPreferred.isNotEmpty()) {
            return qualitiesBelowPreferred.first()
        }

        return availableQualities.maxByOrNull { getQualityResolution(it) }
            ?: DEFAULT_QUALITY
    }

    fun selectQualityByMaxSize(
        availableQualities: List<String>,
        maxSizeMB: Long
    ): String {
        val estimatedSizes = availableQualities.associate { quality ->
            quality to estimateFileSize(quality, 24)
        }

        val qualifyingQualities = estimatedSizes.filterValues { it <= maxSizeMB * StorageUtils.BYTES_PER_MB }
            .keys.toList()

        return if (qualifyingQualities.isNotEmpty()) {
            qualifyingQualities.maxByOrNull { getQualityResolution(it) }
                ?: availableQualities.maxByOrNull { getQualityResolution(it) }
                ?: DEFAULT_QUALITY
        } else {
            availableQualities.minByOrNull { getQualityResolution(it) }
                ?: DEFAULT_QUALITY
        }
    }

    fun selectQualityByNetworkType(
        availableQualities: List<String>,
        networkType: NetworkType
    ): String {
        val qualityLimits = mapOf(
            NetworkType.WIFI to listOf("2160p", "1440p", "1080p", "720p", "480p"),
            NetworkType.CELLULAR_5G to listOf("1080p", "720p", "480p"),
            NetworkType.CELLULAR_4G to listOf("720p", "480p", "360p"),
            NetworkType.CELLULAR_3G to listOf("480p", "360p", "240p"),
            NetworkType.CELLULAR_2G to listOf("240p", "360p"),
            NetworkType.UNKNOWN to listOf("720p", "480p")
        )

        val allowedQualities = qualityLimits[networkType] ?: listOf("720p")

        val matchingQuality = availableQualities.find { it in allowedQualities }
        return matchingQuality ?: availableQualities.maxByOrNull { getQualityResolution(it) }
            ?: DEFAULT_QUALITY
    }

    fun getQualityInfo(quality: String): QualityInfo {
        val normalized = quality.lowercase()
        val resolution = getQualityResolution(normalized)
        val isHdr = normalized.contains("hdr") || normalized.contains("10bit")

        return QualityInfo(
            quality = normalized,
            resolution = resolution,
            isHdr = isHdr
        )
    }

    fun getQualityResolution(quality: String): Int {
        val normalized = quality.lowercase()

        return QUALITY_RESOLUTION_MAP.entries.find {
            normalized.contains(it.key.lowercase())
        }?.value ?: run {
            val match = Regex("(\\d+)p").find(normalized)
            match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
    }

    fun getQualityDisplayName(quality: String): String {
        val normalized = quality.lowercase()
        return when {
            normalized.contains("2160") || normalized.contains("4k") -> "4K Ultra HD"
            normalized.contains("1440") -> "1440p"
            normalized.contains("1080") -> "Full HD"
            normalized.contains("720") -> "HD"
            normalized.contains("480") -> "SD"
            normalized.contains("360") -> "Low"
            normalized.contains("240") -> "Minimal"
            else -> quality.uppercase()
        }
    }

    fun getQualityOrder(): List<String> {
        return QUALITY_ORDER
    }

    fun isQualitySupported(quality: String, minResolution: Int): Boolean {
        return getQualityResolution(quality) >= minResolution
    }

    fun filterQualities(
        availableQualities: List<String>,
        minResolution: Int? = null,
        maxResolution: Int? = null
    ): List<String> {
        return availableQualities.filter { quality ->
            val resolution = getQualityResolution(quality)
            (minResolution == null || resolution >= minResolution) &&
                (maxResolution == null || resolution <= maxResolution)
        }.sortedByDescending { getQualityResolution(it) }
    }

    fun estimateFileSize(quality: String, durationMinutes: Int): Long {
        val bitrateKbps = when (quality.lowercase()) {
            "2160p", "4k" -> 15000
            "1440p" -> 8000
            "1080p" -> 4000
            "720p" -> 2500
            "480p" -> 1000
            "360p" -> 500
            "240p" -> 250
            else -> 2500
        }

        val bits = bitrateKbps * 1000L * durationMinutes * 60
        return bits / 8
    }

    fun estimateFileSizeWithOverhead(quality: String, durationMinutes: Int, overheadPercent: Double = 0.1): Long {
        val baseSize = estimateFileSize(quality, durationMinutes)
        return (baseSize * (1 + overheadPercent)).toLong()
    }

    enum class NetworkType {
        WIFI,
        CELLULAR_5G,
        CELLULAR_4G,
        CELLULAR_3G,
        CELLULAR_2G,
        UNKNOWN
    }
}
