package com.anivault.domain.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class SubtitleManager {

    /**
     * Parse VTT (WebVTT) subtitle format
     */
    suspend fun parseVtt(content: String): List<SubtitleCue> = withContext(Dispatchers.Default) {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.split("\n")
        var i = 0

        // Skip WEBVTT header and any metadata
        while (i < lines.size && !lines[i].matches(Regex("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"))) {
            i++
        }

        while (i < lines.size) {
            // Skip empty lines
            if (lines[i].isBlank()) {
                i++
                continue
            }

            // Parse timestamp line: 00:01:23.456 --> 00:01:28.789
            val timestampLine = lines[i]
            val timestampMatch = timestampLine.matches(Regex("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} --> \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"))

            if (timestampMatch) {
                val times = timestampLine.split(" --> ")
                val startTime = parseTimestamp(times[0])
                val endTime = parseTimestamp(times[1])

                i++

                // Collect subtitle text (may span multiple lines)
                val textLines = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank() && !lines[i].matches(Regex("^\\d{2}:\\d{2}:\\d{2}"))) {
                    // Skip styling commands
                    if (!lines[i].startsWith("<")) {
                        textLines.add(lines[i].replace(Regex("<[^>]*>"), ""))
                    }
                    i++
                }

                if (textLines.isNotEmpty()) {
                    cues.add(
                        SubtitleCue(
                            startTime = startTime,
                            endTime = endTime,
                            text = textLines.joinToString("<br>")
                        )
                    )
                }
            } else {
                i++
            }
        }

        cues
    }

    /**
     * Parse SRT (SubRip) subtitle format
     */
    suspend fun parseSrt(content: String): List<SubtitleCue> = withContext(Dispatchers.Default) {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.split("\n\n")

        for (block in blocks) {
            val lines = block.trim().split("\n")
            if (lines.size >= 3) {
                // Skip sequence number (first line)
                // Parse timestamp line: 00:01:23,456 --> 00:01:28,789
                val timestampLine = lines[1]
                val timestampMatch = timestampLine.matches(Regex("^\\d{2}:\\d{2}:\\d{2}[,.]\\d{3} --> \\d{2}:\\d{2}:\\d{2}[,.]\\d{3}"))

                if (timestampMatch) {
                    val times = timestampLine.split(" --> ")
                    val startTime = parseSrtTimestamp(times[0])
                    val endTime = parseSrtTimestamp(times[1])

                    // Collect subtitle text (remaining lines)
                    val textLines = lines.drop(2)
                    val text = textLines.joinToString("<br>")

                    cues.add(
                        SubtitleCue(
                            startTime = startTime,
                            endTime = endTime,
                            text = text
                        )
                    )
                }
            }
        }

        cues.sortedBy { it.startTime }
    }

    /**
     * Parse VTT timestamp format: HH:MM:SS.mmm
     */
    private fun parseTimestamp(timestamp: String): Long {
        val parts = timestamp.trim().split(":")
        if (parts.size != 3) return 0L

        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        val secondsParts = parts[2].split(".")
        val seconds = secondsParts[0].toLongOrNull() ?: 0L
        val milliseconds = if (secondsParts.size > 1) secondsParts[1].toLongOrNull() ?: 0L else 0L

        return ((hours * 3600 + minutes * 60 + seconds) * 1000 + milliseconds)
    }

    /**
     * Parse SRT timestamp format: HH:MM:SS,mmm (comma instead of dot)
     */
    private fun parseSrtTimestamp(timestamp: String): Long {
        val parts = timestamp.trim().split(":")
        if (parts.size != 3) return 0L

        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        val secondsParts = parts[2].split(",")
        val seconds = secondsParts[0].toLongOrNull() ?: 0L
        val milliseconds = if (secondsParts.size > 1) secondsParts[1].toLongOrNull() ?: 0L else 0L

        return ((hours * 3600 + minutes * 60 + seconds) * 1000 + milliseconds)
    }

    /**
     * Detect subtitle format from content
     */
    fun detectFormat(content: String): SubtitleFormat {
        return when {
            content.trim().startsWith("WEBVTT") -> SubtitleFormat.VTT
            content.trim().matches(Regex("^\\d+\\s+\\d{2}:\\d{2}:\\d{2}[,.]\\d{3} -->")) -> SubtitleFormat.SRT
            content.contains("-->") && content.matches(Regex(".*\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}.*")) -> SubtitleFormat.VTT
            else -> SubtitleFormat.UNKNOWN
        }
    }

    /**
     * Get subtitle cue for current position
     */
    fun getCueAtPosition(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
        return cues.find { it.startTime <= positionMs && it.endTime >= positionMs }
    }

    /**
     * Load subtitle from URL
     */
    suspend fun loadSubtitle(url: String): Result<SubtitleTrack> = withContext(Dispatchers.Default) {
        try {
            val content = java.net.URL(url).readText()
            val format = detectFormat(content)

            if (format == SubtitleFormat.UNKNOWN) {
                Result.failure(Exception("Unknown subtitle format"))
            } else {
                Result.success(
                    SubtitleTrack(
                        id = java.util.UUID.randomUUID().toString(),
                        label = "Auto",
                        language = "en",
                        url = url,
                        content = content,
                        format = format
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load subtitle from local content
     */
    fun loadSubtitleFromContent(content: String, label: String = "Auto", language: String = "en"): Result<SubtitleTrack> {
        return try {
            val format = detectFormat(content)

            if (format == SubtitleFormat.UNKNOWN) {
                Result.failure(Exception("Unknown subtitle format"))
            } else {
                Result.success(
                    SubtitleTrack(
                        id = java.util.UUID.randomUUID().toString(),
                        label = label,
                        language = language,
                        content = content,
                        format = format
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class SubtitleCue(
    val startTime: Long,
    val endTime: Long,
    val text: String
)
