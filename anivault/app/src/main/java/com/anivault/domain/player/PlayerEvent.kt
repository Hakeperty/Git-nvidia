package com.anivault.domain.player

sealed class PlayerEvent {
    data class Play(val episode: com.anivault.data.model.Episode) : PlayerEvent()
    data class Pause() : PlayerEvent()
    data class Seek(val position: Long) : PlayerEvent()
    data class SetPlaybackSpeed(val speed: Float) : PlayerEvent()
    data class SetVolume(val volume: Float) : PlayerEvent()
    data class SetBrightness(val brightness: Float) : PlayerEvent()
    data class SelectSubtitle(val track: SubtitleTrack?) : PlayerEvent()
    data class ToggleControls() : PlayerEvent()
    data class SkipIntro() : PlayerEvent()
    data class PlayNext() : PlayerEvent()
    data class PlayPrevious() : PlayerEvent()
    data class SaveProgress(val position: Long, val total: Long) : PlayerEvent()
    data class Error(val message: String) : PlayerEvent()
    data class EpisodeComplete(val episode: com.anivault.data.model.Episode) : PlayerEvent()
}
