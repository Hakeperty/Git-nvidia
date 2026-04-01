package com.anivault.data.scraper

import com.anivault.data.model.*
import com.anivault.util.NetworkUtils
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

class MangaDexScraper : Scraper {

    override val name = "MangaDex"
    override val baseUrl = "https://mangadex.org"
    override val supportedUrls = listOf(
        Regex(".*mangadex\\.org/title/.*"),
        Regex(".*mangadex\\.org/manga/.*")
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val apiUrl = "https://api.mangadex.org"

    override suspend fun supportsUrl(url: String): Boolean {
        return supportedUrls.any { it.matches(url) }
    }

    override suspend fun scrapeAnime(url: String): AnimeResult? {
        // MangaDex is primarily a manga site
        return null
    }

    override suspend fun scrapeManga(url: String): MangaResult? {
        try {
            val mangaId = extractMangaId(url) ?: return null

            // Fetch manga details
            val mangaDetails = fetchMangaDetails(mangaId) ?: return null

            // Fetch chapters
            val chapters = fetchChapters(mangaId)

            val manga = Manga(
                id = mangaId,
                title = mangaDetails.title,
                description = mangaDetails.description,
                coverImage = mangaDetails.coverImage,
                sourceUrl = url,
                sourceSite = name,
                genres = mangaDetails.genres,
                status = mapStatus(mangaDetails.status),
                author = mangaDetails.author,
                artist = mangaDetails.artist,
                year = mangaDetails.year,
                totalChapters = chapters.size
            )

            return MangaResult(manga, chapters)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun extractMangaId(url: String): String? {
        val pattern = Regex("title/([a-f0-9-]{36})")
        return pattern.find(url)?.groupValues?.get(1)
    }

    private suspend fun fetchMangaDetails(id: String): MangaDetails? {
        try {
            val url = "$apiUrl/manga/$id?includes[]=cover_art"
            val response = NetworkUtils.fetchJson(url)
            return json.decodeFromString<MangaDetailsResponse>(response).data
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun fetchChapters(mangaId: String): List<Chapter> {
        try {
            val chapters = mutableListOf<Chapter>()
            var offset = 0
            val limit = 500

            while (true) {
                val url = "$apiUrl/manga/$mangaId/feed?" +
                        "translatedLanguage[]=en" +
                        "&order[chapter]=desc" +
                        "&limit=$limit" +
                        "&offset=$offset"

                val response = NetworkUtils.fetchJson(url)
                val feed = json.decodeFromString<ChapterFeed>(response)

                if (feed.data.isEmpty()) break

                chapters.addAll(feed.data.map { chapter ->
                    Chapter(
                        id = chapter.id,
                        mangaId = mangaId,
                        chapterNumber = chapter.chapter.toFloatOrNull() ?: 0f,
                        title = chapter.title,
                        volumeNumber = chapter.volume?.toIntOrNull(),
                        pageCount = chapter.pages,
                        releaseDate = chapter.publishAt?.let { it * 1000 },
                        scanlator = chapter.scanlator
                    )
                })

                offset += limit
                if (feed.data.size < limit) break
            }

            return chapters.sortedByDescending { it.chapterNumber }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    suspend fun getChapterPages(chapterId: String): List<String> {
        try {
            // Fetch chapter data
            val url = "$apiUrl/chapter/$chapterId"
            val response = NetworkUtils.fetchJson(url)
            val chapterData = json.decodeFromString<ChapterDataResponse>(response).data

            // Fetch at-home server
            val atHomeUrl = "$apiUrl/at-home/server/${chapterData.hashes?.mid ?: return emptyList()}"
            val atHomeResponse = NetworkUtils.fetchJson(atHomeUrl)
            val atHomeData = json.decodeFromString<AtHomeResponse>(atHomeResponse)

            val baseUrl = atHomeData.baseUrl
            val chapterHash = chapterData.hashes?.data ?: return emptyList()

            // Build page URLs
            return chapterData.images?.map { filename ->
                "$baseUrl/data/${chapterHash}/${filename}"
            } ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun mapStatus(status: String?): com.anivault.data.model.MangaStatus {
        return when (status) {
            "ongoing" -> com.anivault.data.model.MangaStatus.ONGOING
            "completed" -> com.anivault.data.model.MangaStatus.COMPLETED
            "hiatus" -> com.anivault.data.model.MangaStatus.HIATUS
            "cancelled" -> com.anivault.data.model.MangaStatus.CANCELLED
            else -> com.anivault.data.model.MangaStatus.UNKNOWN
        }
    }

    @Serializable
    data class MangaDetailsResponse(val data: MangaDetails?)

    @Serializable
    data class MangaDetails(
        val id: String,
        val title: String,
        val description: String?,
        val status: String?,
        val year: Int?,
        val attributes: Map<String, String>?,
        val relationships: List<Relationship>?
    ) {
        val coverImage: String?
            get() = relationships?.find { it.type == "cover_art" }?.fileName
                ?.let { "https://uploads.mangadex.org/covers/$id/$it" }

        val author: String?
            get() = relationships?.find { it.type == "author" }?.attributes?.name

        val artist: String?
            get() = relationships?.find { it.type == "artist" }?.attributes?.name

        val genres: List<String>
            get() = attributes?.filter { it.key in listOf("genre", "theme") }?.values?.toList() ?: emptyList()
    }

    @Serializable
    data class Relationship(
        val type: String,
        val fileName: String?,
        val attributes: Attributes?
    ) {
        @Serializable
        data class Attributes(val name: String?)
    }

    @Serializable
    data class ChapterFeed(val data: List<ChapterItem>)

    @Serializable
    data class ChapterItem(
        val id: String,
        val title: String?,
        val chapter: String,
        val volume: String?,
        val pages: Int = 0,
        val publishAt: Long?,
        val scanlator: String?
    )

    @Serializable
    data class ChapterDataResponse(val data: ChapterData)

    @Serializable
    data class ChapterData(
        val hashes: Hashes?,
        val images: List<String>?
    ) {
        @Serializable
        data class Hashes(val data: String?, val mid: String?)
    }

    @Serializable
    data class AtHomeResponse(val baseUrl: String)
}
