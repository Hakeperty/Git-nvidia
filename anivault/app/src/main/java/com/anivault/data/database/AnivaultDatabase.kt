package com.anivault.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anivault.data.model.Anime
import com.anivault.data.model.Chapter
import com.anivault.data.model.Download
import com.anivault.data.model.Episode
import com.anivault.data.model.Manga
import com.anivault.domain.player.PlayerProgress

@Database(
    entities = [Anime::class, Episode::class, Manga::class, Chapter::class, Download::class, PlayerProgress::class],
    version = 2,
    exportSchema = false
)
abstract class AnivaultDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playerProgressDao(): PlayerProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AnivaultDatabase? = null

        fun getDatabase(context: Context): AnivaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnivaultDatabase::class.java,
                    "anivault_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
