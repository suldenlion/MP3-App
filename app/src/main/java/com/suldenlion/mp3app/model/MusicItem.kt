package com.suldenlion.mp3app.model

/**
 * 기기에서 스캔한 음악 파일의 데이터를 표현하는 클래스
 *
 * @param id MediaStore의 ID
 * @param title 곡 제목
 * @param artist 아티스트 이름
 * @param contentUri 파일에 접근하기 위한 Uri (String)
 */
data class MusicItem(
    val id: Long,
    val title: String,
    val artist: String,
    val contentUri: String,
    val isFavorite: Boolean = false
)
