package com.suldenlion.mp3app.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suldenlion.mp3app.data.Lyric
import com.suldenlion.mp3app.model.MusicItem
import com.suldenlion.mp3app.repository.MusicRepository
import com.suldenlion.mp3app.service.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class PlaybackMode {
    REPEAT_ALL,
    REPEAT_ONE,
    SHUFFLE
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val musicRepository = MusicRepository(application.applicationContext)
    private val context = application.applicationContext

    private val _musicList = MutableStateFlow<List<MusicItem>>(emptyList())
    val musicList: StateFlow<List<MusicItem>> = _musicList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentLyrics = MutableStateFlow<Lyric?>(null)
    val currentLyrics: StateFlow<Lyric?> = _currentLyrics.asStateFlow()

    private val _playbackMode = MutableStateFlow(PlaybackMode.REPEAT_ALL)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private val _timerText = MutableStateFlow("타이머 설정")
    val timerText: StateFlow<String> = _timerText.asStateFlow()
    private var timerJob: Job? = null

    companion object {
        const val ACTION_SET_PLAYBACK_MODE = "com.suldenlion.mp3app.ACTION_SET_PLAYBACK_MODE"
        const val EXTRA_PLAYBACK_MODE = "com.suldenlion.mp3app.EXTRA_PLAYBACK_MODE"
    }

    fun loadMusic() {
        if (_musicList.value.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            musicRepository.scanForNewAudio()
            val audioFiles = musicRepository.getAudioFiles()
            _musicList.value = audioFiles
            _isLoading.value = false
        }
    }

    fun loadLyrics(musicId: Long) {
        viewModelScope.launch {
            _currentLyrics.value = musicRepository.getLyrics(musicId)
        }
    }

    fun saveLyrics(musicId: Long, lyrics: String) {
        viewModelScope.launch {
            val lyric = Lyric(musicId = musicId, lyrics = lyrics)
            musicRepository.saveLyrics(lyric)
            _currentLyrics.value = lyric
        }
    }

    fun toggleFavorite(musicId: Long) {
        viewModelScope.launch {
            val currentItem = _musicList.value.find { it.id == musicId }
            val currentStatus = currentItem?.isFavorite ?: false

            if (currentStatus) {
                musicRepository.removeFavorite(musicId)
            } else {
                musicRepository.addFavorite(musicId)
            }

            _musicList.update { currentList ->
                currentList.map { musicItem ->
                    if (musicItem.id == musicId) {
                        musicItem.copy(isFavorite = !currentStatus)
                    } else {
                        musicItem
                    }
                }
            }
        }
    }

    fun togglePlaybackMode() {
        _playbackMode.update { currentMode ->
            val newMode = when (currentMode) {
                PlaybackMode.REPEAT_ALL -> PlaybackMode.REPEAT_ONE
                PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
                PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT_ALL
            }
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_SET_PLAYBACK_MODE
                putExtra(EXTRA_PLAYBACK_MODE, newMode.name)
            }
            context.startService(intent)
            newMode
        }
    }

    fun setTimer(minutes: Long) {
        timerJob?.cancel() // 기존 타이머가 있으면 취소
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SET_TIMER
            putExtra(PlaybackService.EXTRA_TIMER_MINUTES, minutes)
        }
        context.startService(intent)

        timerJob = viewModelScope.launch {
            var remainingSeconds = TimeUnit.MINUTES.toSeconds(minutes)
            while (remainingSeconds > 0) {
                _timerText.value = formatRemainingTime(remainingSeconds)
                delay(1000)
                remainingSeconds--
            }
            _timerText.value = "타이머 종료"
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        _timerText.value = "타이머 설정"
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_CANCEL_TIMER
        }
        context.startService(intent)
    }

    private fun formatRemainingTime(seconds: Long): String {
        val minutes = TimeUnit.SECONDS.toMinutes(seconds)
        val remainingSeconds = seconds - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
}
