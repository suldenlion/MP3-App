package com.suldenlion.mp3app.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.suldenlion.mp3app.data.Favorite
import com.suldenlion.mp3app.data.Lyric
import com.suldenlion.mp3app.data.LyricsDatabase
import com.suldenlion.mp3app.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MusicRepository(private val context: Context) {

    private val TAG = "MP3AppDebug"
    private val db = LyricsDatabase.getDatabase(context)
    private val lyricDao = db.lyricDao()
    private val favoriteDao = db.favoriteDao()

    /**
     * 기기의 외부 저장소 전체를 스캔하여 MediaStore를 갱신합니다.
     */
    suspend fun scanForNewAudio() {
        Log.d(TAG, "Starting media scan...")
        suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(android.os.Environment.getExternalStorageDirectory().toString()),
                null
            ) { path, uri ->
                Log.d(TAG, "Scan completed. Path: $path, Uri: $uri")
                continuation.resume(Unit)
            }
        }
    }

    suspend fun getAudioFiles(): List<MusicItem> = withContext(Dispatchers.IO) {
        // 1. DB에서 즐겨찾기 목록을 먼저 가져와 Set으로 변환 (빠른 조회를 위해)
        val favoriteIds = favoriteDao.getAllFavorites().map { it.musicId }.toSet()

        val musicList = mutableListOf<MusicItem>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        // 음악 파일만 필터링하도록 IS_MUSIC 필터를 복원합니다.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        val selectionArgs = null
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        Log.d(TAG, "Querying MediaStore...")
        val query: Cursor? = context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        query?.use { cursor ->
            Log.d(TAG, "Found ${cursor.count} music files.")

            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val isFavorite = favoriteIds.contains(id) // 즐겨찾기 여부 확인
                Log.d(TAG, "  -> Found item: Title=$title, Artist=$artist, isFavorite=$isFavorite")
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                musicList.add(MusicItem(id, title, artist, contentUri.toString(), isFavorite))
            }
        }
        return@withContext musicList
    }


    suspend fun getLyrics(musicId: Long): Lyric? {
        return lyricDao.getLyricsByMusicId(musicId)
    }

    suspend fun saveLyrics(lyric: Lyric) {
        lyricDao.insertLyric(lyric)
    }

    suspend fun addFavorite(musicId: Long) {
        favoriteDao.addFavorite(Favorite(musicId))
    }

    suspend fun removeFavorite(musicId: Long) {
        favoriteDao.removeFavorite(musicId)
    }

    suspend fun isFavorite(musicId: Long): Boolean {
        return favoriteDao.isFavorite(musicId)
    }
}
