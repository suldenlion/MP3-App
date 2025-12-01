package com.suldenlion.mp3app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suldenlion.mp3app.data.Lyric
import com.suldenlion.mp3app.model.MusicItem
import com.suldenlion.mp3app.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val musicRepository = MusicRepository(application.applicationContext)

    private val _musicList = MutableStateFlow<List<MusicItem>>(emptyList())
    val musicList: StateFlow<List<MusicItem>> = _musicList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentLyrics = MutableStateFlow<Lyric?>(null)
    val currentLyrics: StateFlow<Lyric?> = _currentLyrics.asStateFlow()

    fun loadMusic() {
        if (_musicList.value.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            // 음악 목록을 가져오기 전에 미디어 스캔을 먼저 수행
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
            _currentLyrics.value = lyric // Update current lyrics immediately after saving
        }
    }
}
