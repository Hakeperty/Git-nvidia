package com.anivault.data.scraper

import com.anivault.data.model.*
import com.anivault.util.NetworkUtils
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup

class AniWatchScraper : Scraper {

    override val name = "AniWatch"
    override val baseUrl = "https://aniwatchtv.to"
    override val supportedUrls = listOf(
        Regex(".*aniwatchtv\\.to/.*"),
        Regex(".*hianime\\.to/.*"),
        Regex(".*aniwatch\\.to/.*")
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun supportsUrl(url: String): Boolean {
        return supportedUrls.any { it.matches(url) }
    }

    override suspend fun scrapeAnime(url: String): AnimeResult? {
        try {
            val document = Jsoup.connect(url)
                .userAgent(NetworkUtils.USER_AGENT)
                .timeout(10000)
                .get()

            // Extract anime info from page
            val title = document.select("h1.f-title").text()
                .ifEmpty { document.select(".f-info h1").text() }

            val description = document.select(".f-info-summary, .desc").text()
            val coverImage = document.select("img.f-poster, img.f-info-poster").attr("src")
                .ifEmpty { document.select("img").firstOrNull()?.attr("src") }

            val genres = document.select(".f-info-genres a").map { it.text() }

            val year = document.select(".f-info span:contains(Year) + span").text()
                .toIntOrNull()

            // Get anime ID from URL or page
            val animeId = extractAnimeId(url, document) ?: return null

            // Fetch episode list
            val episodes = fetchEpisodes(animeId)

            val anime = Anime(
                id = animeId,
                title = title,
                description = description,
                coverImage = coverImage,
                sourceUrl = url,
                sourceSite = name,
                genres = genres,
                year = year,
                totalEpisodes = episodes.size,
                hasDub = episodes.any { it.isDub },
                hasSub = episodes.any { !it.isDub }
            )

            return AnimeResult(anime, episodes)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override suspend fun scrapeManga(url: String): MangaResult? {
        // AniWatch is primarily an anime site
        return null
    }

    private suspend fun fetchEpisodes(animeId: String): List<Episode> {
        try {
            // Try to fetch from AniWatch API
            val apiUrl = "$baseUrl/ajax/anime/episodes/$animeId"
            val response = NetworkUtils.fetchJson(apiUrl)
            val episodesJson = json.decodeFromString<EpisodesResponse>(response)

            val episodes = mutableListOf<Episode>()

            for (season in episodesJson.data) {
                val seasonNumber = season.key.toIntOrNull() ?: 1
                for (episode in season.value) {
                    val episodeId = episode.id
                    val episodeNum = episode.number

                    episodes.add(
                        Episode(
                            id = episodeId,
                            animeId = animeId,
                            episodeNumber = episodeNum,
                            seasonNumber = seasonNumber,
                            title = episode.title,
                            isDub = episode.slug.contains("dub")
                        )
                    )
                }
            }

            return episodes.sortedBy { it.episodeNumber }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun extractAnimeId(url: String, document: org.jsoup.nodes.Document): String? {
        // Try to extract ID from URL
        val urlPattern = Regex("/anime/([^/]+)")
        val urlMatch = urlPattern.find(url)
        if (urlMatch != null) {
            return urlMatch.groupValues[1]
        }

        // Try to extract from page data
        val scriptPattern = Regex("""animeId\s*[:=]\s*["'](\d+)["']""")
        val scripts = document.select("script")
        for (script in scripts) {
            val data = script.data()
            val match = scriptPattern.find(data)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

    suspend fun getEpisodeSources(episodeId: String, isDub: Boolean = false): EpisodeSources? {
        try {
            val type = if (isDub) "dub" else "sub"
            val apiUrl = "$baseUrl/ajax/v2/episode/servers/$episodeId?type=$type"
            val response = NetworkUtils.fetchJson(apiUrl)

            // Parse server list and get stream URL
            return parseServers(response, episodeId)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseServers(response: String, episodeId: String): EpisodeSources? {
        try {
            val json = Json { ignoreUnknownKeys = true }
            val serverResponse = json.decodeFromString<ServerResponse>(response)

            // Get Hikari server (usually the best quality)
            val serverId = serverResponse.data?.firstOrNull()?.id ?: return null

            val sourceUrl = "$baseUrl/ajax/sources/$serverId"
            return EpisodeSources(
                streamUrl = sourceUrl,
                subtitleUrl = null // Subtitles would be fetched separately
            )
        } catch (e: Exception) {
            return null
        }
    }

    data class EpisodesResponse(val data: Map<String, List<EpisodeData>>)
    data class EpisodeData(val id: String, val number: Int, val title: String?, val slug: String)
    data class ServerResponse(val data: List<ServerData>?)
    data class ServerData(val id: Long, val name: String)
    data class EpisodeSources(val streamUrl: String?, val subtitleUrl: String?)
}
