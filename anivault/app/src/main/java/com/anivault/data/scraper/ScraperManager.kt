package com.anivault.data.scraper

import com.anivault.data.model.Anime
import com.anivault.data.model.Episode
import com.anivault.data.model.Manga
import com.anivault.data.model.Chapter

class ScraperManager(private val scrapers: List<Scraper>) {

    suspend fun detectSourceType(url: String): SourceType? {
        for (scraper in scrapers) {
            if (scraper.supportsUrl(url)) {
                return SourceType(scraper.name, scraper.baseUrl)
            }
        }
        return null
    }

    suspend fun importContent(url: String): ImportResult {
        for (scraper in scrapers) {
            if (scraper.supportsUrl(url)) {
                val animeResult = scraper.scrapeAnime(url)
                if (animeResult != null) {
                    return ImportResult.Anime(animeResult.anime, animeResult.episodes)
                }

                val mangaResult = scraper.scrapeManga(url)
                if (mangaResult != null) {
                    return ImportResult.Manga(mangaResult.manga, mangaResult.chapters)
                }
            }
        }
        return ImportResult.Error("Unsupported URL or scraper unavailable")
    }

    suspend fun getStreamUrl(episodeId: String, quality: String, isDub: Boolean): String? {
        // This would be implemented by specific scrapers
        // For now, returns the stream URL from episode data
        return null
    }

    suspend fun getChapterImages(chapterId: String): List<String> {
        // This would be implemented by specific scrapers
        return emptyList()
    }

    enum class ImportResult {
        ;
        data class Anime(val anime: Anime, val episodes: List<Episode>) : ImportResult()
        data class Manga(val manga: Manga, val chapters: List<Chapter>) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    data class SourceType(val name: String, val baseUrl: String)
}
