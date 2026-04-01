package com.anivault.data.scraper

import com.anivault.data.model.Anime
import com.anivault.data.model.Episode
import com.anivault.data.model.Manga
import com.anivault.data.model.Chapter

/**
 * Base interface for all content scrapers
 */
interface Scraper {
    val name: String
    val baseUrl: String
    val supportedUrls: List<Regex>

    suspend fun supportsUrl(url: String): Boolean
    suspend fun scrapeAnime(url: String): AnimeResult?
    suspend fun scrapeManga(url: String): MangaResult?
}

data class AnimeResult(
    val anime: Anime,
    val episodes: List<Episode>
)

data class MangaResult(
    val manga: Manga,
    val chapters: List<Chapter>
)
