package io.github.adit0132.rockscreen.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.adit0132.rockscreen.data.model.LyricsResult
import io.github.adit0132.rockscreen.data.model.TrackInfo
import io.github.adit0132.rockscreen.service.PlaybackHub
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LockScreenViewModel : ViewModel() {

    val currentTrack: StateFlow<TrackInfo?> = PlaybackHub.currentTrack
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackHub.currentTrack.value)

    val lyricsResult: StateFlow<LyricsResult> = PlaybackHub.lyricsResult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackHub.lyricsResult.value)

    val isPlaying: StateFlow<Boolean> = PlaybackHub.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackHub.isPlaying.value)

    val position: StateFlow<PlaybackHub.Position> = PlaybackHub.position
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackHub.position.value)
}
