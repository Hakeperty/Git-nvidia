package com.anivault.ui.screens.player

import com.anivault.data.database.PlayerProgressDao
import com.anivault.domain.player.PlayerProgress
import kotlinx.coroutines.flow.Flow

class PlayerProgressRepository(
    private val playerProgressDao: PlayerProgressDao
) {
    fun getProgressByEpisodeId(episodeId: String): Flow<PlayerProgress?> =
        playerProgressDao.getByEpisodeId(episodeId)

    suspend fun getProgressByEpisodeIdSync(episodeId: String): PlayerProgress? =
        playerProgressDao.getByEpisodeIdSync(episodeId)

    suspend fun saveProgress(progress: PlayerProgress) {
        playerProgressDao.insert(progress)
    }

    suspend fun updateProgress(
        episodeId: String,
        position: Long,
        total: Long,
        speed: Float,
        playing: Boolean
    ) {
        playerProgressDao.updateProgress(episodeId, position, total, speed, playing)
    }

    fun getInProgress(): Flow<List<PlayerProgress>> =
        playerProgressDao.getInProgress()

    suspend fun deleteProgress(episodeId: String) {
        playerProgressDao.deleteByEpisodeId(episodeId)
    }
}
