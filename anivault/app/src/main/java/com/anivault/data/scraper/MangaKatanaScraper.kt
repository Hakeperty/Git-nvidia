package com.anivault.data.scraper

import com.anivault.data.model.*
import com.anivault.util.NetworkUtils
import org.jsoup.Jsoup

class MangaKatanaScraper : Scraper {

    override val name = "MangaKatana"
    override val baseUrl = "https://mangakatana.com"
    override val supportedUrls = listOf(
        Regex(".*mangakatana\\.com/.*")
    )

    override suspend fun supportsUrl(url: String): Boolean {
        return supportedUrls.any { it.matches(url) }
    }

    override suspend fun scrapeAnime(url: String): AnimeResult? {
        return null
    }

    override suspend fun scrapeManga(url: String): MangaResult? {
        try {
            val document = Jsoup.connect(url)
                .userAgent(NetworkUtils.USER_AGENT)
                .timeout(10000)
                .get()

            val title = document.select("h1.manga_name").text()
            val description = document.select(".desc, .info_description").text()
            val coverImage = document.select("img.manga_poster").attr("src")

            val genres = document.select(".info_genres a").map { it.text() }

            val mangaId = extractMangaId(url)

            val chapters = fetchChapters(url, document)

            val manga = Manga(
                id = mangaId ?: url.hashCode().toString(),
                title = title,
                description = description,
                coverImage = coverImage,
                sourceUrl = url,
                sourceSite = name,
                genres = genres,
                totalChapters = chapters.size
            )

            return MangaResult(manga, chapters)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun extractMangaId(url: String): String? {
        return url.substringAfterLast("/").substringBefore("?")
    }

    private suspend fun fetchChapters(mangaUrl: String, document: org.jsoup.nodes.Document): List<Chapter> {
        val chapters = mutableListOf<Chapter>()

        try {
            val chapterElements = document.select("table.chapter_list tr")

            for (element in chapterElements) {
                val link = element.select("a").first() ?: continue
                val chapterUrl = link.attr("abs:href")
                val chapterName = link.text()

                // Parse chapter number from name
                val chapterNum = parseChapterNumber(chapterName)

                val dateElement = element.select("td").last()
                val releaseDate = dateElement?.text()?.let { parseDate(it) }

                chapters.add(
                    Chapter(
                        id = chapterUrl?.hashCode()?.toString() ?: "${chapterNum}",
                        mangaId = mangaUrl.hashCode().toString(),
                        chapterNumber = chapterNum,
                        title = chapterName,
                        releaseDate = releaseDate,
                        pageUrls = emptyList() // Pages fetched on demand
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return chapters.sortedByDescending { it.chapterNumber }
    }

    private fun parseChapterNumber(name: String): Float {
        val pattern = Regex("Chapter\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        return pattern.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    }

    private fun parseDate(dateStr: String): Long? {
        try {
            // Simple date parsing - format varies by source
            val pattern = Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4})")
            val match = pattern.find(dateStr) ?: return null

            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()

            val calendar = java.util.Calendar.getInstance()
            calendar.set(year, month - 1, day)
            return calendar.timeInMillis
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun getChapterPages(chapterUrl: String): List<String> {
        try {
            val document = Jsoup.connect(chapterUrl)
                .userAgent(NetworkUtils.USER_AGENT)
                .timeout(10000)
                .get()

            return document.select("div#imgBox img")
                .map { it.attr("src") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            return emptyList()
        }
    }
}
