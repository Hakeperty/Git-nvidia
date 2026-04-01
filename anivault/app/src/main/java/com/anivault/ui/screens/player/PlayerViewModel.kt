package com.anivault.ui.screens.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anivault.data.database.PlayerProgressDao
import com.anivault.data.model.Episode
import com.anivault.data.model.Anime
import com.anivault.domain.player.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class PlayerViewModel(
    application: Application,
    private val playerProgressDao: PlayerProgressDao
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _events = Channel<PlayerEvent>(Channel.BUFFERED)
    val events: Channel<PlayerEvent> = _events

    private val subtitleManager = SubtitleManager()
    private var currentSubtitleCues: List<SubtitleCue> = emptyList()
    private var playbackUpdateJob: kotlinx.coroutines.Job? = null

    fun setEpisode(episode: Episode, series: Anime? = null, nextEpisodes: List<Episode> = emptyList()) {
        viewModelScope.launch {
            _state.update { it.copy(
                currentEpisode = episode,
                seriesInfo = series,
                upNextEpisodes = nextEpisodes,
                isLoading = true,
                error = null
            ) }

            // Load saved progress
            val savedProgress = playerProgressDao.getByEpisodeIdSync(episode.id)

            _state.update { it.copy(
                currentPosition = savedProgress?.currentPosition ?: 0L,
                totalTime = episode.duration?.toLong()?.times(1000) ?: 0L,
                playbackSpeed = savedProgress?.playbackSpeed ?: 1f,
                isLoading = false
            ) }

            // Load subtitles if available
            episode.subtitleUrl?.let { url ->
                loadSubtitles(url)
            }
        }
    }

    private fun loadSubtitles(url: String) {
        viewModelScope.launch {
            subtitleManager.loadSubtitle(url)
                .onSuccess { track ->
                    _state.update { it.copy(
                        subtitleTracks = listOf(track),
                        selectedSubtitleTrack = track
                    ) }

                    // Parse the subtitles
                    track.content?.let { content ->
                        val cues = when (track.format) {
                            SubtitleFormat.VTT -> subtitleManager.parseVtt(content)
                            SubtitleFormat.SRT -> subtitleManager.parseSrt(content)
                            else -> emptyList()
                        }
                        currentSubtitleCues = cues
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = "Failed to load subtitles: ${error.message}") }
                }
        }
    }

    fun loadSubtitlesFromContent(content: String, label: String = "Auto") {
        viewModelScope.launch {
            subtitleManager.loadSubtitleFromContent(content, label)
                .onSuccess { track ->
                    _state.update { it.copy(
                        subtitleTracks = listOf(track),
                        selectedSubtitleTrack = track
                    ) }

                    val cues = when (track.format) {
                        SubtitleFormat.VTT -> subtitleManager.parseVtt(content)
                        SubtitleFormat.SRT -> subtitleManager.parseSrt(content)
                        else -> emptyList()
                    }
                    currentSubtitleCues = cues
                }
        }
    }

    fun handleEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.Play -> startPlayback(event.episode)
            is PlayerEvent.Pause -> pausePlayback()
            is PlayerEvent.Seek -> seekTo(event.position)
            is PlayerEvent.SetPlaybackSpeed -> setPlaybackSpeed(event.speed)
            is PlayerEvent.SetVolume -> _state.update { it.copy(volume = event.volume) }
            is PlayerEvent.SetBrightness -> _state.update { it.copy(brightness = event.brightness) }
            is PlayerEvent.SelectSubtitle -> selectSubtitle(event.track)
            is PlayerEvent.ToggleControls -> _state.update { it.copy(isControlsVisible = !it.isControlsVisible) }
            is PlayerEvent.SkipIntro -> skipIntro()
            is PlayerEvent.PlayNext -> playNextEpisode()
            is PlayerEvent.PlayPrevious -> playPreviousEpisode()
            is PlayerEvent.SaveProgress -> saveProgress(event.position, event.total)
            is PlayerEvent.Error -> _state.update { it.copy(error = event.message) }
            is PlayerEvent.EpisodeComplete -> onEpisodeComplete(event.episode)
        }
    }

    private fun startPlayback(episode: Episode) {
        _state.update { it.copy(isPlaying = true) }
        startProgressUpdates()
    }

    private fun pausePlayback() {
        _state.update { it.copy(isPlaying = false) }
        playbackUpdateJob?.cancel()
    }

    private fun seekTo(position: Long) {
        _state.update { it.copy(currentPosition = position) }
    }

    private fun setPlaybackSpeed(speed: Float) {
        _state.update { it.copy(playbackSpeed = speed) }
        saveProgress(_state.value.currentPosition, _state.value.totalTime)
    }

    private fun selectSubtitle(track: SubtitleTrack?) {
        _state.update { it.copy(selectedSubtitleTrack = track) }
    }

    private fun skipIntro() {
        val skipIntroEnd = _state.value.skipIntroEnd ?: return
        seekTo(skipIntroEnd)
    }

    private fun playNextEpisode() {
        val current = _state.value.currentEpisode ?: return
        val next = _state.value.upNextEpisodes.firstOrNull { it.id != current.id }
        next?.let { playEpisode(it) }
    }

    private fun playPreviousEpisode() {
        val current = _state.value.currentEpisode ?: return
        val prev = _state.value.upNextEpisodes
            .filter { it.episodeNumber < current.episodeNumber }
            .maxByOrNull { it.episodeNumber }
        prev?.let { playEpisode(it) }
    }

    private fun playEpisode(episode: Episode) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val savedProgress = playerProgressDao.getByEpisodeIdSync(episode.id)
            val nextEpisodes = _state.value.upNextEpisodes.filter { it.id != episode.id }

            _state.update { it.copy(
                currentEpisode = episode,
                currentPosition = savedProgress?.currentPosition ?: 0L,
                totalTime = episode.duration?.toLong()?.times(1000) ?: 0L,
                upNextEpisodes = nextEpisodes,
                isLoading = false,
                isPlaying = true
            ) }

            episode.subtitleUrl?.let { url ->
                loadSubtitles(url)
            }

            startProgressUpdates()
        }
    }

    private fun onEpisodeComplete(episode: Episode) {
        viewModelScope.launch {
            // Mark episode as watched
            playerProgressDao.updateProgress(
                episodeId = episode.id,
                position = _state.value.totalTime,
                total = _state.value.totalTime,
                speed = _state.value.playbackSpeed,
                playing = false,
                completed = true
            )

            // Auto-play next episode if enabled
            if (_state.value.autoPlayNext) {
                delay(2000) // 2 second delay before next episode
                playNextEpisode()
            }
        }
    }

    private fun saveProgress(position: Long, total: Long) {
        viewModelScope.launch {
            val episode = _state.value.currentEpisode ?: return@launch

            playerProgressDao.updateProgress(
                episodeId = episode.id,
                position = position,
                total = total,
                speed = _state.value.playbackSpeed,
                playing = _state.value.isPlaying
            )
        }
    }

    private fun startProgressUpdates() {
        playbackUpdateJob?.cancel()
        playbackUpdateJob = viewModelScope.launch {
            while (isActive) {
                if (_state.value.isPlaying) {
                    delay(1000)
                    _state.update { it.copy(
                        currentPosition = it.currentPosition + (1000 * it.playbackSpeed).toLong()
                    ) }

                    // Save progress periodically
                    saveProgress(_state.value.currentPosition, _state.value.totalTime)

                    // Check if episode is complete
                    if (_state.value.isCompleted) {
                        _state.value.currentEpisode?.let { episode ->
                            handleEvent(PlayerEvent.EpisodeComplete(episode))
                        }
                    }
                } else {
                    delay(500)
                }
            }
        }
    }

    fun getCurrentSubtitle(): SubtitleCue? {
        val position = _state.value.currentPosition
        return subtitleManager.getCueAtPosition(currentSubtitleCues, position)
    }

    fun setSkipIntro(start: Long, end: Long) {
        _state.update { it.copy(skipIntroStart = start, skipIntroEnd = end) }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        _state.update { it.copy(autoPlayNext = enabled) }
    }

    fun setSeriesInfo(series: Anime) {
        _state.update { it.copy(seriesInfo = series) }
    }

    fun setUpNextEpisodes(episodes: List<Episode>) {
        _state.update { it.copy(upNextEpisodes = episodes) }
    }

    fun setCurrentPosition(position: Long) {
        _state.update { it.copy(currentPosition = position) }
    }

    fun setTotalTime(total: Long) {
        _state.update { it.copy(totalTime = total) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackUpdateJob?.cancel()
        // Save final progress
        _state.value.currentEpisode?.let { episode ->
            viewModelScope.launch {
                saveProgress(_state.value.currentPosition, _state.value.totalTime)
            }
        }
    }
}
